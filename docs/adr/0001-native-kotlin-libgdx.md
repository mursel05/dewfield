# ADR 0001: Native Kotlin on libGDX

- **Status:** accepted (2026-11-21)
- **Supersedes:** the Godot 4 prototype (v0.1)
- **Amended by:** ADR 0003 (the game is portrait, not landscape)

## Context

v2 must:

- be a native Android game, in landscape, with tap-to-move;
- use smooth vector art, not pixel art;
- run well on low-end phones;
- leave iOS possible later.

The Godot prototype worked, but its Android export uses prebuilt templates, and the pixel-art pipeline didn't fit the new direction. The owner works mostly in TypeScript and Kotlin-friendly ecosystems and wants code that is easy to read and test.

## Options

| Option | For | Against |
| --- | --- | --- |
| Godot 4 (keep) | Editor, scenes | Exported, not native. GDScript is weakly typed. The rules are hard to unit test outside the engine. |
| Unity | Big ecosystem | Heavy runtime and licensing churn. C# tooling outside the editor. Overkill for 2D. |
| Jetpack Compose + Canvas | Pure Android | A game loop, sprite batching and camera have to be hand-built. No iOS path. |
| **Kotlin + libGDX** | Native Android (and desktop for fast iteration). Plain Kotlin rules testable with JUnit. Sprite batching, scene2d UI, FreeType fonts. Small APK. Mature (10+ years). iOS possible later through RoboVM/MobiVM. | No visual editor, so maps are data. More code for UI. |

## Decision

Use Kotlin with libGDX:

- `core` holds the game. The rules are pure Kotlin with no engine types.
- Thin `android` and `desktop` launchers start it.
- Art is generated from SVG code and packed into an atlas. Audio is synthesized.
- The desktop build doubles as the screenshot tool.

## Consequences

- **Testing:** the game rules have fast unit tests (60+), which is what keeps balance changes safe.
- **UI:** it is code (scene2d), so a small widget kit (`Ui`, `PanelKit`) keeps panels consistent.
- **iOS:** this would need a MobiVM launcher module later. Nothing in `core` blocks it.
- **Builds:** the Android build needs Google's Maven repository. Environments that block it can still build and test everything else, and CI builds the APK.
