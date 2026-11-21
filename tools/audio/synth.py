#!/usr/bin/env python3
"""
Synthesizes every sound in Dewfield: the UI and farm effects, the rain loop and the day and
night music. Everything is generated from code (numpy + ffmpeg/libvorbis), so the audio is
original, reproducible and free of licensing questions.

    python3 tools/audio/synth.py            # writes assets/sfx/*.ogg and assets/music/*.ogg
    python3 tools/audio/synth.py --only coin,levelup

Loops (rain, music) are rendered circularly: notes and reverb tails that run past the end wrap
around to the start, so the files loop without a seam.
"""
import argparse
import os
import subprocess

import numpy as np

SR = 44100
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


# ----------------------------------------------------------------------------- building blocks

def n_of(seconds):
    return int(round(seconds * SR))


def times(n):
    return np.arange(n) / SR


def phase(freq, n):
    f = np.full(n, float(freq)) if np.isscalar(freq) else np.asarray(freq, float)
    return 2 * np.pi * np.cumsum(f) / SR


def osc(kind, freq, seconds):
    n = n_of(seconds)
    ph = phase(freq, n)
    if kind == "sine":
        return np.sin(ph)
    if kind == "tri":
        return 2 / np.pi * np.arcsin(np.sin(ph))
    if kind == "square":
        return np.tanh(4 * np.sin(ph))
    if kind == "saw":
        return sum(((-1) ** (k + 1)) * np.sin(k * ph) / k for k in range(1, 12)) * (2 / np.pi)
    raise ValueError(kind)


def glide(points, seconds):
    """Frequency curve through (time fraction, Hz) points, interpolated in log space."""
    n = n_of(seconds)
    xs, fs = zip(*points)
    return np.exp(np.interp(np.linspace(0, 1, n), xs, np.log(fs)))


def noise(seconds, rng):
    return rng.standard_normal(n_of(seconds))


def decay(n, tau, attack=0.002):
    t = times(n)
    env = np.exp(-t / tau)
    a = max(1, n_of(attack))
    env[:a] *= np.linspace(0, 1, a)
    return env


def adsr(n, a, d, s, r):
    env = np.full(n, s)
    a, d, r = n_of(a), n_of(d), n_of(r)
    a = min(a, n)
    env[:a] = np.linspace(0, 1, a)
    d_end = min(n, a + d)
    env[a:d_end] = np.linspace(1, s, d_end - a)
    if r > 0:
        r = min(r, n)
        env[n - r:] *= np.linspace(1, 0, r)
    return env


def shape(x, gain, circular=False):
    """Zero-phase filtering in the frequency domain. [gain] maps Hz to linear gain."""
    n = len(x)
    size = n if circular else 1 << int(np.ceil(np.log2(2 * n)))
    spec = np.fft.rfft(x, size)
    f = np.fft.rfftfreq(size, 1 / SR)
    return np.fft.irfft(spec * gain(f), size)[:n]


def lowpass(x, fc, order=2, circular=False):
    return shape(x, lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order)), circular)


def highpass(x, fc, order=2, circular=False):
    return shape(x, lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-6)) ** (2 * order)), circular)


def bandpass(x, lo, hi, order=2, circular=False):
    return highpass(lowpass(x, hi, order, circular), lo, order, circular)


