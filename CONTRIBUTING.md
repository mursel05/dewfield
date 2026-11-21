# Contributing to Dewfield

Thanks for helping. This page covers the workflow. `AGENTS.md` covers the architecture and code rules, and `docs/` has the details.

## Setup

- JDK 17 or newer (21 recommended). Gradle comes from the wrapper.
- Optional:
  - The Android SDK (platform 36), to build the APK locally.
  - Python 3 with numpy plus ffmpeg (with libvorbis), to regenerate audio.
  - Node 20+, to regenerate art.

```bash
./gradlew :core:test        # rules, world and string tests
./gradlew :desktop:run      # play on desktop (mouse = finger, scroll = zoom, Esc = back)
```

## Workflow

1. Branch from `main`: `feat/<topic>`, `fix/<topic>`, `chore/<topic>` or `docs/<topic>`.
2. Make one logical change per commit, with an imperative subject (`Fix toast width when text wraps`).
3. Open a pull request using the template. CI must be green: tests, APK build and the emulator smoke test.
4. Squash-merge into `main`. A tag `vX.Y.Z` publishes a GitHub release with the APK (see `docs/deployment.md`).

## Checklists

**Gameplay or balance change**
- Update `ItemDb`, add or adjust tests in `FarmTest`, and update the tables in `docs/game-design.md`.

**New text**
- Add the key to `tools/i18n/strings.py` in English and Azerbaijani, then run `python3 tools/i18n/strings.py`.

**UI change**
- Run the screenshot tour. Attach before and after screenshots to the PR, including one in Azerbaijani.

**New sound**
- Add a generator function to `tools/audio/synth.py`, register the id in `Audio.SOUNDS` and regenerate.

**New art**
- Add the shape to `tools/art/src/*.js`, rebuild the atlas and check it in the tour.

## Reporting bugs

Use the bug report form. Include the device, the Android version, the steps, and a screenshot or screen recording if you can.
