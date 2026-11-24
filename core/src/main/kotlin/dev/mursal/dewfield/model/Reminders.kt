package dev.mursal.dewfield.model

import java.util.TimeZone

/** Why the game would like to call the player back, in priority order. */
enum class ReminderKind { GIFT, FARM, COMEBACK }

/** A local notification to show at [atMillis], with a translatable title and text. */
data class Reminder(val kind: ReminderKind, val atMillis: Long, val title: Msg, val text: Msg)

/**
 * Plans the local notifications the game leaves behind when it goes to the background.
 *
 * The farm doesn't change while the app is closed, so a reminder only says what is already true:
 * ripe crops or full baskets waiting, tomorrow's daily gift, and one gentle nudge after a few days
 * away. None fires at night, and no two land within [MIN_GAP_HOURS] of each other: the more
 * important one (gift, then farm, then comeback) wins.
 */
object Reminders {
    /** Quiet hours, as minutes of the local day: nothing from 21:00 until 09:00. */
    const val QUIET_FROM = 21 * 60
    const val QUIET_UNTIL = 9 * 60

    /** Reminders pushed out of the quiet hours arrive at 09:30. */
    const val MORNING = QUIET_UNTIL + 30
    const val FARM_DELAY_HOURS = 4
    const val GIFT_UNCLAIMED_HOURS = 3
    const val GIFT_AT = 10 * 60
    const val COMEBACK_DAYS = 3
    const val COMEBACK_AT = 18 * 60
    const val MIN_GAP_HOURS = 6

    fun plan(farm: Farm, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): List<Reminder> {
        val clock = LocalClock(zone)
        val today = clock.day(nowMillis)
        val candidates = ArrayList<Reminder>()

        if (farm.state.stats.daysPlayed >= 1) {
            val claimable = DailyGift.canClaim(farm.state, today)
            val at = if (claimable) awake(clock, nowMillis + GIFT_UNCLAIMED_HOURS * LocalClock.HOUR) else clock.at(today + 1, GIFT_AT)
            val day = DailyGift.nextIndex(farm.state) + 1
            candidates += Reminder(ReminderKind.GIFT, at, Msg("notify.gift.title"), Msg("notify.gift.text", day))
        }
        farmNews(farm)?.let { (title, text) ->
            candidates += Reminder(ReminderKind.FARM, awake(clock, nowMillis + FARM_DELAY_HOURS * LocalClock.HOUR), title, text)
        }
        val friend = farm.state.animals.firstOrNull { !it.arriving }?.name
        candidates += Reminder(
            ReminderKind.COMEBACK,
            clock.at(today + COMEBACK_DAYS, COMEBACK_AT),
            Msg("notify.miss.title"),
            if (friend != null) Msg("notify.miss.animals", friend) else Msg("notify.miss.field"),
        )

        val kept = ArrayList<Reminder>()
        for (r in candidates.sortedBy { it.kind.ordinal }) {
            if (r.atMillis <= nowMillis) continue
            if (kept.none { kotlin.math.abs(it.atMillis - r.atMillis) < MIN_GAP_HOURS * LocalClock.HOUR }) kept += r
        }
        return kept.sortedBy { it.atMillis }
    }

    /** The most useful thing waiting on the farm, as a title and a text, or null if nothing is. */
    internal fun farmNews(farm: Farm): Pair<Msg, Msg>? {
        val s = farm.state
        val ripe = s.plots.count { farm.isRipe(it) }
        if (ripe > 0) return Msg("notify.ripe.title") to Msg("notify.ripe.text", ripe)
        val products = s.baskets.values.sumOf { it.values.sum() }
        if (products > 0) return Msg("notify.basket.title") to Msg("notify.basket.text", products)
        val goods = s.machines.values.sumOf { it.ready.values.sum() }
        if (goods > 0) return Msg("notify.machine.title") to Msg("notify.machine.text", goods)
        val hungry = ItemDb.troughs.values.any { t ->
            val need = s.animals.filter { !it.arriving && ItemDb.animal(it.kind).feed == t.feed }.sumOf { ItemDb.animal(it.kind).feedPerDay }
            need > 0 && farm.trough(t.id) < need
        }
        if (hungry) return Msg("notify.hungry.title") to Msg("notify.hungry.text")
        return null
    }

    /** Moves a time out of the quiet hours to the next morning. */
    private fun awake(clock: LocalClock, t: Long): Long {
        val m = clock.minuteOfDay(t)
        return when {
            m >= QUIET_FROM -> clock.at(clock.day(t) + 1, MORNING)
            m < QUIET_UNTIL -> clock.at(clock.day(t), MORNING)
            else -> t
        }
    }
}