def place(buf, sig, at, gain=1.0, wrap=False):
    """
    Adds [sig] into [buf] starting at sample [at]; with [wrap] the overflow continues at 0.
    The last few milliseconds are faded so a note cut off mid-decay doesn't click.
    """
    n = len(buf)
    f = min(len(sig) // 4, n_of(0.006))
    if f > 0:
        sig = sig.copy()
        sig[-f:] *= np.linspace(1, 0, f)
    if wrap:
        idx = (np.arange(len(sig)) + at) % n
        np.add.at(buf, idx, sig * gain)
    else:
        end = min(n, at + len(sig))
        if end > at:
            buf[at:end] += sig[:end - at] * gain


def finish(x, peak, fade=0.004):
    """Removes DC, fades the edges and scales a one-shot to [peak]."""
    x = x - np.mean(x)
    f = min(len(x) // 4, n_of(fade))
    if f > 0:
        x[:f] *= np.linspace(0, 1, f)
        x[-f:] *= np.linspace(1, 0, f)
    m = np.max(np.abs(x))
    return x * (peak / m) if m > 0 else x


def impulse(seconds, rt60, rng, predelay=0.012, bright=6000):
    n = n_of(seconds)
    t = times(n)
    ir = rng.standard_normal(n) * np.exp(-t * 6.9 / rt60)
    ir = lowpass(ir, bright)
    ir[:n_of(predelay)] = 0
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x, ir, wet, circular=False):
    if circular:
        size = len(x)
        y = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(ir, size), size)
    else:
        size = 1 << int(np.ceil(np.log2(len(x) + len(ir))))
        y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x)]
    return x + wet * y


def tail(x, seconds):
    return np.concatenate([x, np.zeros(n_of(seconds))])


NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def midi(name):
    """'A4' -> 69, 'C#5' -> 73, 'Bb3' -> 58."""
    pitch = NOTE[name[0]]
    rest = name[1:]
    while rest and rest[0] in "#b":
        pitch += 1 if rest[0] == "#" else -1
        rest = rest[1:]
    return pitch + 12 * (int(rest) + 1)


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ----------------------------------------------------------------------------- sound effects

def sfx_click(rng):
    x = osc("sine", 1400, 0.045) * decay(n_of(0.045), 0.008)
    x += 0.25 * highpass(noise(0.045, rng), 3000) * decay(n_of(0.045), 0.004)
    return finish(x, 0.5)


def sfx_tick(rng):
    return finish(osc("sine", 2300, 0.03) * decay(n_of(0.03), 0.006), 0.32)


def two_notes(f1, f2, gap, length, kind="tri"):
    out = np.zeros(n_of(gap + length + 0.05))
    for i, f in enumerate((f1, f2)):
        d = gap + 0.05 if i == 0 else length
        tone = osc(kind, f, d) + 0.3 * osc("sine", 2 * f, d)
        place(out, tone * decay(n_of(d), d / 3.5), n_of(i * gap))
    return out


def sfx_open(rng):
    x = two_notes(hz(79), hz(84), 0.06, 0.16)
    whoosh = bandpass(noise(0.2, rng), 900, 3200) * adsr(n_of(0.2), 0.12, 0.05, 0.4, 0.06)
    place(x, whoosh, 0, 0.12)
    return finish(x, 0.55)


def sfx_close(rng):
    x = two_notes(hz(84), hz(77), 0.05, 0.12)
    return finish(x, 0.48)


def sfx_error(rng):
    out = np.zeros(n_of(0.3))
    for i, (f, d) in enumerate(((220, 0.09), (174.6, 0.16))):
        tone = lowpass(osc("square", f, d), 1500) * adsr(n_of(d), 0.005, 0.03, 0.7, 0.04)
        place(out, tone, n_of(i * 0.1))
    return finish(out, 0.33)


def bell(f, seconds, tau):
    n = n_of(seconds)
    x = osc("sine", f, seconds) * decay(n, tau)
    x += 0.35 * osc("sine", f * 2.0, seconds) * decay(n, tau * 0.5)
    x += 0.12 * osc("sine", f * 2.76, seconds) * decay(n, tau * 0.3)
    return x


def sfx_coin(rng):
    out = np.zeros(n_of(0.5))
    place(out, bell(hz(88), 0.08, 0.05), 0)
    place(out, bell(hz(95), 0.42, 0.14), n_of(0.065))
    return finish(out, 0.55)


def thump(f0, f1, seconds, tau):
    return osc("sine", glide([(0, f0), (1, f1)], seconds), seconds) * decay(n_of(seconds), tau)


