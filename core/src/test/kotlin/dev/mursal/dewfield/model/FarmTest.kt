package dev.mursal.dewfield.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A small two-block field: block 0 is x 0..3, block 1 is x 10..13, rows 0..1. */
val TEST_FIELD = FieldLayout(
    listOf(
        (0..3).flatMap { x -> (0..1).map { y -> Cell(x, y) } },
        (10..13).flatMap { x -> (0..1).map { y -> Cell(x, y) } },
    ),
)

fun newFarm(seed: Long = 42L): Farm = Farm.newGame(seed, TEST_FIELD)

private val A = Cell(0, 0)
private val B = Cell(1, 0)

class FarmTest {

    private fun Farm.sleep(weather: Weather = Weather.SUNNY) = endDay(forcedWeather = weather)

    private fun Farm.plantWatered(cell: Cell, seed: String) {
        add(seed, 1)
        assertTrue(till(cell).ok)
        assertTrue(plant(cell, seed).ok)
        assertTrue(water(cell).ok)
    }

    private fun Farm.growToRipe(cell: Cell) {
        while (!isRipe(plot(cell)!!)) {
            if (!plot(cell)!!.watered) {
                refillCan()
                assertTrue(water(cell).ok)
            }
            sleep()
        }
    }

    @Test
    fun `new game starts with seeds, chickens, the mill and three orders`() {
        val f = newFarm()
        assertEquals(Farm.START_MONEY, f.state.money)
        assertEquals(6, f.count("radish_seeds"))
        assertEquals(2, f.animalsIn(Housing.COOP).size)
        assertTrue(f.owns("mill"))
        assertEquals(Farm.MAX_ORDERS, f.state.orders.size)
        assertEquals(16, f.state.plots.size)
        assertEquals(20, f.state.canWater)
        assertEquals(1, f.level)
    }

    @Test
    fun `till, plant, water and grow a radish over three watered nights`() {
        val f = newFarm()
        assertTrue(f.till(A).ok)
        assertEquals(Farm.MAX_ENERGY - Farm.COST_TILL, f.state.energy)
        assertTrue(f.plant(A, "radish_seeds").ok)
        assertEquals(5, f.count("radish_seeds"))
        repeat(3) { day ->
            assertFalse(f.isRipe(f.plot(A)!!), "not ripe before night $day")
            assertTrue(f.water(A).ok)
            f.sleep()
        }
        assertTrue(f.isRipe(f.plot(A)!!))
        val out = f.harvest(A)
        assertTrue(out is Outcome.Ok)
        // Watered every night, not fertilized: silver.
        assertEquals(1, f.count("radish#1"))
        assertNull(f.plot(A)!!.crop)
        assertTrue(f.plot(A)!!.tilled, "soil stays tilled after harvest")
    }

    @Test
    fun `unwatered crops do not grow and expensive crops wither sooner`() {
        val f = newFarm()
        f.add("pumpkin_seeds", 1)
        f.plantWatered(A, "radish_seeds")
        f.plantWatered(B, "pumpkin_seeds")
        f.sleep() // both grow one night
        f.sleep() // dry 1
        assertEquals(1, f.plot(A)!!.growth)
        assertFalse(f.plot(B)!!.dead)
        f.sleep() // dry 2: pumpkin (wither 2) dies, radish (wither 4) survives
        assertTrue(f.plot(B)!!.dead)
        assertFalse(f.plot(A)!!.dead)
        assertTrue(f.plot(A)!!.missedWater)
        assertTrue(f.clear(B).ok)
        assertNull(f.plot(B)!!.crop)
    }

    @Test
    fun `quality is normal when a night was missed and gold with fertilizer and daily water`() {
        val f = newFarm()
        // Normal: skip a night.
        f.plantWatered(A, "radish_seeds")
        f.sleep()
        f.sleep() // dry night
        f.growToRipe(A)
        f.harvest(A)
        assertEquals(1, f.count("radish"))

        // Gold: fertilizer + water every night.
        f.add(ItemDb.FERTILIZER, 1)
        f.add("radish_seeds", 1)
        assertTrue(f.till(B).ok)
        assertTrue(f.fertilize(B).ok)
        assertTrue(f.plant(B, "radish_seeds").ok)
        assertTrue(f.water(B).ok)
        f.growToRipe(B)
        f.harvest(B)
        assertEquals(1, f.count("radish#2"))
        assertFalse(f.plot(B)!!.fertilized, "fertilizer is used up by a one-time crop")
    }

