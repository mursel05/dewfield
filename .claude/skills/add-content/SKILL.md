---
name: add-content
description: Add a new crop, animal, feed, item, machine, recipe or meal to Dewfield end to end (balance data, strings in two languages, art, tests, docs). Use whenever the game gets new content.
---

# Adding content

Every id is used as a data key, an i18n key suffix and an atlas region name, so a new thing touches the same places every time. Work through the list for the kind you add.

## 1. Balance data: `core/.../model/ItemDb.kt`

| Kind | What to add |
| --- | --- |
| Crop | An `ItemDef` for the seed (`buyPrice`, `unlockLevel`), an `ItemDef` for the produce (`sellPrice`, `energy`, `hasQuality = true`) and a `CropDef` (`growDays`, `regrowDays`, yield, `witherDays`, `xp`). |
| Animal | An `AnimalDef` with its own `feed`, a `TroughDef` for that feed, an `ItemDef` for the product, an `ItemDef` for the feed if shops sell it, and a pen on the farm map (`world/Locations.kt`). |
| Machine | A `MachineDef`, one or more `RecipeDef`s and an object on the farm map. |
| Meal | An `ItemDef` (category `MEAL`, energy) and a `MealDef`. |

## 2. Strings: `tools/i18n/strings.py`

Add both English and Azerbaijani for:

- `item.<id>`
- `animal.<id>`, `feed.<feed>` and `trough.<id>`, for animals
- `machine.<id>` and `machine.<id>.about`, for machines
- `about.<objectId>`, for every new map object

The hook regenerates the `.properties` files. Otherwise run `python3 tools/i18n/strings.py`. `StringsTest` fails if a name is missing.

## 3. Art: `tools/art/src/`

- Icons: `icons.js` or `produce.js`.
- Crop growth stages: `crops.js`, named `crop/<id>_0` to `crop/<id>_2`, plus an optional `crop/<id>_regrow` for regrowing crops.
- Characters and animals: `characters.js`.
- Objects: `objects.js`.

Render the art and pack the atlas:

```bash
node tools/art/build.js <id>
./gradlew :tools:packAtlas
```

Keep the soft vector style. No pixel art.

## 4. Tests: `core/src/test/.../model/FarmTest.kt`

Add a test for the new behaviour: it grows in N watered nights, the animal eats only its feed, the recipe output keeps quality, and so on. Then run `./gradlew :core:test`.

## 5. Docs and check

- Add the row to the matching table in `docs/game-design.md`.
- Run the screenshot tour (skill `screenshot-tour`). Check the shop list, the bag and the detail panes in both languages.