def sfx_till(rng):
    out = np.zeros(n_of(0.26))
    for at, g in ((0.0, 1.0), (0.075, 0.55)):
        dirt = lowpass(noise(0.14, rng), 1100) * decay(n_of(0.14), 0.035, attack=0.003)
        place(out, dirt + 0.8 * thump(95, 55, 0.14, 0.04), n_of(at), g)
    return finish(out, 0.7)


def sfx_plant(rng):
    out = np.zeros(n_of(0.2))
    place(out, thump(520, 240, 0.08, 0.025), 0)
    rustle = bandpass(noise(0.15, rng), 2200, 6500) * decay(n_of(0.15), 0.04)
    place(out, rustle, n_of(0.02), 0.25)
    return finish(out, 0.5)


def sfx_water(rng):
    d = 0.45
    out = bandpass(noise(d, rng), 500, 4200) * adsr(n_of(d), 0.015, 0.1, 0.45, 0.25) * 0.5
    for _ in range(9):
        at = rng.uniform(0.02, 0.32)
        f0 = rng.uniform(550, 900)
        chirp = osc("sine", glide([(0, f0), (1, f0 * 2.1)], 0.03), 0.03) * decay(n_of(0.03), 0.012)
        place(out, chirp, n_of(at), rng.uniform(0.25, 0.5))
    return finish(out, 0.55)


def sfx_harvest(rng):
    out = np.zeros(n_of(0.55))
    for i, m in enumerate((84, 88, 91)):
        note = osc("tri", hz(m), 0.3) * decay(n_of(0.3), 0.08) + 0.2 * osc("sine", hz(m + 12), 0.3) * decay(n_of(0.3), 0.04)
        place(out, note, n_of(i * 0.06))
    for _ in range(6):
        f = rng.uniform(3500, 6000)
        place(out, osc("sine", f, 0.06) * decay(n_of(0.06), 0.015), n_of(rng.uniform(0.12, 0.4)), 0.12)
    return finish(out, 0.55)


def sfx_pop(rng):
    x = osc("sine", glide([(0, 380), (1, 1150)], 0.06), 0.06) * decay(n_of(0.06), 0.022)
    return finish(x, 0.5)


def sfx_door(rng):
    out = np.zeros(n_of(0.55))
    knock = lowpass(noise(0.1, rng), 700) * decay(n_of(0.1), 0.025) + thump(140, 90, 0.1, 0.04)
    place(out, knock, 0)
    d = 0.32
    f = glide([(0, 170), (0.6, 230), (1, 205)], d) * (1 + 0.03 * np.sin(2 * np.pi * 23 * times(n_of(d))))
    creak = bandpass(osc("saw", f, d), 400, 2200) * adsr(n_of(d), 0.04, 0.05, 0.7, 0.1)
    place(out, creak, n_of(0.12), 0.35)
    return finish(out, 0.55)


def voice(f, seconds, lo, hi, env):
    return bandpass(osc("saw", f, seconds), lo, hi) * env


def sfx_pet_chicken(rng):
    out = np.zeros(n_of(0.5))
    for i in range(2):
        d = 0.07
        f = glide([(0, 640), (1, 540)], d)
        place(out, voice(f, d, 800, 2600, adsr(n_of(d), 0.006, 0.02, 0.7, 0.025)), n_of(i * 0.11))
    d = 0.2
    f = glide([(0, 680), (0.35, 920), (1, 600)], d)
    place(out, voice(f, d, 800, 2800, adsr(n_of(d), 0.01, 0.05, 0.7, 0.06)), n_of(0.24))
    return finish(out, 0.5)


def sfx_pet_cow(rng):
    d = 0.95
    t = times(n_of(d))
    f = glide([(0, 118), (0.25, 128), (1, 98)], d) * (1 + 0.012 * np.sin(2 * np.pi * 5 * t))
    x = lowpass(osc("saw", f, d), 900, order=3)
    x = x + 0.6 * bandpass(x, 300, 750)
    x *= adsr(n_of(d), 0.09, 0.15, 0.85, 0.28)
    return finish(x, 0.45)


