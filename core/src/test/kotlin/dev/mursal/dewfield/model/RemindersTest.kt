package dev.mursal.dewfield.model

import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemindersTest {
    private val zone = TimeZone.getTimeZone("UTC")
    private val day = 20_000L
    private fun at(hour: Int, minute: Int = 0, plusDays: Int = 0): Long = (day + plusDays) * 86_400_000L + (hour * 60L + minute) * 60_000L
    private fun hourOf(t: Long) = (Math.floorMod(t, 86_400_000L) / 3_600_000L).toInt()

    private fun ripeFarm(): Farm {
        val f = newFarm()
        val c = Cell(0, 0)
        f.till(c)
        f.plant(c, "radish_seeds")
        while (!f.isRipe(f.plot(c)!!)) {
            f.refillCan()
            f.water(c)
            f.endDay()
        }
        return f
    }

    @Test
    fun `a new farm that never slept only gets the comeback nudge`() {
        val f = newFarm()
        val plan = Reminders.plan(f, at(12), zone)
        assertEquals(listOf(ReminderKind.COMEBACK), plan.map { it.kind })
        assertEquals(at(Reminders.COMEBACK_AT / 60, plusDays = Reminders.COMEBACK_DAYS), plan.single().atMillis)
        assertEquals("notify.miss.animals", plan.single().text.key)
    }

    @Test
    fun `ripe crops, tomorrow's gift and the comeback are spread out`() {
        val f = ripeFarm()
        DailyGift.claim(f, day)
        val plan = Reminders.plan(f, at(11), zone)
        assertEquals(listOf(ReminderKind.FARM, ReminderKind.GIFT, ReminderKind.COMEBACK), plan.map { it.kind })
        assertEquals(at(15), plan[0].atMillis)
        assertEquals("notify.ripe.text", plan[0].text.key)
        assertEquals(at(Reminders.GIFT_AT / 60, plusDays = 1), plan[1].atMillis)
    }

    @Test
    fun `nothing fires at night and the gift wins a clash`() {
        val f = ripeFarm()
        DailyGift.claim(f, day)
        // Leaving at 20:00: the farm reminder would land at midnight, moves to 09:30 and then clashes
        // with tomorrow's 10:00 gift reminder, which is kept.
        val plan = Reminders.plan(f, at(20), zone)
        assertEquals(listOf(ReminderKind.GIFT, ReminderKind.COMEBACK), plan.map { it.kind })
        for (r in Reminders.plan(f, at(23, 30), zone) + Reminders.plan(f, at(3), zone)) {
            val h = hourOf(r.atMillis)
            assertTrue(h >= Reminders.QUIET_UNTIL / 60 && h < Reminders.QUIET_FROM / 60, "${r.kind} at $h:00")
        }
    }

    @Test
    fun `an unclaimed gift is mentioned the same day`() {
        val f = newFarm()
        f.endDay()
        val plan = Reminders.plan(f, at(10), zone)
        val gift = plan.first { it.kind == ReminderKind.GIFT }
        assertEquals(at(13), gift.atMillis)
        assertEquals(listOf(1), gift.text.args)
    }

    @Test
    fun `farm news picks the most useful thing waiting`() {
        val f = newFarm()
        f.basket(Housing.COOP)["egg"] = 2
        assertEquals("notify.basket.text", Reminders.farmNews(f)?.second?.key)
        f.basket(Housing.COOP).clear()
        f.state.troughs["hopper"] = 0
        assertEquals("notify.hungry.text", Reminders.farmNews(f)?.second?.key)
        f.state.troughs["hopper"] = 30
        assertEquals(null, Reminders.farmNews(f))
    }
}
