---
name: screenshot-tour
description: Run Dewfield's desktop screenshot tour and review the screens. Use after changing UI, art, rendering, lighting or strings, or when asked to show what the game looks like.
---

# Screenshot tour

The desktop build drives a fixed farm (seed 1234) through 34 scripted scenes and saves a PNG of each. The script is `desktop/src/main/kotlin/dev/mursal/dewfield/desktop/Tour.kt`.

## Run it

```bash
OUT="$PWD/build/tour" && rm -rf "$OUT" && mkdir -p "$OUT"
xvfb-run -a -s "-screen 0 720x1600x24" ./gradlew :desktop:run -q --args="--tour $OUT --size 720x1600"
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
| 23–25 | pause menu (with the reminders switch), seed shop and level up in Azerbaijani |
| 26–28 | daily gift, wishing fountain and keepsake shelf in Azerbaijani |
| 29–31 | sitting on a bench at sunset, a shooting star at night, the rainbow the morning after rain |
| 32–34 | a found keepsake, the daily gift, and the gift opened with the reminder offer |

## Review

- Look at each affected screenshot at full size. To skim them all, tile them 2x2 at half size, then open the full size of anything suspicious.
- Use the checklist in `.claude/agents/ui-reviewer.md`.
- Add a scene to `Tour.kt` when you build a new panel.
