package dev.mursal.dewfield.model

import kotlinx.serialization.json.Json

/**
 * Turns [GameState] into JSON and back. Decoding repairs what it can (unknown items,
 * out-of-range numbers, missing plots) and returns null for a file that is not a save at all,
 * so a damaged save never crashes the game.
 */
object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
        coerceInputValues = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    /**
     * Reads a save for the current [field]. [fieldV2] is the field layout of version 2 saves,
     * used to move their plots onto the current field; without it old plots are dropped.
     */
    fun decode(text: String, field: FieldLayout, fieldV2: FieldLayout? = null): GameState? {
        val s = try {
            json.decodeFromString(GameState.serializer(), text)
        } catch (e: Exception) {
            return null
        }
        if (s.version > SAVE_VERSION || s.version < 2) return null
        if (s.version < 3) migrateV2(s, field, fieldV2)
        s.version = SAVE_VERSION
        repair(s, field)
        return s
    }

    /**
     * Version 3 turned the maps for portrait play. Each plot moves to the cell with the same
     * index in the same block of the new field, so crops survive, and the saved standing spot
     * is dropped because it points into the old map.
     */
    private fun migrateV2(s: GameState, field: FieldLayout, fieldV2: FieldLayout?) {
        if (fieldV2 != null) {
            val moved = HashMap<Cell, Cell>()
            fieldV2.blocks.zip(field.blocks).forEach { (from, to) -> from.zip(to).forEach { (a, b) -> moved[a] = b } }
            val plots = s.plots.mapNotNull { p -> moved[p.cell]?.let { p.copy(x = it.x, y = it.y) } }
            s.plots.clear()
            s.plots += plots
        }
        s.playerX = Float.NaN
        s.playerY = Float.NaN
    }

    private fun repair(s: GameState, field: FieldLayout) {
        s.day = s.day.coerceAtLeast(1)
        s.minutes = s.minutes.coerceIn(Farm.DAY_START.toDouble(), Farm.DAY_END.toDouble())
        s.energy = s.energy.coerceIn(0, Farm.MAX_ENERGY)
        s.money = s.money.coerceAtLeast(0)
        s.xp = s.xp.coerceAtLeast(0)
        s.inventory.entries.removeAll { (k, n) -> ItemDb.items[ItemKey.id(k)] == null || n <= 0 }
        if (s.selected != null && (s.inventory[s.selected!!] ?: 0) <= 0) s.selected = null

        ItemDb.upgrades.forEach { (id, def) -> s.upgrades[id] = (s.upgrades[id] ?: 1).coerceIn(1, def.maxLevel) }
        s.upgrades.keys.retainAll(ItemDb.upgrades.keys)
        s.machines.keys.retainAll(ItemDb.machines.keys)
        if ("mill" !in s.machines) s.machines["mill"] = MachineState("mill")
        s.machines.values.forEach { m ->
            m.queue.removeAll { ItemDb.recipes[it.recipe]?.machine != m.id }
            m.ready.entries.removeAll { (k, n) -> ItemDb.items[ItemKey.id(k)] == null || n <= 0 }
        }
        ItemDb.troughs.forEach { (id, def) -> s.troughs[id] = (s.troughs[id] ?: 0).coerceIn(0, def.capacity) }
        s.troughs.keys.retainAll(ItemDb.troughs.keys)
        s.baskets.values.forEach { b -> b.entries.removeAll { (k, n) -> ItemDb.items[ItemKey.id(k)] == null || n <= 0 } }

        s.animals.removeAll { ItemDb.animals[it.kind] == null }
        s.animals.forEach { it.mood = it.mood.coerceIn(0, Farm.MAX_MOOD) }
        s.nextAnimalId = maxOf(s.nextAnimalId, (s.animals.maxOfOrNull { it.id } ?: 0) + 1)

        // Keep exactly one plot per field cell, whatever the file says.
        val byCell = s.plots.associateBy { it.cell }
        s.plots.clear()
        for (c in field.allCells) {
            val p = byCell[c] ?: PlotState(c.x, c.y)
            if (p.crop != null && ItemDb.crops[p.crop!!] == null) {
                p.crop = null
                p.dead = false
                p.growth = 0
            }
            if (p.crop == null) {
                p.dead = false
                p.growth = 0
                p.harvests = 0
            }
            p.growth = p.growth.coerceAtLeast(0)
            p.dryNights = p.dryNights.coerceAtLeast(0)
            s.plots += p
        }

        ItemDb.sellable.forEach { id ->
            val p = s.prices.getOrPut(id) { PriceState() }
            p.mult = p.mult.coerceIn(Farm.PRICE_MIN, Farm.PRICE_MAX)
            p.prevMult = p.prevMult.coerceIn(Farm.PRICE_MIN, Farm.PRICE_MAX)
        }
        s.prices.keys.retainAll(ItemDb.sellable.toSet())
        if (s.hotItem != null && s.hotItem !in ItemDb.items) s.hotItem = null
        if (s.tomorrowHotItem != null && s.tomorrowHotItem !in ItemDb.items) s.tomorrowHotItem = null
        s.orders.removeAll { o -> o.lines.isEmpty() || o.lines.any { ItemDb.items[it.item] == null } }
        s.nextOrderId = maxOf(s.nextOrderId, (s.orders.maxOfOrNull { it.id } ?: 0) + 1)

        val canCap = ItemDb.upgrade(ItemDb.UPGRADE_CAN).levels[s.upgrades.getValue(ItemDb.UPGRADE_CAN) - 1].value
        s.canWater = s.canWater.coerceIn(0, canCap)

        val l = s.leisure
        val keepsakes = l.keepsakes.filter { id -> ItemDb.keepsakes.any { it.id == id } }.distinct()
        l.keepsakes.clear()
        l.keepsakes += keepsakes
        l.wishesToday = l.wishesToday.coerceIn(0, ItemDb.WISHES_PER_DAY)
        l.restedToday = l.restedToday.coerceIn(0, ItemDb.REST_CAP)
        s.gift.claimed = s.gift.claimed.coerceAtLeast(0)
        s.gift.lastGameDay = s.gift.lastGameDay.coerceIn(0, s.day)
    }
}
