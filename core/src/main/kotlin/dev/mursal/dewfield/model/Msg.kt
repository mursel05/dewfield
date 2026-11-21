package dev.mursal.dewfield.model

/**
 * A translatable message: an i18n key plus arguments. Arguments may be plain values or
 * nested [Msg]s (for example an item name), which the UI translates recursively.
 * The rules layer never builds display text itself, so it stays testable and language-free.
 */
data class Msg(val key: String, val args: List<Any> = emptyList()) {
    constructor(key: String, vararg args: Any) : this(key, args.toList())

    companion object {
        fun item(id: String) = Msg("item.$id")
        fun animal(kind: String) = Msg("animal.$kind")
        fun machine(id: String) = Msg("machine.$id")
        fun upgrade(id: String) = Msg("upgrade.$id")
        fun client(id: String) = Msg("client.$id")
        fun weather(w: Weather) = Msg("weather.${w.name.lowercase()}")
    }
}

/** Something gained by an action, used for floating "+2 Carrot" texts. */
data class Gain(val key: String, val amount: Int)

sealed interface Outcome {
    data class Ok(val msg: Msg? = null, val gains: List<Gain> = emptyList()) : Outcome
    data class Fail(val msg: Msg) : Outcome

    val ok: Boolean get() = this is Ok
}

sealed interface FarmEvent {
    data class LevelUp(val level: Int, val unlocks: List<String>) : FarmEvent
    /** The clock reached midnight: the farmer collapses. */
    data object Exhausted : FarmEvent
}

data class MorningReport(
    val day: Int,
    val weather: Weather,
    val passedOut: Boolean,
    val cropsGrew: Int,
    val cropsRipe: Int,
    val cropsWithered: Int,
    val products: Map<String, Int>,
    val hungry: List<String>,
    val arrived: List<String>,
    val machineGoods: Map<String, Int>,
    val ordersExpired: Int,
    val newOrders: Int,
    val hotItem: String?,
)