    @Test
    fun `regrowing tomato gives two per harvest and keeps its fertilizer`() {
        val f = newFarm()
        f.state.xp = ItemDb.levelXp[1] // level 2 for tomatoes
        f.add(ItemDb.FERTILIZER, 1)
        f.add("tomato_seeds", 1)
        f.till(A)
        f.fertilize(A)
        f.plant(A, "tomato_seeds")
        f.water(A)
        f.growToRipe(A)
        assertEquals(7, f.state.day - 1)
        f.harvest(A)
        assertEquals(2, f.count("tomato#2"))
        val p = f.plot(A)!!
        assertEquals("tomato", p.crop)
        assertTrue(p.fertilized)
        assertEquals(3, f.daysLeft(p))
        f.growToRipe(A)
        f.harvest(A)
        assertEquals(4, f.countAll("tomato"))
    }

    @Test
    fun `rain waters every tilled plot in the morning`() {
        val f = newFarm()
        f.till(A)
        f.till(B)
        f.sleep(Weather.RAIN)
        assertTrue(f.plot(A)!!.watered)
        assertTrue(f.plot(B)!!.watered)
        assertEquals(Weather.RAIN, f.state.weather)
    }

    @Test
    fun `locked field blocks cannot be used until the field upgrade`() {
        val f = newFarm()
        val locked = Cell(10, 0)
        assertEquals(Msg("msg.field_locked"), (f.till(locked) as Outcome.Fail).msg)
        f.state.xp = ItemDb.levelXp[1]
        f.state.money = 10_000
        assertTrue(f.buyUpgrade(ItemDb.UPGRADE_FIELD).ok)
        assertTrue(f.till(locked).ok)
    }

    @Test
    fun `watering uses the can and the well refills it`() {
        val f = newFarm()
        f.state.canWater = 1
        f.till(A)
        f.till(B)
        assertTrue(f.water(A).ok)
        assertEquals(Msg("msg.can_empty"), (f.water(B) as Outcome.Fail).msg)
        assertTrue(f.refillCan().ok)
        assertEquals(20, f.state.canWater)
        assertFalse(f.refillCan().ok)
    }

    @Test
    fun `energy runs out and food restores it`() {
        val f = newFarm()
        f.state.energy = 2
        assertEquals(Msg("msg.too_tired"), (f.till(A) as Outcome.Fail).msg)
        f.add("carrot", 1)
        assertTrue(f.eat("carrot").ok)
        assertEquals(14, f.state.energy)
        f.add("grain", 1)
        assertEquals(Msg("msg.not_edible"), (f.eat("grain") as Outcome.Fail).msg)
        f.state.energy = Farm.MAX_ENERGY
        f.add("carrot", 1)
        assertFalse(f.eat("carrot").ok)
    }

    @Test
    fun `cooking turns ingredients into a big energy meal`() {
        val f = newFarm()
        assertFalse(f.canCook("pancakes"))
        f.add("egg", 1)
        f.add("milk#2", 1)
        f.add("wheat", 1)
        assertTrue(f.cook("pancakes").ok)
        assertEquals(0, f.countAll("egg"))
        assertEquals(0, f.countAll("milk"))
        f.state.energy = 10
        assertTrue(f.eat("pancakes").ok)
        assertEquals(85, f.state.energy)
    }

    @Test
    fun `shops sell only unlocked items and charge money`() {
        val f = newFarm()
        assertTrue(f.buy("carrot_seeds", 5).ok)
        assertEquals(Farm.START_MONEY - 160, f.state.money)
        assertEquals(Msg("msg.needs_level", 5), (f.buy("pumpkin_seeds", 1) as Outcome.Fail).msg)
        f.state.money = 5
        assertEquals(Msg("msg.no_money"), (f.buy("radish_seeds", 1) as Outcome.Fail).msg)
    }

    @Test
    fun `selling pays by quality and each unit sold lowers today's price`() {
        val f = newFarm()
        f.state.prices.values.forEach { it.mult = 1f; it.prevMult = 1f }
        f.state.hotItem = null
        assertEquals(80, f.priceOf("carrot"))
        assertEquals(100, f.priceOf("carrot#1"))
        assertEquals(120, f.priceOf("carrot#2"))
        f.add("carrot", 10)
        val quote = f.quote("carrot", 10)
        assertTrue(quote < 800, "saturation lowers the total ($quote)")
        val before = f.state.money
        assertTrue(f.sell("carrot", 10).ok)
        assertEquals(before + quote, f.state.money)
        assertTrue(f.priceOf("carrot") < 80)
        f.sleep()
        assertTrue(f.state.soldToday.isEmpty(), "saturation resets overnight")
    }