def sfx_pet_sheep(rng):
    d = 0.6
    t = times(n_of(d))
    f = glide([(0, 300), (0.2, 330), (1, 285)], d) * (1 + 0.025 * np.sin(2 * np.pi * 17 * t))
    trem = 0.75 + 0.25 * np.sin(2 * np.pi * 17 * t)
    x = bandpass(osc("saw", f, d), 450, 2600) * trem * adsr(n_of(d), 0.04, 0.1, 0.8, 0.18)
    return finish(x, 0.5)


def sfx_levelup(rng):
    out = np.zeros(n_of(1.5))
    for i, m in enumerate((72, 76, 79, 84)):
        d = 1.2 - i * 0.09
        note = osc("tri", hz(m), d) * decay(n_of(d), 0.35) + 0.3 * osc("sine", hz(m + 12), d) * decay(n_of(d), 0.18)
        place(out, note, n_of(i * 0.09), 0.8)
    for m in (72, 76, 79):
        d = 1.0
        place(out, osc("sine", hz(m), d) * adsr(n_of(d), 0.05, 0.2, 0.5, 0.6), n_of(0.36), 0.35)
    for _ in range(10):
        place(out, osc("sine", rng.uniform(3000, 6500), 0.08) * decay(n_of(0.08), 0.02), n_of(rng.uniform(0.35, 1.0)), 0.08)
    out = reverb(out, impulse(1.2, 1.0, rng), 0.35)
    return finish(out, 0.65)


def sfx_sleep(rng):
    out = np.zeros(n_of(2.0))
    for i, m in enumerate((79, 76, 72, 74, 67)):
        d = 1.2
        note = osc("sine", hz(m), d) * decay(n_of(d), 0.45) + 0.15 * osc("sine", hz(m) * 3.0, d) * decay(n_of(d), 0.12)
        place(out, note, n_of(i * 0.24), 0.7 if i < 4 else 0.55)
    out = reverb(out, impulse(1.6, 1.4, rng), 0.45)
    return finish(out, 0.45)


def sfx_rooster(rng):
    out = np.zeros(n_of(1.25))
    syllables = [
        (0.0, 0.13, [(0, 520), (1, 720)]),
        (0.15, 0.11, [(0, 720), (1, 660)]),
        (0.28, 0.15, [(0, 660), (1, 930)]),
        (0.46, 0.62, [(0, 900), (0.25, 1020), (1, 610)]),
    ]
    for at, d, pts in syllables:
        t = times(n_of(d))
        f = glide(pts, d) * (1 + 0.02 * np.sin(2 * np.pi * 9 * t))
        place(out, voice(f, d, 700, 3200, adsr(n_of(d), 0.012, 0.04, 0.8, min(0.12, d / 2))), n_of(at))
    return finish(out, 0.42)


def sfx_cook(rng):
    d = 0.75
    hiss = highpass(noise(d, rng), 3200) * adsr(n_of(d), 0.05, 0.1, 0.6, 0.3) * 0.35
    for _ in range(26):
        pop = highpass(noise(0.012, rng), 1500) * decay(n_of(0.012), 0.003)
        place(hiss, pop, n_of(rng.uniform(0, d - 0.02)), rng.uniform(0.3, 0.9))
    return finish(hiss, 0.42)


def sfx_step(rng):
    x = lowpass(noise(0.08, rng), 450) * decay(n_of(0.08), 0.018) + 0.7 * thump(80, 60, 0.08, 0.025)
    return finish(x, 0.22)


SFX = {
    "click": sfx_click, "tick": sfx_tick, "open": sfx_open, "close": sfx_close, "error": sfx_error,
    "coin": sfx_coin, "till": sfx_till, "plant": sfx_plant, "water": sfx_water, "harvest": sfx_harvest,
    "pop": sfx_pop, "door": sfx_door, "pet_chicken": sfx_pet_chicken, "pet_cow": sfx_pet_cow,
    "pet_sheep": sfx_pet_sheep, "levelup": sfx_levelup, "sleep": sfx_sleep, "rooster": sfx_rooster,
    "cook": sfx_cook, "step": sfx_step,
}


