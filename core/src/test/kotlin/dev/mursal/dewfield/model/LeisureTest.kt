package dev.mursal.dewfield.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeisureTest {

    private fun Farm.keepsakeEvents(): List<FarmEvent.Keepsake> = events.filterIsInstance<FarmEvent.Keepsake>()

    // ------------------------------------------------------------------ fountain

    @Test
    fun `the first wish always comes true and costs a coin toss`() {
        val f = newFarm()
        f.state.tomorrowWeather = Weather.SUNNY
        val money = f.state.money
        val r = f.leisure.wish(Wish.RAIN)
        assertIs<Outcome.Ok>(r)
        assertFalse(Leisure.isMiss(r.msg))
        assertEquals(Weather.RAIN, f.state.tomorrowWeather)
        assertEquals(money - ItemDb.WISH_COST, f.state.money)
        val report = f.endDay()
        assertEquals(Weather.RAIN, report.weather)
        assertTrue(report.wishCameTrue)
    }

    @Test
    fun `wishes are capped per day and reset overnight`() {
        val f = newFarm()
        f.state.money = 10_000
        f.state.energy = 10
        repeat(ItemDb.WISHES_PER_DAY) { assertTrue(f.leisure.wish(Wish.GIFT).ok) }
        assertEquals(0, f.leisure.wishesLeft)
        val money = f.state.money
        val r = f.leisure.wish(Wish.GIFT)
        assertEquals("wish.no_more", (r as Outcome.Fail).msg.key)
        assertEquals(money, f.state.money, "a refused wish costs nothing")
        f.endDay()
        assertEquals(ItemDb.WISHES_PER_DAY, f.leisure.wishesLeft)
    }

    @Test
    fun `pointless wishes are refused without paying`() {
        val f = newFarm()
        f.state.tomorrowWeather = Weather.RAIN
        assertEquals("wish.rain_already", (f.leisure.wish(Wish.RAIN) as Outcome.Fail).msg.key)
        f.state.energy = Farm.MAX_ENERGY
        assertEquals("msg.not_hungry", (f.leisure.wish(Wish.ENERGY) as Outcome.Fail).msg.key)
        f.state.money = ItemDb.WISH_COST - 1
        assertEquals("msg.no_money", (f.leisure.wish(Wish.GIFT) as Outcome.Fail).msg.key)
        assertEquals(0, f.state.leisure.wishesMade)
    }

    @Test
    fun `over many days wishes come true about as often as their chance and never pay much`() {
        var granted = 0
        var made = 0
        var spent = 0
        var energy = 0
        for (seed in 1L..60L) {
            val f = newFarm(seed)
            f.state.money = 100_000
            repeat(10) {
                f.state.energy = 10
                repeat(ItemDb.WISHES_PER_DAY) {
                    val before = f.state.energy
                    val r = f.leisure.wish(Wish.ENERGY)
                    assertIs<Outcome.Ok>(r)
                    made++
                    spent += ItemDb.WISH_COST
                    if (!Leisure.isMiss(r.msg)) granted++
                    energy += f.state.energy - before
                }
                f.endDay()
            }
        }
        // Discount the guaranteed first wish of each farm.
        val rate = (granted - 60).toFloat() / (made - 60)
        assertTrue(rate in 0.38f..0.52f, "energy wishes came true at $rate")
        assertTrue(energy <= made * ItemDb.WISH_ENERGY)
    }

    @Test
    fun `a small gift only holds cheap seeds that are already unlocked`() {
        val f = newFarm()
        repeat(200) {
            val g = f.leisure.smallGift(kotlin.random.Random(it))
            val def = ItemDb.item(g.key)
            assertTrue(f.isUnlocked(g.key), "${g.key} is locked")
            if (def.category == Category.SEED) assertTrue(def.buyPrice <= ItemDb.GIFT_SEED_MAX_PRICE)
            assertTrue(g.amount in 1..ItemDb.GIFT_FEED)
        }
    }

    // ------------------------------------------------------------------ benches

    @Test
    fun `bench rest gives one energy per tick up to the daily cap`() {
        val f = newFarm()
        f.state.energy = 10
        repeat(ItemDb.REST_CAP) { assertTrue(f.leisure.restTick().ok) }
        assertEquals(10 + ItemDb.REST_CAP, f.state.energy)
        assertEquals("rest.done", (f.leisure.restTick() as Outcome.Fail).msg.key)
        f.endDay()
        f.state.energy = Farm.MAX_ENERGY
        assertEquals("rest.full", (f.leisure.restTick() as Outcome.Fail).msg.key)
        assertEquals(ItemDb.REST_CAP, f.leisure.restLeft)
    }

    @Test
    fun `resting is twice as quick at sunset`() {
        val f = newFarm()
        f.state.minutes = 12 * 60.0
        assertEquals(ItemDb.REST_MINUTES, f.leisure.restMinutesPerEnergy())
        f.state.minutes = 19 * 60.0
        assertTrue(f.leisure.isSunset())
        assertEquals(ItemDb.REST_MINUTES / 2, f.leisure.restMinutesPerEnergy())
    }

    // ------------------------------------------------------------------ sky

    @Test
    fun `the first shooting star is the star shard, later ones a gift once a night`() {
        val f = newFarm()
        f.state.minutes = 22 * 60.0
        assertTrue(f.leisure.starsTonight())
        assertTrue(f.leisure.catchStar().ok)
        assertTrue(f.leisure.has("star_shard"))
        assertEquals("star_shard", f.keepsakeEvents().single().id)
        assertEquals("star.again", (f.leisure.catchStar() as Outcome.Ok).msg?.key)
        f.endDay()
        val r = f.leisure.catchStar() as Outcome.Ok
        assertEquals("star.caught", r.msg?.key)
        assertEquals(1, r.gains.size)
        f.state.weather = Weather.RAIN
        assertFalse(f.leisure.starsTonight())
    }

    @Test
    fun `a rainbow shows the morning after rain and pays once`() {
        val f = newFarm()
        f.endDay(forcedWeather = Weather.RAIN)
        assertFalse(f.leisure.rainbowVisible())
        val report = f.endDay(forcedWeather = Weather.SUNNY)
        assertTrue(report.rainbow)
        assertTrue(f.leisure.rainbowVisible())
        val money = f.state.money
        assertTrue(f.leisure.takeRainbow().ok)
        assertTrue(f.state.money - money in ItemDb.RAINBOW_COINS_MIN..ItemDb.RAINBOW_COINS_MAX)
        assertFalse(f.leisure.rainbowVisible())
        assertFalse(f.leisure.takeRainbow().ok)
        f.state.minutes = Leisure.RAINBOW_UNTIL.toDouble()
        assertFalse(f.endDay(forcedWeather = Weather.SUNNY).rainbow)
    }

    // ------------------------------------------------------------------ keepsakes

    @Test
    fun `sparkles find every ground keepsake, then coins, one a day`() {
        val f = newFarm()
        val ground = ItemDb.keepsakesFrom(KeepsakeSource.GROUND)
        var days = 0
        while (ground.any { !f.leisure.has(it) }) {
            if (f.leisure.sparkleIndex(8) != null) {
                assertTrue(f.leisure.findSparkle().ok)
                assertNull(f.leisure.sparkleIndex(8), "a found sparkle is gone for the day")
                assertFalse(f.leisure.findSparkle().ok)
            }
            f.endDay()
            assertTrue(++days < 60, "ground keepsakes take too long to find")
        }
        while (f.leisure.sparkleIndex(8) == null) f.endDay()
        val money = f.state.money
        val r = f.leisure.findSparkle() as Outcome.Ok
        assertEquals("sparkle.coins", r.msg?.key)
        assertTrue(f.state.money - money in ItemDb.SPARKLE_COINS_MIN..ItemDb.SPARKLE_COINS_MAX)
    }

    @Test
    fun `the sparkle place stays the same all day`() {
        val f = newFarm(5)
        while (f.leisure.sparkleIndex(8) == null) f.endDay()
        val a = f.leisure.sparkleIndex(8)
        f.state.minutes = 15 * 60.0
        assertEquals(a, f.leisure.sparkleIndex(8))
    }

    @Test
    fun `clover harvests sometimes hide a four-leaf clover`() {
        var found = false
        for (seed in 1L..200L) {
            val f = newFarm(seed)
            val c = Cell(0, 0)
            f.add("clover_seeds", 1)
            f.till(c)
            f.plant(c, "clover_seeds")
            while (!f.isRipe(f.plot(c)!!)) {
                f.refillCan()
                f.water(c)
                f.endDay()
            }
            f.harvest(c)
            if (f.leisure.has("four_leaf")) {
                found = true
                break
            }
        }
        assertTrue(found)
    }

    @Test
    fun `a full shelf pays its reward exactly once`() {
        val f = newFarm()
        val money = f.state.money
        ItemDb.keepsakes.forEach { f.leisure.find(it.id) }
        val events = f.keepsakeEvents()
        assertEquals(ItemDb.keepsakes.size, events.size)
        assertTrue(events.last().shelfDone)
        assertTrue(events.dropLast(1).none { it.shelfDone })
        assertEquals(money + ItemDb.SHELF_REWARD_COINS, f.state.money)
        assertEquals(ItemDb.keepsakes.size * ItemDb.KEEPSAKE_XP + ItemDb.SHELF_REWARD_XP, f.state.xp)
        f.leisure.find("horseshoe")
        assertEquals(money + ItemDb.SHELF_REWARD_COINS, f.state.money)
    }

    // ------------------------------------------------------------------ daily gift

    @Test
    fun `the daily gift waits for the first night, then pays once per calendar day`() {
        val f = newFarm()
        val day = 20_000L
        assertFalse(DailyGift.canClaim(f.state, day))
        f.endDay()
        assertTrue(DailyGift.canClaim(f.state, day))
        val money = f.state.money
        val r = DailyGift.claim(f, day) as Outcome.Ok
        assertEquals(Gain("coins", 25), r.gains.single())
        assertEquals(money + 25, f.state.money)
        assertFalse(DailyGift.claim(f, day).ok)
        assertFalse(DailyGift.canClaim(f.state, day - 3), "turning the clock back pays nothing")
        repeat(5) { f.endDay() }
        assertFalse(DailyGift.canClaim(f.state, day), "game days don't count")
        assertTrue(DailyGift.claim(f, day + 4).ok, "missing days doesn't reset the calendar")
        assertEquals(2, DailyGift.nextIndex(f.state))
    }

    @Test
    fun `the seventh gift is the big one and the calendar starts over`() {
        val f = newFarm()
        f.endDay()
        for (i in 0 until 7) {
            assertTrue(DailyGift.claim(f, 100L + i).ok)
            assertFalse(DailyGift.canClaim(f.state, 101L + i), "a clock pushed ahead still needs a night's sleep")
            f.endDay()
        }
        assertEquals(0, DailyGift.nextIndex(f.state))
        assertEquals(1, f.count("pancakes"))
        val total = ItemDb.dailyGifts.flatten().filter { it.key == "coins" }.sumOf { it.amount }
        assertTrue(total <= 200, "the weekly coin gifts are $total")
    }

    @Test
    fun `calendar days follow the time zone`() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        val baku = java.util.TimeZone.getTimeZone("GMT+04:00")
        val lateEvening = 20_000L * 86_400_000L + 22 * 3_600_000L
        assertEquals(20_000L, DailyGift.today(lateEvening, utc))
        assertEquals(20_001L, DailyGift.today(lateEvening, baku))
    }

    @Test
    fun `old saves without leisure data load with an empty shelf`() {
        val s = assertNotNull(SaveCodec.decode("{\"version\":3,\"day\":9}", TEST_FIELD))
        assertEquals(0, s.leisure.keepsakes.size)
        assertNull(s.gift.lastDay)
        val damaged = SaveCodec.encode(newFarm().state).replace("\"keepsakes\":[]", "\"keepsakes\":[\"horseshoe\",\"ufo\",\"horseshoe\"]")
        assertEquals(listOf("horseshoe"), assertNotNull(SaveCodec.decode(damaged, TEST_FIELD)).leisure.keepsakes)
    }
}
