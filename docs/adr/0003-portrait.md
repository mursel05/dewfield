# ADR 0003: Portrait orientation

- **Status:** accepted (2026-11-21)
- **Amends:** ADR 0001, which asked for landscape

## Context

v0.2.0 shipped in landscape. After playing it, the owner asked for the game to be portrait everywhere:
a farm game is played in short sessions, often with one hand, and phones are held upright.
Rotating the screen isn't enough. Landscape maps, side-by-side panels and a HUD spread along a wide
top edge all waste a tall screen.

## Decision

- **Orientation:** `sensorPortrait` on Android. The desktop window opens at 450x900.
- **UI:** a 720x1280 `ExtendViewport`. Layouts must work from 3:4 (tablets, a 960x1280 stage) to 9:21 (a 720x1680 stage).
  - Panels are one column, 700 wide. Lists stack over a fixed-height detail pane.
  - Short panels size to their content (`Modal.FIT`). Lists and shops take the full height (`Modal.FILL`).
  - The HUD keeps status pills at the top left and round buttons at the top right. The seed bar, can and task queue sit at the bottom, within thumb reach.
  - The app draws under notches (`renderUnderCutout`), and the UI keeps clear of `Gdx.graphics.safeInset*` through `Ui.SafeArea`.
- **Camera:** the view is measured in tiles across the short side (10.5 outdoors). Screens squatter than 9:16 keep a 9:16 phone's rows and see more width, so tablets don't get a letterbox.
- **Maps:** redrawn tall. The farm is 22x42, the market 16x30 and the house 10x15. Field blocks are 4 plots wide and 6 tall, so two sit side by side on screen.
- **Saves:** version 3. Version 2 saves move their plots block by block onto the new field and forget the old standing spot.

## Consequences

- Every panel was re-laid out. New panels must be designed for one column.
- The screenshot tour runs at portrait sizes (720x1600, 720x1280, 960x1280). The emulator smoke test computes the "New farm" tap from the portrait screen size.
- Landscape is no longer supported. A landscape desktop window still runs, with the camera falling back to tiles of height.
