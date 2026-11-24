package dev.mursal.dewfield.model

import kotlin.random.Random

/**
 * Small pleasures around the farm: wishes at the market fountain, rest on a bench, shooting stars,
 * the rainbow after rain, sparkles on the ground and the keepsakes they hide.
 *
 * Every reward is small and capped per day (see the pastimes block in [ItemDb]), so none of this
 * earns more than farming or skips a step of the progression. Random results come from the farm's
 * seeded generator, so they are the same on every replay of a day.
 */
class Leisure internal constructor(private val farm: Farm) {
    private val state: GameState get() = farm.state
    private val s: LeisureState get() = farm.state.leisure

    // ------------------------------------------------------------------ the fountain

    val wishesLeft: Int get() = (ItemDb.WISHES_PER_DAY - s.wishesToday).coerceAtLeast(0)

    /** Why a wish can't be made right now, or null if it can. Nothing is paid for a blocked wish. */
    fun wishBlocker(kind: Wish): Msg? = when {
        s.wishesToday >= ItemDb.WISHES_PER_DAY -> Msg("wish.no_more")
        kind == Wish.RAIN && state.tomorrowWeather == Weather.RAIN -> Msg("wish.rain_already")
        kind == Wish.ENERGY && state.energy >= Farm.MAX_ENERGY -> Msg("msg.not_hungry")
        state.money < ItemDb.WISH_COST -> Msg("msg.no_money")
        else -> null
    }

    /**
     * Tosses a coin and makes a wish. It comes true with the chance in [ItemDb.wishChance] (always,
     * the first time on a farm). A granted rain wish changes tomorrow's forecast; energy and gift
     * wishes pay out at once. Use [isMiss] to tell a wish that didn't come true.
     */
    fun wish(kind: Wish): Outcome {
        wishBlocker(kind)?.let { return Outcome.Fail(it) }
        farm.pay(ItemDb.WISH_COST)
        val rng = farm.rng("wish${s.wishesMade}")
        val first = s.wishesMade == 0
        s.wishesToday++
        s.wishesMade++
        if (rng.nextFloat() < ItemDb.FOUNTAIN_KEEPSAKE_CHANCE) findFirstMissing(KeepsakeSource.FOUNTAIN)
        val granted = first || rng.nextFloat() < (ItemDb.wishChance[kind] ?: 0f)
        if (!granted) return Outcome.Ok(Msg("$MISS${rng.nextInt(MISS_LINES)}"))
        return when (kind) {
            Wish.RAIN -> {
                state.tomorrowWeather = Weather.RAIN
                s.wishedRain = true
                Outcome.Ok(Msg("wish.rain_granted"))
            }
            Wish.ENERGY -> Outcome.Ok(Msg("wish.energy_granted"), listOf(farm.grant(Gain("energy", ItemDb.WISH_ENERGY))))
            Wish.GIFT -> Outcome.Ok(Msg("wish.gift_granted"), listOf(farm.grant(smallGift(rng))))
        }
    }

    /**
     * A small present: two cheap seeds the farmer can already buy, some fertilizer, or a little feed
     * for animals the farm keeps.
     */
    internal fun smallGift(rng: Random): Gain {
        val seeds = ItemDb.shopSeeds.filter {
            ItemDb.cropBySeed.containsKey(it) && farm.isUnlocked(it) && ItemDb.item(it).buyPrice <= ItemDb.GIFT_SEED_MAX_PRICE
        }
        val feeds = state.animals.map { ItemDb.animal(it.kind).feed }.distinct()
            .mapNotNull { feed -> ItemDb.feedAliases[feed]?.firstOrNull { it in ItemDb.shopFeed } }
        // Feed only for animals the farm keeps: without any, the feed slot becomes fertilizer.
        return when (rng.nextInt(3)) {
            0 -> Gain(seeds[rng.nextInt(seeds.size)], ItemDb.GIFT_SEEDS)
            1 -> Gain(ItemDb.FERTILIZER, ItemDb.GIFT_FERTILIZER)
            else -> if (feeds.isEmpty()) {
                Gain(ItemDb.FERTILIZER, ItemDb.GIFT_FERTILIZER)
            } else {
                Gain(feeds[rng.nextInt(feeds.size)], ItemDb.GIFT_FEED)
            }
        }
    }

    // ------------------------------------------------------------------ benches

    val restLeft: Int get() = (ItemDb.REST_CAP - s.restedToday).coerceAtLeast(0)

    fun isSunset(): Boolean = state.minutes >= ItemDb.SUNSET_START && state.minutes < ItemDb.SUNSET_END

    /** Game minutes of sitting per point of energy right now: half as many at sunset, when the view is best. */
    fun restMinutesPerEnergy(): Double = if (isSunset()) ItemDb.REST_MINUTES / 2 else ItemDb.REST_MINUTES

    /** One point of rest on a bench. Fails once today's rest is used up or energy is already full. */
    fun restTick(): Outcome {
        if (s.restedToday >= ItemDb.REST_CAP) return Outcome.Fail(Msg("rest.done"))
        if (state.energy >= Farm.MAX_ENERGY) return Outcome.Fail(Msg("rest.full"))
        s.restedToday++
        return Outcome.Ok(gains = listOf(farm.grant(Gain("energy", 1))))
    }