    @Test
    fun `hot item pays extra and prices drift within bounds`() {
        val f = newFarm()
        f.state.prices.getValue("lettuce").mult = 1f
        f.state.hotItem = "lettuce"
        assertEquals(55, f.priceOf("lettuce"))
        repeat(60) { f.sleep() }
        f.state.prices.values.forEach { assertTrue(it.mult in Farm.PRICE_MIN..Farm.PRICE_MAX) }
        assertTrue(f.state.prices.values.map { it.mult }.distinct().size > 5, "prices actually move")
    }

    @Test
    fun `each animal eats its own feed from its own trough`() {
        val f = newFarm()
        f.state.xp = ItemDb.levelXp[2] // level 3: cows and sheep
        f.state.money = 10_000
        assertTrue(f.buyAnimal("cow").ok)
        assertTrue(f.buyAnimal("sheep").ok)
        f.sleep() // they arrive; chickens eat 2 grain from the hopper (had 4)
        assertEquals(2, f.trough("hopper"))
        val cow = f.state.animals.first { it.kind == "cow" }
        val sheep = f.state.animals.first { it.kind == "sheep" }
        assertFalse(cow.arriving)

        // Hay does not feed sheep; grain does not feed cows.
        f.add("hay", 5)
        assertTrue(f.fillTrough("hay_rack").ok)
        assertEquals(5, f.trough("hay_rack"))
        assertEquals(Msg("msg.no_feed", Msg("feed.clover")), (f.fillTrough("clover_tray") as Outcome.Fail).msg)
        f.sleep()
        assertTrue(cow.fedLastNight)
        assertFalse(sheep.fedLastNight)
        assertEquals(3, f.trough("hay_rack"), "a cow eats two hay")
        assertEquals(1, f.basket(Housing.BARN).values.sum())

        // Sheep accept the clover crop and the bought mix.
        f.add("clover", 2)
        f.add("clover_mix", 3)
        assertTrue(f.fillTrough("clover_tray").ok)
        assertEquals(5, f.trough("clover_tray"))
        assertEquals(0, f.countAll("clover"))
    }

    @Test
    fun `fed and petted animals get happier and give better products`() {
        val f = newFarm()
        val hen = f.state.animals.first()
        f.add("grain", 20)
        f.fillTrough("hopper")
        assertTrue(f.pet(hen.id).ok)
        assertFalse(f.pet(hen.id).ok, "once a day")
        f.sleep() // mood 5 +1 pet +1 fed = 7 -> silver egg
        assertEquals(7, hen.mood)
        val basket = f.basket(Housing.COOP)
        assertTrue(basket.containsKey("egg#1"))
        f.pet(hen.id)
        f.sleep() // 9 -> gold
        assertTrue(basket.containsKey("egg#2"))
        val out = f.collectBasket(Housing.COOP)
        assertTrue(out.ok)
        assertEquals(4, f.countAll("egg"))
        assertTrue(basket.isEmpty())
    }

    @Test
    fun `hungry animals lose mood and make nothing`() {
        val f = newFarm()
        f.state.troughs["hopper"] = 0
        val report = f.sleep()
        assertEquals(2, report.hungry.size)
        assertTrue(f.basket(Housing.COOP).isEmpty())
        assertEquals(3, f.state.animals.first().mood)
    }

    @Test
    fun `sheep give wool every second fed day`() {
        val f = newFarm()
        f.state.xp = ItemDb.levelXp[2]
        f.state.money = 10_000
        f.buyAnimal("sheep")
        f.sleep()
        f.add("clover_mix", 10)
        f.fillTrough("clover_tray")
        f.sleep()
        assertEquals(0, f.basket(Housing.BARN).values.sum())
        f.sleep()
        assertEquals(1, f.basket(Housing.BARN).values.sum())
    }

    @Test
    fun `animal housing has a capacity that upgrades raise`() {
        val f = newFarm()
        f.state.money = 100_000
        f.buyAnimal("chicken")
        f.buyAnimal("chicken")
        assertEquals(Msg("msg.coop_full"), (f.buyAnimal("chicken") as Outcome.Fail).msg)
        f.state.xp = ItemDb.levelXp[2]
        assertTrue(f.buyUpgrade(ItemDb.UPGRADE_COOP).ok)
        assertTrue(f.buyAnimal("chicken").ok)
        assertEquals(Msg("msg.needs_level", 2), (newFarm().buyAnimal("cow") as Outcome.Fail).msg)
    }

