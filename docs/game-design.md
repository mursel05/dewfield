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

## Pastimes and surprises

Small extras that make the world nicer to spend time in. Every reward is small and capped per day,
so none of them earns more than farming. The numbers live in the "pastimes" block of `ItemDb`.

- **Benches:** one on the farm above the pond, two at the market. Sitting gives 1 energy every 4 game
  minutes, up to 20 a day, twice as fast at sunset (18:00–20:30). The clock keeps running while you sit,
  so a full rest costs 80 game minutes (40 at sunset).
- **Wishing fountain** (market): a wish costs 10 coins, 3 a day. The first wish on a farm always comes true.

  | Wish | Comes true | Gives |
  | --- | --- | --- |
  | Rain tomorrow | 35% | tomorrow's weather turns to rain (not offered when rain is already coming) |
  | A little gift | 30% | a small gift (below) |
  | A burst of energy | 45% | +20 energy (not offered at full energy) |

- **Small gift** (a granted wish, a caught star): 2 seeds the player can already buy that cost 40 or less,
  2 fertilizer, or 5 feed for an animal the farm keeps (fertilizer when it keeps none).
- **Shooting stars:** on nights without rain, from 20:30, a star crosses the sky every 18–35 seconds
  (2.5× as often while sitting on a bench). Tapping one makes a wish: a small gift, once a night.
  The first star ever caught is the star shard keepsake.
- **Rainbow:** the morning after a rainy day, until 10:00. Tapping it finds 15–35 coins, once.
- **Sparkles:** on 60% of days something glints at one of 10 quiet spots on the farm or at the market.
  It is a ground keepsake the player doesn't have yet, or 5–15 coins once they have them all.
- **The well:** tap it five times quickly with a full can and it echoes back.

### Keepsakes

Nine little finds for the shelf at home. Each one gives 10 XP. A full shelf pays 250 coins and 40 XP, once.

| Where | Keepsakes |
| --- | --- |
| Sparkles on the ground | horseshoe, glass marble, pocket watch, spiral shell, stone arrowhead, fern fossil |
| Harvesting clover (5% per harvest) | four-leaf clover |
| Making a wish (8% per wish) | old wishing coin |
| The first shooting star caught | star shard |

### Daily gift

One gift per real calendar day (the phone's time zone), starting after the first night's sleep.
Missing days never resets the calendar, it just waits. Turning the clock back can't claim twice, and a gift
also needs a night's sleep in the game since the last one, so pushing the clock ahead doesn't pay either.
After day 7 it starts over.

| Day | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gift | 25 coins | 2 fertilizer | 4 lettuce seeds | 10 grain | 50 coins | 3 fertilizer | 100 coins and pancakes |

### Reminders (Android)

Local notifications, off until the player says yes. The game offers them once, right after the first
daily gift, and the menu has a switch. Nothing goes over the network.

- When the game goes to the background it plans at most one reminder of each kind, at least 6 hours
  apart and never between 21:00 and 09:00 (those move to 09:30):
  - **Farm news,** 4 hours later: ripe crops, else goods waiting in the coop or barn, else finished
    machine goods, else a trough too low for tonight. Game time only moves while playing, so the news
    is still true when it arrives.
  - **Daily gift:** 3 hours later if it is still unopened, else tomorrow at 10:00.
  - **Come back:** after 3 days away, at 18:00, naming one of the farm's animals.
- Opening the game cancels them all.

## World

The game is played in portrait, so every map is taller than it is wide.

- **Farm (22x42):**
  - The coop and chicken pen are at the top, and the barn with its pen below them.
  - The house and mill are in the middle. The road to the market leaves on the right.
  - The four field blocks are at the bottom, two by two, with the well beside them. Each block is 4 plots wide and 6 tall.
  - Blocks unlock top-left, top-right, bottom-left, bottom-right.
- **Market (16x30):** three stalls along the top (seeds, feed and animals, buyer), Tural's workshop in the middle, the wishing fountain, two benches and the order board below. The road home is on the left.
- **House (10x15):** the bed, the keepsake shelf and the TV in a nook at the top, the stove under its own wall on the right, and the living room above the door.

## Start

The player starts with:

- 500 coins
- 6 radish and 4 lettuce seeds
- two chickens, 10 grain and 4 grain already in the hopper
- the mill
- one field block
- three orders
