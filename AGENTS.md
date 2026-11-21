# AGENTS.md

Guide for AI coding agents (Claude Code, Codex, Cursor, …) and humans working on Dewfield.
Read this before changing code. `CLAUDE.md` imports this file.

## What this is

Dewfield is an original, cozy farm life game for Android, played in portrait:

- Walk with taps.
- Till, plant, water and harvest.
- Feed each animal its own feed.
- Sell at a market with prices that change every day.
- Sleep to start a new day.

It is written in Kotlin on libGDX. The art is soft vector, never pixel art, and the game must not look like any existing farm game.

## Commands

| What | Command |
| --- | --- |
| Unit tests (rules, world, strings) | `./gradlew :core:test` |
| Compile everything but Android | `./gradlew :core:compileKotlin :desktop:compileKotlin` |
| Run on desktop | `./gradlew :desktop:run` |
| Screenshot tour (25 PNGs) | `./gradlew :desktop:run --args="--tour /abs/out/dir --size 720x1600"` (headless: prefix `xvfb-run -a -s "-screen 0 720x1600x24"`) |
| Regenerate strings | `python3 tools/i18n/strings.py` |
| Regenerate sounds and music | `python3 tools/audio/synth.py` (needs numpy and ffmpeg with libvorbis) |
| Regenerate art atlas | `node tools/art/build.js` then `./gradlew :tools:packAtlas` (needs Node and Playwright, see `docs/conventions.md`) |
| Android APK | `./gradlew :android:assembleRelease` (needs an Android SDK; CI builds it on every push) |

The `:android` module is included only when `ANDROID_HOME`, `ANDROID_SDK_ROOT` or `local.properties` points at an SDK.
Set `ANDROID_HOME=` (empty) to leave it out.

## Layout

```
core/      game code, no platform APIs
  model/   rules: Farm, GameState, ItemDb (all balance data), SaveCodec, Msg
  world/   maps (Locations), tap-to-move (World, PathFinder), world events
  render/  WorldRenderer, CameraRig, Lighting, Effects, Spr
  ui/      scene2d HUD and panels (Ui widget kit, Modal, PanelKit, *Panels)
  screen/  TitleScreen, GameScreen (input, events, travel, sleep)
  app/     Settings, SaveStore      audio/ Audio      i18n/ Strings      gfx/ Assets
desktop/   LWJGL3 launcher and the screenshot Tour
android/   Android launcher, manifest, icons
tools/     art pipeline (SVG -> atlas), i18n generator, audio synth
assets/    atlas, fonts, i18n, sfx, music (shared by desktop and Android)
docs/      architecture, game design, conventions, testing, deployment, ADRs
```

## Architecture rules

- **Rules are pure Kotlin.**
  - `model/` and `world/` never touch libGDX graphics, audio or input, so they run in plain unit tests.
  - Rules return `Outcome.Ok` / `Outcome.Fail` with a `Msg` (an i18n key plus arguments), never a display string.
  - They report side effects through event queues (`farm.events`, `world.events`) that `GameScreen` drains.
- **One source of balance data.** Prices, crops, animals, machines, recipes, upgrades and levels live in `ItemDb`. Tune the game there.
- **Every id is a string key.** `item.<id>`, `animal.<id>`, `machine.<id>` and the like must exist in both languages. `StringsTest` enforces this.
- **Panels rebuild, they don't patch.**
  - A `Modal` refills its body in `fill()` after every action, so the numbers are always current.
  - List and detail panels keep their scroll position with `ListDetail`.
- **World units are tiles.** 1 unit = 1 tile and y points up. Maps are taller than wide, for a portrait screen.
- **The UI is portrait.** It uses a 720x1280 `ExtendViewport`, so a layout must work from 3:4 (tablet) to 9:21 (tall phone).
  Keep controls inside `ui.safe` (notches and cutouts) and the main actions within thumb reach at the bottom.
- **Saves are versioned JSON.** They go through `SaveCodec`, are written atomically and keep a backup. When you add state, give it a default value so old saves still load.

## Conventions

- Kotlin official style, 4 spaces, max line ~140. Use `val` over `var`. Avoid `!!`. No new dependency without a reason in the PR.
- KDoc on classes and on anything non-obvious. Comments explain *why*.
- **Text:** never hard-code player-facing text.
  - Add the key to `tools/i18n/strings.py` in English **and** Azerbaijani, then run the generator.
  - Don't edit `assets/i18n/*.properties` by hand.
  - Azerbaijani: don't attach case suffixes to numbers or placeholders (`{0}-ə`). Rephrase instead (`Al: {0}`).
- **Font:** Nunito lacks `→ ← ★ ♥ ✓`. Use icons instead (`check`, `close`, `arrow_up`, …). A test fails if a string uses a glyph the font doesn't have.
- **Icons:** glyph icons (`check`, `close`, `plus`, `minus`, `play`, `queue`, `zzz`) are white and get tinted by `Ui.icon`.
- **Art:** soft vector shapes from `tools/art/src/*.js`. No pixel art, no outlines copied from other games.
- **Commits:** imperative subject ≤ 72 chars (`Add loom recipe for cloth`), body says why. One logical change per commit.

## Before you say it's done

1. `./gradlew :core:test` passes.
2. For UI or rendering changes, run the screenshot tour and look at the affected screens, in English and Azerbaijani.
3. New strings exist in both languages. New balance numbers are reflected in `docs/game-design.md`.
4. Don't claim it works on a phone unless it ran on one (or the CI emulator smoke test passed).

## Don'ts

- Don't commit keystores, `keystore.properties`, `.env` files or tokens. Release signing comes from CI secrets.
- Don't add network calls, analytics or ads.
- Don't put Android-only code in `core/`.