    // ------------------------------------------------------------------ the sky

    /** Shooting stars fall late on nights without rain. */
    fun starsTonight(): Boolean = state.weather != Weather.RAIN && state.minutes >= STARS_FROM

    /**
     * Catches a shooting star. The first one on a farm is the star shard keepsake; later ones bring a
     * small gift, once a night. More stars after that are just pretty.
     */
    fun catchStar(): Outcome {
        if (s.starCaughtToday) return Outcome.Ok(Msg("star.again"))
        s.starCaughtToday = true
        s.starsCaught++
        if (findFirstMissing(KeepsakeSource.STAR)) return Outcome.Ok()
        return Outcome.Ok(Msg("star.caught"), listOf(farm.grant(smallGift(farm.rng("star")))))
    }

    /** The morning after rain a rainbow stands over the farm until [RAINBOW_UNTIL], until its coins are found. */
    fun rainbowVisible(): Boolean =
        s.rainbowDay == state.day && s.rainbowTakenDay != state.day && state.weather != Weather.RAIN && state.minutes < RAINBOW_UNTIL

    fun takeRainbow(): Outcome {
        if (!rainbowVisible()) return Outcome.Fail(Msg("rainbow.gone"))
        s.rainbowTakenDay = state.day
        val coins = farm.rng("rainbow").nextInt(ItemDb.RAINBOW_COINS_MIN, ItemDb.RAINBOW_COINS_MAX + 1)
        return Outcome.Ok(Msg("rainbow.found", coins), listOf(farm.grant(Gain("coins", coins))))
    }

    // ------------------------------------------------------------------ sparkles and keepsakes

    /**
     * Which of [spots] places has today's sparkle, or null if there is none today or it was found.
     * The world owns the list of places; the rules only pick one, the same way all day.
     */
    fun sparkleIndex(spots: Int): Int? {
        if (spots <= 0 || s.sparkleTakenDay == state.day) return null
        val rng = farm.rng("sparkle")
        if (rng.nextFloat() >= ItemDb.SPARKLE_CHANCE) return null
        return rng.nextInt(spots)
    }

    /** Picks up today's sparkle: a ground keepsake not found yet, or a few coins once all are found. */
    fun findSparkle(): Outcome {
        if (s.sparkleTakenDay == state.day) return Outcome.Fail(Msg("msg.not_field"))
        s.sparkleTakenDay = state.day
        val rng = farm.rng("sparkle_find")
        val missing = ItemDb.keepsakesFrom(KeepsakeSource.GROUND).filter { it !in s.keepsakes }
        if (missing.isNotEmpty()) {
            find(missing[rng.nextInt(missing.size)])
            return Outcome.Ok()
        }
        val coins = rng.nextInt(ItemDb.SPARKLE_COINS_MIN, ItemDb.SPARKLE_COINS_MAX + 1)
        return Outcome.Ok(Msg("sparkle.coins", coins), listOf(farm.grant(Gain("coins", coins))))
    }

    /** A clover harvest may hide a four-leaf clover. */
    internal fun cloverLuck(rng: Random) {
        if (rng.nextFloat() < ItemDb.CLOVER_KEEPSAKE_CHANCE) findFirstMissing(KeepsakeSource.CLOVER)
    }

    fun has(id: String): Boolean = id in s.keepsakes

    val found: Int get() = s.keepsakes.size

    private fun findFirstMissing(source: KeepsakeSource): Boolean {
        val id = ItemDb.keepsakesFrom(source).firstOrNull { it !in s.keepsakes } ?: return false
        find(id)
        return true
    }

    /** Puts a keepsake on the shelf with a little XP, and pays the shelf reward once every one is found. */
    internal fun find(id: String) {
        if (id in s.keepsakes || ItemDb.keepsakes.none { it.id == id }) return
        s.keepsakes += id
        farm.addXp(ItemDb.KEEPSAKE_XP)
        val done = !s.shelfRewarded && ItemDb.keepsakes.all { it.id in s.keepsakes }
        if (done) {
            s.shelfRewarded = true
            farm.grant(Gain("coins", ItemDb.SHELF_REWARD_COINS))
            farm.addXp(ItemDb.SHELF_REWARD_XP)
        }
        farm.events += FarmEvent.Keepsake(id, done)
    }

    // ------------------------------------------------------------------ the night

    /** Resets the daily counters. [yesterday] is the weather of the day that just ended. */
    internal fun newDay(yesterday: Weather) {
        s.wishesToday = 0
        s.wishedRain = false
        s.restedToday = 0
        s.starCaughtToday = false
        if (yesterday == Weather.RAIN && state.weather != Weather.RAIN) s.rainbowDay = state.day
    }

    companion object {
        /** Shooting stars from 20:30, once it is properly dark. */
        const val STARS_FROM = 20 * 60 + 30

        /** The rainbow fades at 10:00. */
        const val RAINBOW_UNTIL = 10 * 60

        private const val MISS = "wish.miss."

        /** How many ways a wish can miss (wish.miss.0 and up). */
        const val MISS_LINES = 4

        /** True for the message of a wish that didn't come true. */
        fun isMiss(msg: Msg?): Boolean = msg?.key?.startsWith(MISS) == true
    }
}
