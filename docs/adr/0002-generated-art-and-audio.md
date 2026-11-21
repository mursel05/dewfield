# ADR 0002: Art and audio generated from code

- **Status:** accepted (2026-11-21)

## Context

- The game needs a full set of sprites, icons, UI skins, sounds and music with one consistent style.
- The owner wants nothing that resembles an existing farm game.
- No artist or composer is involved yet, and stock assets bring licensing questions and an inconsistent look.

## Decision

Generate the art and audio from code, and keep the sources in the repo:

- **Art:** `tools/art/src/*.js` builds SVG for every sprite.
  - Shapes are soft and rounded, palettes are shared and shading is gentle.
  - Headless Chromium renders them to PNG, and `:tools:packAtlas` packs one atlas.
- **Audio:** `tools/audio/synth.py` (numpy, plus ffmpeg/libvorbis) synthesizes 20 effects, a rain loop and two original music loops.
- **Font:** Nunito (SIL Open Font License, see `assets/fonts/OFL.txt`).

## Consequences

- **Licensing:** every asset is original and reproducible, so there are no licensing questions.
- **Iteration:** changing a colour or a sound is a code change you can review in a PR.
- **Quality ceiling:** the result is lower than hand-made art and audio. Assets can be replaced one by one later, because the game only knows atlas region names and sound ids.
- **Rebuilding assets:** this needs Node + Playwright for art, and Python + numpy + ffmpeg for audio. The generated files are committed, so building the game doesn't need these tools.
