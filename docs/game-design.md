# Game design

The values below mirror `core/.../model/ItemDb.kt` and `Farm.kt`. Update both when you tune the game.

## Day loop

- A day starts at 06:00. One game minute lasts 0.6 real seconds, so a full day is about 18 minutes.
- At midnight the farmer passes out and wakes up with 60 energy instead of 100.
- The market is open 07:00–20:00. Nargiz sells seeds and fertilizer, Orkhan sells feed and animals, Leyla buys produce, and Tural sells upgrades and machines. The orders board is always open.
- Sleeping runs the night: crops grow, animals eat, products appear, machines finish, prices move, new orders arrive and tomorrow's weather is announced on the TV.

## Energy (max 100)

| Action | Cost |
| --- | --- |
| Till | 3 |
| Clear a dead plant | 2 |
| Water, plant, fertilize, harvest, fill a trough | 1 |

Food restores energy. Raw crops give 8–15, meals 40–100.

## Crops

| Crop | Seed price | Grows (watered nights) | Regrows | Yield | Sells for | Withers after | Unlock |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Radish | 10 | 3 | – | 1 | 24 | 4 dry nights | 1 |
| Lettuce | 18 | 4 | – | 1 | 42 | 4 | 1 |
| Wheat | 8 | 4 | – | 2 | 12 | 4 | 1 |
| Clover | 6 | 3 | every 2 | 2 | 8 | 4 | 1 |
| Carrot | 32 | 5 | – | 1 | 80 | 3 | 1 |
| Potato | 40 | 6 | – | 2–3 | 38 | 3 | 2 |
| Tomato | 65 | 7 | every 3 | 2 | 30 | 3 | 2 |
| Corn | 55 | 8 | every 4 | 1 | 48 | 3 | 3 |
| Strawberry | 110 | 8 | every 3 | 3 | 28 | 2 | 4 |
| Pumpkin | 160 | 11 | – | 1 | 450 | 2 | 5 |

- Cheap crops are quick and forgiving. Expensive crops pay more per day but wither sooner, so they reward daily care.
- Rain waters every tilled plot.
- **Quality:**
  - Watered every night and fertilized gives gold quality, worth +50%.
  - Only one of the two gives silver, worth +25%.

## Animals and feed

Each animal eats its own feed from its own trough, overnight.

| Animal | Price | Lives in | Eats (per night) | Trough (capacity) | Product | Unlock |
| --- | --- | --- | --- | --- | --- | --- |
| Chicken | 350 | Coop | 1 grain | Grain hopper (30) | Egg every fed day (30) | 1 |
| Cow | 1400 | Barn | 2 hay | Hay rack (40) | Milk every fed day (75) | 2 |
| Sheep | 1000 | Barn | 1 clover | Clover tray (30) | Wool every 2 fed days (140) | 3 |

- **Feed prices:** grain 6, hay 5, Clover Mix 7. Clover grown in the field also feeds sheep.
- **The mill makes feed:** 1 wheat becomes 5 hay, and 1 corn becomes 10 grain. Both are ready the next morning. Growing feed is cheaper than buying it.
- **Mood (0–10, starts at 5):**
  - It goes up by 1 for each petting (once a day) and for each night the animal is fed.
  - It goes down by 2 for each hungry night.
  - Products are silver at mood 5 or more and gold at 8 or more.
  - A hungry animal makes no progress towards its next product.
- New animals arrive the next morning.

## Machines

| Machine | Price | Unlock | Slots | Recipes |
| --- | --- | --- | --- | --- |
| Mill | owned | 1 | 10 | wheat → 5 hay, corn → 10 grain |
| Cheese press | 1000 | 3 | 5 | milk → cheese (170), keeps quality |
| Loom | 1400 | 4 | 3 | wool → cloth (320), keeps quality |

## Market prices

- Each sellable item has a daily multiplier between 0.70 and 1.45 that drifts overnight. The sell panel shows the trend.
- Every unit sold today lowers that item's price by 2%, down to a floor of 60%. It resets overnight, so spreading sales pays off.
- One **hot item** a day sells for +30%. The TV announces tomorrow's hot item.

## Orders

- The board holds up to 3 orders, each from a different client.
- An order asks only for things the player can currently produce.
- It pays about 1.5–1.9× the base value, plus XP, and expires after 3–5 days.

## Upgrades (Tural)

| Upgrade | Levels (price → value, unlock level) |
| --- | --- |
| Watering can | 20 water → 600: 40 (lv 2) → 1800: 80 (lv 4) |
| Farmland | 1 block → 700: 2 (lv 2) → 1800: 3 (lv 3) → 3500: 4 (lv 5) |
| Coop | 4 chickens → 1500: 8 (lv 3) |
| Barn | 3 animals → 2600: 6 (lv 4) |

## Levels

The total XP needed for levels 1 to 10 is 0, 60, 180, 400, 750, 1250, 2000, 3000, 4500 and 6500. Harvests, products, deliveries and machine goods give XP. Each level-up shows what it unlocked.

## Meals (stove)

| Meal | Ingredients | Energy |
| --- | --- | --- |
| Fried egg | egg | 40 |
| Salad | lettuce, tomato | 50 |
| Veggie stew | potato, carrot | 60 |
| Pancakes | egg, milk, wheat | 75 |
| Pumpkin soup | pumpkin, milk | 100 |

## Start

The player starts with:

- 500 coins
- 6 radish and 4 lettuce seeds
- two chickens, 10 grain and 4 grain already in the hopper
- the mill
- one field block
- three orders