# ----------------------------------------------------------------------------- rain loop

def rain_loop(rng, seconds=12.0):
    n = n_of(seconds)
    # Pink-ish noise built in the frequency domain is periodic by construction.
    f = np.fft.rfftfreq(n, 1 / SR)
    spec = (rng.standard_normal(len(f)) + 1j * rng.standard_normal(len(f))) / np.sqrt(np.maximum(f, 20))
    bed = np.fft.irfft(spec, n)
    bed = bandpass(bed, 250, 5500, circular=True)
    bed /= np.max(np.abs(bed))
    out = bed * 0.55
    for _ in range(int(seconds * 34)):
        d = rng.uniform(0.01, 0.025)
        lo = rng.uniform(1500, 3500)
        drop = bandpass(noise(d, rng), lo, lo * 2.2) * decay(n_of(d), d / 4)
        place(out, drop, int(rng.uniform(0, n)), rng.uniform(0.08, 0.3), wrap=True)
    out -= np.mean(out)
    return out * (0.5 / np.max(np.abs(out)))


# ----------------------------------------------------------------------------- music

def chord_tones(root, quality):
    third = 3 if quality == "m" else 4
    return [root, root + third, root + 7]


def voiced(tones, low, high):
    out = []
    for m in tones:
        while m < low:
            m += 12
        while m >= high:
            m -= 12
        out.append(m)
    return sorted(out)


def parse_chord(name):
    quality = "m" if name.endswith("m") else ""
    return midi(name[:-1] + "3" if quality else name + "3"), quality


def pan(sig, p):
    """Equal-power pan, p in -1..1."""
    a = (p + 1) * np.pi / 4
    return np.cos(a) * sig, np.sin(a) * sig


class Song:
    def __init__(self, bpm, bars, beats=4):
        self.spb = 60.0 / bpm
        self.length = n_of(bars * beats * self.spb)
        self.left = np.zeros(self.length)
        self.right = np.zeros(self.length)

    def at(self, beat):
        return n_of(beat * self.spb)

    def add(self, sig, beat, gain=1.0, p=0.0):
        l, r = pan(sig, p)
        place(self.left, l, self.at(beat), gain, wrap=True)
        place(self.right, r, self.at(beat), gain, wrap=True)

    def render(self, rng, rt60, wet, bright=5000):
        stereo = []
        for i, ch in enumerate((self.left, self.right)):
            ir = impulse(min(rt60 * 1.2, 3.5), rt60, np.random.default_rng(100 + i), predelay=0.02, bright=bright)
            stereo.append(reverb(ch, ir, wet, circular=True))
        x = np.vstack(stereo)
        x -= x.mean(axis=1, keepdims=True)
        x = np.tanh(1.2 * x / np.max(np.abs(x))) / np.tanh(1.2)
        return x * 0.82


def marimba(f, seconds):
    n = n_of(seconds)
    return osc("sine", f, seconds) * decay(n, 0.32) + 0.25 * osc("sine", f * 4, seconds) * decay(n, 0.05)


def ocarina(f, seconds, rng):
    n = n_of(seconds)
    t = times(n)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.clip((t - 0.15) / 0.2, 0, 1)
    x = osc("sine", f * vib, seconds) + 0.15 * osc("sine", 2 * f * vib, seconds) + 0.05 * osc("sine", 3 * f * vib, seconds)
    x += 0.02 * bandpass(rng.standard_normal(n), f, f * 4)
    return x * adsr(n, 0.035, 0.12, 0.8, min(0.15, seconds / 3))


def music_box(f, seconds):
    n = n_of(seconds)
    return (osc("sine", f, seconds) * decay(n, 1.1)
            + 0.35 * osc("sine", 2 * f, seconds) * decay(n, 0.35)
            + 0.15 * osc("sine", 3.98 * f, seconds) * decay(n, 0.12))


