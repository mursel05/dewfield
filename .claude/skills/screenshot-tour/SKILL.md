---
name: screenshot-tour
description: Run Dewfield's desktop screenshot tour and review the screens. Use after changing UI, art, rendering, lighting or strings, or when asked to show what the game looks like.
---

# Screenshot tour

The desktop build drives a fixed farm (seed 1234) through 25 scripted scenes and saves a PNG of each. The script is `desktop/src/main/kotlin/dev/mursal/dewfield/desktop/Tour.kt`.

## Run it

```bash
OUT="$PWD/build/tour" && rm -rf "$OUT" && mkdir -p "$OUT"
xvfb-run -a -s "-screen 0 1600x720x24" ./gradlew :desktop:run -q --args="--tour $OUT --size 1600x720"
```

- On a machine with a display, drop the `xvfb-run …` prefix.
- `[Audio] Missing sound` lines mean an `.ogg` is missing from `assets/sfx`.
- ALSA errors in containers are harmless: the run simply has no audio device.

## What the scenes show

| # | Scene |
| --- | --- |
| 01–05 | title, house in the morning, farm, queued tasks, farm working |
| 06–11 | market, seed shop, feed shop, sell, workshop, orders |
| 12–18 | animals, mill, bag, stove, TV, sleep prompt, morning report |
| 19–22 | sunset, night (lights, fireflies, lantern), rain, market at night |
| 23–25 | pause menu, seed shop and level up in Azerbaijani |

## Review

- Look at each affected screenshot at full size. To skim them all, tile them 2x2 at half size, then open the full size of anything suspicious.
- Use the checklist in `.claude/agents/ui-reviewer.md`.
- Add a scene to `Tour.kt` when you build a new panel.