    @Test
    fun `the mill turns wheat into hay and corn into grain overnight`() {
        val f = newFarm()
        f.add("wheat", 3)
        f.add("corn#2", 1)
        assertTrue(f.loadMachine("mill", "mill_wheat", 2).ok)
        assertTrue(f.loadMachine("mill", "mill_corn", 5).ok, "loads as many as you have")
        assertEquals(1, f.countAll("wheat"))
        assertEquals(0, f.countAll("corn"))
        assertFalse(f.collectMachine("mill").ok, "nothing ready before the night")
        f.sleep()
        assertTrue(f.collectMachine("mill").ok)
        assertEquals(10, f.count("hay"))
        assertEquals(10 + 10, f.count("grain"))
    }

    @Test
    fun `cheese keeps the milk's quality and machines must be bought first`() {
        val f = newFarm()
        f.add("milk#2", 2)
        assertEquals(Msg("msg.not_owned"), (f.loadMachine("cheese_press", "press_milk", 1) as Outcome.Fail).msg)
        f.state.money = 5_000
        assertEquals(Msg("msg.needs_level", 3), (f.buyMachine("cheese_press") as Outcome.Fail).msg)
        f.state.xp = ItemDb.levelXp[2]
        assertTrue(f.buyMachine("cheese_press").ok)
        assertTrue(f.loadMachine("cheese_press", "press_milk", 2).ok)
        f.sleep()
        f.collectMachine("cheese_press")
        assertEquals(2, f.count("cheese#2"))
        assertFalse(f.loadMachine("cheese_press", "mill_wheat", 1).ok, "wrong machine for the recipe")
    }

    @Test
    fun `machines have limited slots`() {
        val f = newFarm()
        f.add("wheat", 20)
        assertTrue(f.loadMachine("mill", "mill_wheat", 20).ok)
        assertEquals(10, f.machine("mill")!!.queue.size)
        assertEquals(Msg("msg.machine_full"), (f.loadMachine("mill", "mill_wheat", 1) as Outcome.Fail).msg)
    }

    @Test
    fun `upgrades cost money, need levels and stop at max`() {
        val f = newFarm()
        f.state.money = 100_000
        assertEquals(Msg("msg.needs_level", 2), (f.buyUpgrade(ItemDb.UPGRADE_CAN) as Outcome.Fail).msg)
        f.state.xp = ItemDb.levelXp.last()
        assertTrue(f.buyUpgrade(ItemDb.UPGRADE_CAN).ok)
        assertEquals(40, f.state.canWater)
        assertTrue(f.buyUpgrade(ItemDb.UPGRADE_CAN).ok)
        assertEquals(80, f.canCapacity)
        assertEquals(Msg("msg.max_level"), (f.buyUpgrade(ItemDb.UPGRADE_CAN) as Outcome.Fail).msg)
    }

    @Test
    fun `orders can be delivered for a bonus and expire otherwise`() {
        val f = newFarm()
        val order = f.state.orders.first()
        assertFalse(f.canDeliver(order))
        order.lines.forEach { f.add(it.item, it.qty) }
        val value = order.lines.sumOf { ItemDb.item(it.item).sellPrice * it.qty }
        assertTrue(order.reward > value, "orders pay more than the base price")
        val before = f.state.money
        assertTrue(f.deliver(order.id).ok)
        assertEquals(before + order.reward, f.state.money)
        assertEquals(2, f.state.orders.size)
        f.sleep()
        assertEquals(Farm.MAX_ORDERS, f.state.orders.size, "the board refills each morning")

        val g = newFarm(7)
        repeat(6) { g.sleep() }
        assertTrue(g.state.orders.all { it.daysLeft > 0 })
    }

    @Test
    fun `orders only ask for things the player can make`() {
        val f = newFarm()
        repeat(30) { f.sleep() }
        val allowed = f.orderCandidates().toSet()
        assertTrue("pumpkin" !in allowed && "wool" !in allowed && "cheese" !in allowed)
        f.state.orders.forEach { o -> o.lines.forEach { assertTrue(it.item in allowed, it.item) } }
    }

    @Test
    fun `xp raises the level and reports what unlocked`() {
        val f = newFarm()
        f.addXp(ItemDb.levelXp[1])
        assertEquals(2, f.level)
        val ev = f.events.removeFirst() as FarmEvent.LevelUp
        assertEquals(2, ev.level)
        assertTrue("item.potato_seeds" in ev.unlocks)
        assertTrue("animal.cow" in ev.unlocks)
        f.addXp(10_000)
        assertEquals(ItemDb.levelXp.size, f.level)
        assertNull(f.levelProgress())
    }