def pad(tones, seconds, cutoff, rng):
    n = n_of(seconds)
    left = np.zeros(n)
    right = np.zeros(n)
    for m in tones:
        f = hz(m)
        left += osc("saw", f * 0.997, seconds) + osc("saw", f * 1.004, seconds)
        right += osc("saw", f * 1.003, seconds) + osc("saw", f * 0.996, seconds)
    env = adsr(n, 0.5, 0.4, 0.8, 0.6)
    return lowpass(left, cutoff, 3) * env, lowpass(right, cutoff, 3) * env


def melody(song, notes, start_beat, voice_fn, gain, p=0.0):
    beat = start_beat
    for name, length in notes:
        if name != "-":
            song.add(voice_fn(hz(midi(name)), length * song.spb + 0.25), beat, gain, p)
        beat += length


DAY_CHORDS = ["F", "Dm", "Bb", "C", "F", "Am", "Bb", "C", "Dm", "Bb", "F", "C", "Bb", "C", "F", "F"]
DAY_MELODY = [
    ("A4", 1), ("C5", 1), ("F5", 1.5), ("E5", 0.5),
    ("D5", 1), ("C5", 0.5), ("A4", 0.5), ("F4", 2),
    ("D5", 1), ("F5", 1), ("D5", 0.5), ("C5", 0.5), ("Bb4", 1),
    ("C5", 1.5), ("D5", 0.5), ("E5", 1), ("G5", 1),
    ("A5", 1.5), ("G5", 0.5), ("F5", 1), ("C5", 1),
    ("E5", 1), ("C5", 0.5), ("E5", 0.5), ("A5", 2),
    ("G5", 1), ("F5", 0.5), ("D5", 0.5), ("F5", 1), ("D5", 1),
    ("E5", 1), ("G5", 1), ("C5", 2),
    ("F5", 1), ("E5", 0.5), ("D5", 0.5), ("A4", 2),
    ("Bb4", 1), ("D5", 1), ("F5", 1), ("D5", 1),
    ("C5", 1), ("A4", 1), ("C5", 1), ("F5", 1),
    ("E5", 2), ("G5", 1), ("E5", 1),
    ("D5", 1), ("F5", 1), ("Bb5", 1), ("A5", 0.5), ("G5", 0.5),
    ("G5", 1.5), ("F5", 0.5), ("E5", 1), ("C5", 1),
    ("F5", 2), ("A5", 1), ("G5", 1),
    ("F5", 3), ("-", 1),
]


def music_day(rng):
    song = Song(bpm=96, bars=len(DAY_CHORDS))
    bar = 4 * song.spb
    for i, name in enumerate(DAY_CHORDS):
        root, q = parse_chord(name)
        tones = chord_tones(root, q)
        l, r = pad(voiced(tones, 57, 69), bar + 0.6, 1300, rng)
        place(song.left, l, song.at(i * 4), 0.05, wrap=True)
        place(song.right, r, song.at(i * 4), 0.05, wrap=True)
        bass = root - 12 if root >= 48 else root
        for beat, m in ((0, bass), (2, bass + 7)):
            n = n_of(1.6 * song.spb)
            b = (osc("sine", hz(m), 1.6 * song.spb) + 0.3 * osc("tri", hz(m), 1.6 * song.spb)) * decay(n, 0.5, attack=0.01)
            song.add(b, i * 4 + beat, 0.33)
        arp = voiced(tones, 60, 72)
        pattern = [arp[0], arp[1], arp[2], arp[0] + 12, arp[2], arp[1], arp[0] + 12, arp[2]]
        for k, m in enumerate(pattern):
            song.add(marimba(hz(m), 0.6), i * 4 + k * 0.5, 0.16, -0.35 if k % 2 == 0 else -0.15)
        for k in range(8):
            shaker = highpass(rng.standard_normal(n_of(0.05)), 6000) * decay(n_of(0.05), 0.012)
            song.add(shaker, i * 4 + k * 0.5 + 0.02, 0.035 if k % 2 else 0.018, 0.4)
    melody(song, DAY_MELODY, 0, lambda f, d: ocarina(f, d, rng), 0.28, 0.1)
    return song.render(rng, rt60=1.6, wet=0.22)


