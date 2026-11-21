# Architecture

Dewfield is a single libGDX application, `DewfieldGame`. Two thin launchers start it: `AndroidLauncher` and the desktop `DesktopLauncher`. Everything that matters lives in `core/`.

```
             ┌──────────────── core ────────────────────────────────────────┐
 input ───►  │ GameScreen ──tap/drag──► World ──tasks──► Farm (rules)       │
 (touch)     │     ▲  │                   │  events        │  events       │
             │     │  └──draw──► WorldRenderer + Lighting + Effects         │
             │     └──── UI (Hud, Modal panels) ◄── Outcome/Msg ───┘       │
             │ Strings (EN/AZ) · Audio · Assets · SaveStore (JSON)          │
             └──────────────────────────────────────────────────────────────┘
```

## Layers

| Layer | Package | Knows about | Tested by |
| --- | --- | --- | --- |
| Rules | `model` | nothing outside itself | `FarmTest`, `SaveCodecTest` |
| World | `world` | `model` | `WorldTest`, `PathFinderTest`, `MapTest` |
| Presentation | `render`, `ui`, `screen` | everything, plus libGDX | screenshot tour |
| Services | `app`, `audio`, `i18n`, `gfx` | libGDX | `StringsTest` |

### Rules (`model`)

- `GameState` is the whole save. It is plain serializable data.
- `Farm` wraps it and implements every action: till, plant, water, harvest, buy, sell, feed, pet, load machine, cook, upgrade, sleep and so on.
- Each action returns `Outcome.Ok(msg, gains)` or `Outcome.Fail(msg)`.
- `Msg` is an i18n key plus arguments. Arguments can be nested `Msg`s, for example an item name.
- Things that happen on their own go to `farm.events`, for example `LevelUp` and `Exhausted`.
- `endDay()` runs the night: crops grow if watered, animals eat from troughs, products appear, machines finish, prices move, orders refresh and the weather rolls. It returns a `MorningReport`.
- `ItemDb` holds all balance data. The seeded RNG (`rng(tag)`) makes each day reproducible for a given farm seed.

### World (`world`)

- `Locations` builds the house, the farm and the market from ASCII maps. Objects carry an id, a kind and a footprint.
- `World` turns a tap into an intent:
  - walk somewhere;
  - work a plot: till, plant, water, harvest or clear, picked from the plot's state and the selected seed;
  - use an object: shop, trough, machine, bed, TV, stove or exit.
- Plot taps queue up, so the farmer walks the queue in order. `PathFinder` runs A* on the walkable grid.
- `World` emits `WorldEvent`s (Toast, Float, Sound, Fx, Open, Travel) for the screen to present.

### Presentation

- `GameScreen` owns the input multiplexer:
  - scene2d stage first;
  - then keys (Back);
  - then a `GestureDetector`: tap walks, long press inspects, drag paints plots or pans, pinch zooms.
- It drains both event queues every frame and handles travel and sleep fades.
- `WorldRenderer` draws in tile units:
  - base tiles, then dual-grid terrain layers (water, soil, path, stone);
  - plots, then y-sorted sprites (props, objects, crops, animals, player, vendors);
  - overlays, then particles.
- `Lighting` renders a half-resolution light map, cleared to the time-of-day ambient colour with lights added, and multiplies it over the scene.
- The UI is scene2d on a 1280x720 `ExtendViewport`:
  - `Hud` holds the clock, coins, energy, level, seed bar and buttons.
  - `Modal` is the base class for every panel. `ModalHost` dims the world and shows one panel at a time, queueing the rest.
  - The HUD fades out while a panel is open, because panels show the coins themselves.

### Services

- `Strings` loads `assets/i18n/strings_<lang>.properties` and falls back to English for missing keys.
- `Audio` plays the synthesized `.ogg` effects with slight pitch jitter and crossfades the day and night music.
- `SaveStore` writes the save atomically (temp file, then rename) and keeps the previous save as a backup.
- `Settings` stores the language, sound and music choices in libGDX `Preferences`.

## Frame order

`world.update` → drain world events → drain farm events → camera follow → renderer update → HUD update → music and hints → draw world → light map → stage act and draw.

## Threading

Everything runs on the render thread. There is no background work. Saves are small (~20 KB) and written on travel, sleep, pause and every 45 s.
