# Testing

## Unit tests (`./gradlew :core:test`)

They run on the JVM with no graphics, in a few seconds.

| Suite | Covers |
| --- | --- |
| `FarmTest` | The full rules: planting and growth, watering and withering, quality, rain, energy and passing out, buying and selling with price drift and saturation, hot items, animals, feed, troughs, mood, products, machines and recipes, cooking, upgrades, field blocks, levels and unlocks, orders. |
| `SaveCodecTest` | Save round trip, missing fields falling back to defaults, damaged values repaired, garbage and future versions rejected. |
| `LeisureTest` | Wishes (cost, daily limit, the first always true, rain changing tomorrow), bench rest and its cap, shooting stars, the rainbow, sparkles, keepsakes and the shelf reward, the daily gift across days, time zones and a clock turned back. |
| `RemindersTest` | Which reminders get planned, quiet hours, the minimum gap between them, nothing in the past. |
| `WorldTest` | Tap intents, the drag-to-queue plot queue, walking to the well when the can is empty, troughs and coop, petting through the gate, locked blocks, travel to the market, energy running out mid-queue, sitting on a bench, the fountain and shelf, picking up a sparkle, the well's echo. |
| `PathFinderTest`, `MapTest` | A* (shortest path, no corner cutting, nearest goal), every object and exit reachable from every spawn, enclosed pens, field blocks matching the map, sparkle spots on open, reachable ground. |
| `StringsTest` | Both languages have the same keys and placeholders. Every key the code uses exists, every game id has a name, and the font covers every character. |

Rules take a seed, so tests are deterministic. Use `newFarm()` in `FarmTest` and call `sleep()` to advance days.

## Screenshot tour (visual check)

```bash
xvfb-run -a -s "-screen 0 720x1600x24" ./gradlew :desktop:run -q \
  --args="--tour $PWD/build/tour --size 720x1600"
```

- `desktop/.../Tour.kt` drives a fixed farm (seed 1234) through 34 scenes:
  - title, house and farm
  - every panel
  - sunset, night and rain
  - six screens in Azerbaijani
  - the pastimes: daily gift (closed and opened, with the reminder offer), fountain, keepsake shelf, a found keepsake, sitting on a bench at sunset, a shooting star and the rainbow
- The tour pretends the platform has notifications (`Tour.PreviewNotifier`), so the reminder switch and offer show up.
- It writes PNGs. Check them after any UI, art or rendering change.
- The game is portrait. Check the other ends of the range too: `--size 720x1280` (9:16 phone) and `--size 960x1280` (3:4 tablet).
  Match the xvfb screen size to `--size`.

## Android

CI builds the release APK and runs an emulator smoke test (`.github/scripts/smoke-test.sh`). The test:

1. Installs the APK on an API 34 x86_64 emulator and launches it.
2. Fails if the process dies or logcat shows a fatal exception.
3. Taps "New farm" (its position is worked out from the portrait screen size), waits, and checks again.
   A new farm never asks for the notification permission, so no system dialog gets in the way.
4. Uploads screenshots as artifacts.

Before a release, also play on a real phone: tap-to-move, drag-to-plant, pinch zoom, Back button, pause/resume, and a save after the app is killed.
For reminders: turn them on (Android 13+ shows the permission dialog), leave the game, and check one arrives; reboot and check pending ones are kept.
