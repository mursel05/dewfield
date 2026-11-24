package dev.mursal.dewfield.model

import kotlinx.serialization.Serializable

/**
 * Everything that is saved. Plain mutable data; all rules live in [Farm].
 * Bump [SAVE_VERSION] and migrate in [SaveCodec] when the shape changes incompatibly.
 */
const val SAVE_VERSION = 3

@Serializable
data class Cell(val x: Int, val y: Int) {
    operator fun plus(o: Cell) = Cell(x + o.x, y + o.y)
    fun manhattan(o: Cell) = kotlin.math.abs(x - o.x) + kotlin.math.abs(y - o.y)
    override fun toString() = "($x,$y)"
}

@Serializable
data class PlotState(
    val x: Int,
    val y: Int,
    var tilled: Boolean = false,
    var watered: Boolean = false,
    var fertilized: Boolean = false,
    var crop: String? = null,
    /** Watered nights grown since planting (or since the last harvest for regrowing crops). */
    var growth: Int = 0,
    var dryNights: Int = 0,
    /** True once the crop spent a night dry. Lowers the harvest quality. */
    var missedWater: Boolean = false,
    var dead: Boolean = false,
    var harvests: Int = 0,
) {
    val cell: Cell get() = Cell(x, y)
}

@Serializable
data class AnimalState(
    val id: Int,
    val kind: String,
    val name: String,
    var mood: Int = Farm.START_MOOD,
    var pettedToday: Boolean = false,
    var fedLastNight: Boolean = false,
    var hungryDays: Int = 0,
    var productProgress: Int = 0,
    /** Bought animals arrive the next morning. */
    var arriving: Boolean = false,
)

@Serializable
data class MachineJob(val recipe: String, val quality: Int = 0)

@Serializable
data class MachineState(
    val id: String,
    val queue: MutableList<MachineJob> = mutableListOf(),
    /** Finished goods waiting to be collected: inventory key -> amount. */
    val ready: MutableMap<String, Int> = mutableMapOf(),
)

@Serializable
data class PriceState(var mult: Float = 1f, var prevMult: Float = 1f)

@Serializable
data class OrderLine(val item: String, val qty: Int)

@Serializable
data class Order(
    val id: Int,
    val client: String,
    val lines: List<OrderLine>,
    val reward: Int,
    val xp: Int,
    var daysLeft: Int,
)

@Serializable
data class Stats(
    var earned: Long = 0,
    var spent: Long = 0,
    var harvested: Int = 0,
    var ordersDone: Int = 0,
    var productsCollected: Int = 0,
    var daysPlayed: Int = 0,
)

/**
 * The fountain, benches, shooting stars, rainbows and keepsakes. Counters ending in "Today" reset
 * every night. Added after save version 3 shipped: every field has a default, so older saves load.
 */
@Serializable
data class LeisureState(
    var wishesToday: Int = 0,
    var wishesMade: Int = 0,
    /** Set when a wish for rain came true, so the morning report can say so. */
    var wishedRain: Boolean = false,
    /** Energy rested on benches today. */
    var restedToday: Int = 0,
    var starCaughtToday: Boolean = false,
    var starsCaught: Int = 0,
    /** The day whose morning shows a rainbow (the day after rain), and the day its coins were found. */
    var rainbowDay: Int = 0,
    var rainbowTakenDay: Int = 0,
    /** The day today's sparkle was picked up. */
    var sparkleTakenDay: Int = 0,
    /** Keepsakes found, in the order they were found. */
    val keepsakes: MutableList<String> = mutableListOf(),
    var shelfRewarded: Boolean = false,
)

/** The daily gift calendar. [lastDay] is the calendar day (in the device's time zone) of the last claim. */
@Serializable
data class GiftState(
    var lastDay: Long? = null,
    /** Gifts claimed so far; the next one is `claimed % 7` in the calendar. */
    var claimed: Int = 0,
    /** Game day of the last claim. A gift also needs a night's sleep since, so moving the phone's clock ahead pays nothing extra. */
    var lastGameDay: Int = 0,
)

@Serializable
data class GameState(
    var version: Int = SAVE_VERSION,
    var seed: Long = 1L,
    var day: Int = 1,
    /** Minutes since midnight. The day runs from [Farm.DAY_START] to [Farm.DAY_END]. */
    var minutes: Double = Farm.DAY_START.toDouble(),
    var money: Int = Farm.START_MONEY,
    var energy: Int = Farm.MAX_ENERGY,
    var xp: Int = 0,
    var location: String = "house",
    var playerX: Float = Float.NaN,
    var playerY: Float = Float.NaN,
    var facing: Int = 0,
    /** Inventory key ("carrot", "carrot#1" for silver, "carrot#2" for gold) -> amount. */
    val inventory: MutableMap<String, Int> = mutableMapOf(),
    var selected: String? = null,
    var canWater: Int = 20,
    /** Upgrade id -> current level (1 = starting level). */
    val upgrades: MutableMap<String, Int> = mutableMapOf(),
    val machines: MutableMap<String, MachineState> = mutableMapOf(),
    val troughs: MutableMap<String, Int> = mutableMapOf(),
    /** Animal products waiting in the coop or barn: housing name -> inventory key -> amount. */
    val baskets: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(),
    val animals: MutableList<AnimalState> = mutableListOf(),
    var nextAnimalId: Int = 1,
    val plots: MutableList<PlotState> = mutableListOf(),
    var weather: Weather = Weather.SUNNY,
    var tomorrowWeather: Weather = Weather.SUNNY,
    val prices: MutableMap<String, PriceState> = mutableMapOf(),
    val soldToday: MutableMap<String, Int> = mutableMapOf(),
    var hotItem: String? = null,
    var tomorrowHotItem: String? = null,
    val orders: MutableList<Order> = mutableListOf(),
    var nextOrderId: Int = 1,
    val stats: Stats = Stats(),
    /** One-time hints already shown. */
    val seenHints: MutableSet<String> = mutableSetOf(),
    val leisure: LeisureState = LeisureState(),
    val gift: GiftState = GiftState(),
)
