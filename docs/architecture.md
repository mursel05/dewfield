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
| Rules | `model` | nothing outside itself | `FarmTest`, `LeisureTest`, `RemindersTest`, `SaveCodecTest` |
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
- `Leisure` (`farm.leisure`) holds the pastimes: fountain wishes, bench rest, shooting stars, the rainbow, sparkles and keepsakes. Finding a keepsake goes to `farm.events` too.
- `DailyGift` works on real calendar days. It takes "today" as a number, so tests pass any day and time zone.
- `Reminders.plan(farm, now, zone)` turns the farm's state into the phone reminders to leave behind, as `Msg`s with times. It is pure, so `RemindersTest` covers quiet hours, gaps and priorities.

### World (`world`)

- `Locations` builds the house, the farm and the market from ASCII maps. Objects carry an id, a kind and a footprint.
- `World` turns a tap into an intent:
  - walk somewhere;
  - work a plot: till, plant, water, harvest or clear, picked from the plot's state and the selected seed;
  - use an object: shop, trough, machine, bed, TV, stove, fountain, bench, shelf or exit;
  - pick up today's sparkle.
- Plot taps queue up, so the farmer walks the queue in order. `PathFinder` runs A* on the walkable grid.
- Sitting on a bench puts the farmer on the seat and remembers the cell they came from. Walking anywhere stands them up there, and saves store that cell, so the farmer never ends up inside the bench.
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
- `CameraRig` measures the view in tiles across the screen's short side (10.5 outdoors, the room width indoors). Screens squatter than 9:16 keep a 9:16 phone's rows and see extra width.
- `Lighting` renders a half-resolution light map, cleared to the time-of-day ambient colour with lights added, and multiplies it over the scene.
- The UI is scene2d on a 720x1280 portrait `ExtendViewport` (see ADR 0003):
  - The stage is always at least 720 wide and 1280 tall: tall phones get a taller stage, tablets a wider one.
  - `Ui.safe` (a `SafeArea`) holds the notch and cutout insets in stage units. The HUD, panels and title keep their controls inside it.
  - `Hud` holds the clock, coins, energy and level pills at the top left, round buttons at the top right, and the can, task queue and a sideways-scrolling seed bar at the bottom, within thumb reach.
  - Panels are 700 wide. Lists and shops take the full height (`Modal.FILL`) and stack the list over a fixed-height detail pane (`ListDetail`). Short panels are as tall as their content (`Modal.FIT`).
  - `Modal` is the base class for every panel. `ModalHost` dims the world and shows one panel at a time, queueing the rest.
  - The HUD fades out while a panel is open, because panels show the coins themselves.
  - `Sky` sits between the world and the HUD. It draws the shooting stars and the rainbow in screen space and takes only taps that land on them; every other tap falls through to the world.

### Services

- `Strings` loads `assets/i18n/strings_<lang>.properties` and falls back to English for missing keys.
- `Audio` plays the synthesized `.ogg` effects with slight pitch jitter and crossfades the day and night music.
- `SaveStore` writes the save atomically (temp file, then rename) and keeps the previous save as a backup.
- `SaveCodec` migrates old saves. Version 3 moved the field to portrait: version 2 plots are moved block by block onto `Locations.FIELD`.
- `Settings` stores the language, sound, music and reminder choices in libGDX `Preferences`.
- `Notifier` is the one platform hook for local notifications. `DewfieldGame` cancels them when the game comes to the front and plans new ones when it goes to the background (see ADR 0004). Android implements it with `AlarmManager` (`AndroidNotifier`, `ReminderReceiver`); the desktop build uses `Notifier.None`.

## Frame order

`world.update` → drain world events → drain farm events → camera follow → renderer update → HUD update → music and hints → draw world → light map → stage act and draw.

## Threading

Everything runs on the render thread. There is no background work. The one exception is Android's `ReminderReceiver`, which posts a reminder the game planned earlier, outside the game. Saves are small (~20 KB) and written on travel, sleep, pause and every 45 s.
