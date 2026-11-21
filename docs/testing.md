# Testing

## Unit tests (`./gradlew :core:test`)

They run on the JVM with no graphics, in a few seconds.

| Suite | Covers |
| --- | --- |
| `FarmTest` | The full rules: planting and growth, watering and withering, quality, rain, energy and passing out, buying and selling with price drift and saturation, hot items, animals, feed, troughs, mood, products, machines and recipes, cooking, upgrades, field blocks, levels and unlocks, orders. |
| `SaveCodecTest` | Save round trip, missing fields falling back to defaults, damaged values repaired, garbage and future versions rejected. |
| `WorldTest` | Tap intents, the drag-to-queue plot queue, walking to the well when the can is empty, troughs and coop, petting through the gate, locked blocks, travel to the market, energy running out mid-queue. |
| `PathFinderTest`, `MapTest` | A* (shortest path, no corner cutting, nearest goal), every object and exit reachable from every spawn, enclosed pens, field blocks matching the map. |
| `StringsTest` | Both languages have the same keys and placeholders. Every key the code uses exists, every game id has a name, and the font covers every character. |

Rules take a seed, so tests are deterministic. Use `newFarm()` in `FarmTest` and call `sleep()` to advance days.

## Screenshot tour (visual check)

```bash
xvfb-run -a -s "-screen 0 1600x720x24" ./gradlew :desktop:run -q \
  --args="--tour $PWD/build/tour --size 1600x720"
```

- `desktop/.../Tour.kt` drives a fixed farm (seed 1234) through 25 scenes:
  - title, house and farm
  - every panel
  - sunset, night and rain
  - three screens in Azerbaijani
- It writes PNGs. Check them after any UI, art or rendering change.
- Other aspect ratios are worth a look too: `--size 1280x960` (4:3 tablet) and `--size 2400x1080` (20:9 phone).

## Android

CI builds the release APK and runs an emulator smoke test (`.github/scripts/smoke-test.sh`). The test:

1. Installs the APK on an API 34 x86_64 emulator and launches it.
2. Fails if the process dies or logcat shows a fatal exception.
3. Taps into the game, waits, and checks again.
4. Uploads screenshots as artifacts.

Before a release, also play on a real phone: tap-to-move, drag-to-plant, pinch zoom, Back button, pause/resume, and a save after the app is killed.