    @Test
    fun `the clock collapses the farmer at midnight once`() {
        val f = newFarm()
        f.advanceTime((Farm.DAY_END - Farm.DAY_START) * Farm.SECONDS_PER_GAME_MINUTE + 5)
        assertEquals(Farm.DAY_END.toDouble(), f.state.minutes)
        assertEquals(FarmEvent.Exhausted, f.events.removeFirst())
        f.advanceTime(10.0)
        assertTrue(f.events.isEmpty())
        f.endDay(passedOut = true)
        assertEquals(Farm.PASS_OUT_ENERGY, f.state.energy)
        assertEquals(Farm.DAY_START.toDouble(), f.state.minutes)
    }

    @Test
    fun `market hours`() {
        val f = newFarm()
        f.state.minutes = 6.5 * 60
        assertFalse(f.isMarketOpen)
        f.state.minutes = 12.0 * 60
        assertTrue(f.isMarketOpen)
        f.state.minutes = 20.0 * 60
        assertFalse(f.isMarketOpen)
    }

    @Test
    fun `weather and hot items are deterministic per seed`() {
        val a = newFarm(99)
        val b = newFarm(99)
        repeat(10) {
            a.endDay()
            b.endDay()
            assertEquals(a.state.weather, b.state.weather)
            assertEquals(a.state.hotItem, b.state.hotItem)
        }
        val weathers = (1..40).map { newFarm(it.toLong()).also { f -> f.endDay() }.state.weather }.toSet()
        assertTrue(weathers.size == 3, "all weather kinds happen: $weathers")
    }

    @Test
    fun `morning report summarizes the night`() {
        val f = newFarm()
        f.plantWatered(A, "radish_seeds")
        f.add("grain", 10)
        f.fillTrough("hopper")
        val r = f.sleep()
        assertEquals(2, r.day)
        assertEquals(1, r.cropsGrew)
        assertEquals(2, r.products["egg"])
        assertTrue(r.hungry.isEmpty())
    }

    @Test
    fun `inventory removes lowest quality first`() {
        val f = newFarm()
        f.add("egg#2", 1)
        f.add("egg", 1)
        assertEquals(listOf(0, 2), f.removeAnyQuality("egg", 2))
        assertNull(f.removeAnyQuality("egg", 1))
        assertEquals("egg", ItemKey.id("egg#2"))
        assertEquals(2, ItemKey.quality("egg#2"))
        assertEquals(0, ItemKey.quality("egg"))
    }

    @Test
    fun `every item, crop and animal id is consistent`() {
        ItemDb.crops.values.forEach {
            assertNotNull(ItemDb.items[it.seed], it.seed)
            assertNotNull(ItemDb.items[it.produce], it.produce)
        }
        ItemDb.animals.values.forEach {
            assertNotNull(ItemDb.items[it.product])
            assertNotNull(ItemDb.troughByFeed[it.feed])
        }
        ItemDb.recipes.values.forEach {
            assertNotNull(ItemDb.items[it.input])
            assertNotNull(ItemDb.items[it.output])
            assertNotNull(ItemDb.machines[it.machine])
        }
        ItemDb.meals.values.forEach { m -> m.ingredients.forEach { assertNotNull(ItemDb.items[it]) } }
        ItemDb.shopSeeds.forEach { assertTrue(ItemDb.item(it).buyPrice > 0) }
    }

    @Test
    fun `expensive seeds earn more per day than cheap ones`() {
        fun profitPerDay(crop: String): Float {
            val c = ItemDb.crop(crop)
            val seed = ItemDb.item(c.seed).buyPrice
            val sell = ItemDb.item(c.produce).sellPrice * (c.yieldMin + c.yieldMax) / 2f
            if (c.regrowDays == 0) return (sell - seed) / c.growDays
            val days = 28
            val harvests = 1 + (days - c.growDays) / c.regrowDays
            return (sell * harvests - seed) / days
        }
        assertTrue(profitPerDay("radish") < profitPerDay("carrot"))
        assertTrue(profitPerDay("carrot") < profitPerDay("tomato"))
        assertTrue(profitPerDay("tomato") < profitPerDay("strawberry"))
        assertTrue(profitPerDay("strawberry") < profitPerDay("pumpkin"))
    }
}
