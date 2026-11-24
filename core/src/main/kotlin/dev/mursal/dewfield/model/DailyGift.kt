package dev.mursal.dewfield.model

import java.util.TimeZone

/**
 * The daily gift: one present for each calendar day the player opens the game, following the
 * seven-day calendar in [ItemDb.dailyGifts]. It counts real days, not game days, so sleeping ten
 * nights in a row doesn't pay ten gifts. Missing a day never resets the calendar; it just waits.
 */
object DailyGift {
    /** The calendar day number of [nowMillis] in [zone], counted from 1970-01-01. */
    fun today(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long = LocalClock(zone).day(nowMillis)

    /**
     * True when a gift is waiting: the farmer has slept at least once (the first minutes of a farm are
     * for learning to farm), nothing was claimed today, and the farmer has slept since the last gift.
     * A clock turned back never pays twice, and one pushed ahead pays at most one gift per game day.
     */
    fun canClaim(state: GameState, today: Long): Boolean =
        state.stats.daysPlayed >= 1 && state.day > state.gift.lastGameDay && state.gift.lastDay.let { it == null || today > it }

    /** Calendar position (0..6) of the next gift. */
    fun nextIndex(state: GameState): Int = state.gift.claimed % ItemDb.dailyGifts.size

    fun claim(farm: Farm, today: Long): Outcome {
        val s = farm.state
        if (!canClaim(s, today)) return Outcome.Fail(Msg("gift.come_back"))
        val index = nextIndex(s)
        s.gift.lastDay = today
        s.gift.lastGameDay = s.day
        s.gift.claimed++
        val gains = ItemDb.dailyGifts[index].map { farm.grant(it) }
        return Outcome.Ok(Msg("gift.claimed", index + 1), gains)
    }
}

/** Calendar days and minutes of the day in a time zone, without java.time (Android 7 lacks it). */
internal class LocalClock(private val zone: TimeZone) {
    fun day(t: Long): Long = Math.floorDiv(t + zone.getOffset(t), DAY)

    fun minuteOfDay(t: Long): Int = (Math.floorMod(t + zone.getOffset(t), DAY) / MINUTE).toInt()

    /** The instant of local [minute] on local [day]. The second pass settles daylight-saving changes. */
    fun at(day: Long, minute: Int): Long {
        val local = day * DAY + minute * MINUTE
        val guess = local - zone.getOffset(local)
        return local - zone.getOffset(guess)
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