NIGHT_CHORDS = ["Am", "F", "C", "G", "Am", "F", "E", "E", "F", "G", "Em", "Am", "Dm", "E", "Am", "Am"]
NIGHT_MELODY = [
    ("E5", 2), ("A5", 1), ("G5", 1),
    ("F5", 2), ("C5", 2),
    ("E5", 1), ("D5", 1), ("C5", 1), ("G4", 1),
    ("B4", 2), ("D5", 2),
    ("C5", 1), ("E5", 1), ("A5", 2),
    ("A5", 1), ("G5", 1), ("F5", 2),
    ("E5", 2), ("B4", 2),
    ("G#4", 3), ("-", 1),
    ("A5", 2), ("C6", 1), ("A5", 1),
    ("B5", 2), ("G5", 2),
    ("G5", 1), ("E5", 1), ("B4", 2),
    ("C5", 2), ("E5", 2),
    ("F5", 2), ("D5", 1), ("F5", 1),
    ("E5", 2), ("B4", 2),
    ("A4", 2), ("C5", 1), ("E5", 1),
    ("A4", 3), ("-", 1),
]


def music_night(rng):
    song = Song(bpm=68, bars=len(NIGHT_CHORDS))
    bar = 4 * song.spb
    for i, name in enumerate(NIGHT_CHORDS):
        root, q = parse_chord(name)
        tones = chord_tones(root, q)
        l, r = pad(voiced(tones, 55, 67), bar + 1.0, 750, rng)
        place(song.left, l, song.at(i * 4), 0.07, wrap=True)
        place(song.right, r, song.at(i * 4), 0.07, wrap=True)
        bass = root - 12 if root >= 48 else root
        n = n_of(3.8 * song.spb)
        song.add(osc("sine", hz(bass), 3.8 * song.spb) * adsr(n, 0.08, 0.5, 0.6, 0.8), i * 4, 0.3)
        harp = voiced(tones, 57, 69)
        for k, m in enumerate([harp[0], harp[1], harp[2], harp[1]]):
            song.add(marimba(hz(m), 0.9), i * 4 + k, 0.09, 0.3)
    melody(song, NIGHT_MELODY, 0, music_box, 0.22, -0.1)
    return song.render(rng, rt60=2.8, wet=0.35, bright=3500)


# ----------------------------------------------------------------------------- output

def encode(x, path, quality, loop=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = np.clip(np.asarray(x, np.float32), -1, 1)
    channels = 1 if x.ndim == 1 else x.shape[0]
    data = np.ascontiguousarray(x.T if channels > 1 else x).astype("<f4").tobytes()
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(channels),
           "-i", "-", "-c:a", "libvorbis", "-q:a", str(quality), "-map_metadata", "-1"]
    if loop:
        cmd += ["-metadata", "ANDROID_LOOP=true"]
    subprocess.run(cmd + [path], input=data, check=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", default=os.path.join(ROOT, "assets"))
    ap.add_argument("--only", default="", help="comma-separated ids (sfx ids, rain, day, night)")
    args = ap.parse_args()
    only = set(filter(None, args.only.split(",")))

    def wanted(name):
        return not only or name in only

    for i, (name, fn) in enumerate(SFX.items()):
        if wanted(name):
            encode(fn(np.random.default_rng(1000 + i)), os.path.join(args.out, "sfx", f"{name}.ogg"), 5)
            print("sfx", name)
    if wanted("rain"):
        encode(rain_loop(np.random.default_rng(7)), os.path.join(args.out, "sfx", "rain.ogg"), 3, loop=True)
        print("loop rain")
    if wanted("day"):
        encode(music_day(np.random.default_rng(11)), os.path.join(args.out, "music", "day.ogg"), 4, loop=True)
        print("music day")
    if wanted("night"):
        encode(music_night(np.random.default_rng(13)), os.path.join(args.out, "music", "night.ogg"), 4, loop=True)
        print("music night")


if __name__ == "__main__":
    main()
