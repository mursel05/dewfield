package dev.mursal.dewfield.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** The farm's field cells, split into blocks that unlock with the field upgrade. */
data class FieldLayout(val blocks: List<List<Cell>>) {
    private val blockOf: Map<Cell, Int> = buildMap { blocks.forEachIndexed { i, b -> b.forEach { put(it, i) } } }
    fun blockIndex(cell: Cell): Int? = blockOf[cell]
    val allCells: List<Cell> get() = blocks.flatten()
}

/**
 * All game rules. Operates on [GameState] and never touches rendering, input or files,
 * so every rule is unit tested. Actions return [Outcome]; level-ups and the midnight
 * collapse are queued in [events] for the UI to pick up.
 */
class Farm(val state: GameState, val field: FieldLayout) {

    companion object {
        const val DAY_START = 6 * 60
        const val DAY_END = 24 * 60
        const val SECONDS_PER_GAME_MINUTE = 0.6
        const val MARKET_OPEN = 7 * 60
        const val MARKET_CLOSE = 20 * 60
        const val MAX_ENERGY = 100
        const val PASS_OUT_ENERGY = 60
        const val START_MONEY = 500
        const val START_MOOD = 5
        const val MAX_MOOD = 10
        const val MAX_ORDERS = 3

        const val COST_TILL = 3
        const val COST_WATER = 1
        const val COST_PLANT = 1
        const val COST_FERTILIZE = 1
        const val COST_HARVEST = 1
        const val COST_CLEAR = 2
        const val COST_FILL = 1

        const val PRICE_MIN = 0.7f
        const val PRICE_MAX = 1.45f
        const val HOT_BONUS = 1.3f
        const val SATURATION_STEP = 0.02f
        const val SATURATION_FLOOR = 0.6f

        val CHICKEN_NAMES = listOf("Pipa", "Nana", "Dido", "Mimi", "Tutu", "Lala", "Zuzu", "Bibi", "Coco", "Kiki")
        val COW_NAMES = listOf("Marta", "Bella", "Maral", "Zira", "Sona", "Duman", "Hazel")
        val SHEEP_NAMES = listOf("Bulud", "Pambiq", "Qartopu", "Lepe", "Nur", "Ziko", "Momo")

        /** A fresh farm: a little money, cheap seeds, two chickens with some grain. */
        fun newGame(seed: Long, field: FieldLayout): Farm {
            val s = GameState(seed = seed)
            field.allCells.forEach { s.plots += PlotState(it.x, it.y) }
            ItemDb.upgrades.keys.forEach { s.upgrades[it] = 1 }
            s.machines["mill"] = MachineState("mill")
            ItemDb.troughs.keys.forEach { s.troughs[it] = 0 }
            s.troughs["hopper"] = 4
            s.inventory["radish_seeds"] = 6
            s.inventory["lettuce_seeds"] = 4
            s.inventory["grain"] = 10
            s.selected = "radish_seeds"
            val farm = Farm(s, field)
            s.canWater = farm.canCapacity
            repeat(2) { s.animals += AnimalState(s.nextAnimalId++, "chicken", CHICKEN_NAMES[it]) }
            ItemDb.sellable.forEach { s.prices[it] = PriceState() }
            val rng = farm.rng("weather")
            s.weather = Weather.SUNNY
            s.tomorrowWeather = farm.rollWeather(rng)
            s.tomorrowHotItem = farm.rollHotItem(farm.rng("hot"))
            farm.refillOrders()
            return farm
        }
    }

    val events = ArrayDeque<FarmEvent>()
    private var exhaustedSent = false
    private val plotIndex = HashMap<Cell, PlotState>()

    init {
        state.plots.forEach { plotIndex[it.cell] = it }
    }

    // ------------------------------------------------------------------ derived values

    val level: Int get() = levelFor(state.xp)

    fun levelFor(xp: Int): Int = ItemDb.levelXp.indexOfLast { xp >= it } + 1

    /** XP progress inside the current level as (have, need), or null at max level. */
    fun levelProgress(): Pair<Int, Int>? {
        val lv = level
        if (lv >= ItemDb.levelXp.size) return null
        val base = ItemDb.levelXp[lv - 1]
        return (state.xp - base) to (ItemDb.levelXp[lv] - base)
    }

    fun upgradeLevel(id: String): Int = state.upgrades[id] ?: 1
    fun upgradeValue(id: String): Int = ItemDb.upgrade(id).levels[upgradeLevel(id) - 1].value
    val canCapacity: Int get() = upgradeValue(ItemDb.UPGRADE_CAN)
    val fieldBlocks: Int get() = upgradeValue(ItemDb.UPGRADE_FIELD)

    fun capacity(housing: Housing): Int = when (housing) {
        Housing.COOP -> upgradeValue(ItemDb.UPGRADE_COOP)
        Housing.BARN -> upgradeValue(ItemDb.UPGRADE_BARN)
    }

    fun animalsIn(housing: Housing): List<AnimalState> =
        state.animals.filter { ItemDb.animal(it.kind).housing == housing }

    fun isUnlocked(id: String): Boolean = level >= ItemDb.unlockLevelOf(id)

    val isMarketOpen: Boolean get() = state.minutes >= MARKET_OPEN && state.minutes < MARKET_CLOSE

    /** 0 at day, 1 at deep night; used for lighting. */
    fun darkness(): Float {
        val h = state.minutes / 60.0
        return when {
            h < 6.5 -> (1.0 - (h - 6.0) / 0.5).coerceIn(0.0, 1.0).toFloat() * 0.5f
            h < 18.0 -> 0f
            h < 21.0 -> ((h - 18.0) / 3.0).toFloat()
            else -> 1f
        }
    }

    fun plot(cell: Cell): PlotState? = plotIndex[cell]

    fun isPlotUnlocked(cell: Cell): Boolean {
        val block = field.blockIndex(cell) ?: return false
        return block < fieldBlocks
    }

    fun isRipe(p: PlotState): Boolean {
        val crop = p.crop ?: return false
        if (p.dead) return false
        val def = ItemDb.crop(crop)
        return p.growth >= if (p.harvests == 0) def.growDays else def.regrowDays
    }

    /** Nights of watered growth still needed. */
    fun daysLeft(p: PlotState): Int {
        val def = ItemDb.crop(p.crop ?: return 0)
        val target = if (p.harvests == 0) def.growDays else def.regrowDays
        return max(0, target - p.growth)
    }

    /** 0..3 growth stage for drawing; 3 = ripe. */
    fun growthStage(p: PlotState): Int {
        if (isRipe(p)) return 3
        val def = ItemDb.crop(p.crop ?: return 0)
        if (p.harvests > 0) return 2
        val f = p.growth.toFloat() / def.growDays
        return when {
            f < 0.34f -> 0
            f < 0.67f -> 1
            else -> 2
        }
    }

    /** Harvest quality: watered every night and fertilized = gold, one of the two = silver. */
    fun expectedQuality(p: PlotState): Int = (if (!p.missedWater) 1 else 0) + (if (p.fertilized) 1 else 0)

    // ------------------------------------------------------------------ inventory

    fun count(key: String): Int = state.inventory[key] ?: 0

    /** Amount of an item across all qualities. */
    fun countAll(id: String): Int = (0..2).sumOf { count(ItemKey.of(id, it)) }

    fun add(key: String, n: Int) {
        if (n <= 0) return
        state.inventory[key] = count(key) + n
    }

    fun remove(key: String, n: Int): Boolean {
        val have = count(key)
        if (have < n) return false
        if (have == n) state.inventory.remove(key) else state.inventory[key] = have - n
        if (state.selected == key && count(key) == 0) state.selected = null
        return true
    }

    /** Removes n of an item, lowest quality first. Returns the qualities taken, or null if not enough. */
    fun removeAnyQuality(id: String, n: Int): List<Int>? {
        if (countAll(id) < n) return null
        val taken = ArrayList<Int>(n)
        var left = n
        for (q in 0..2) {
            val key = ItemKey.of(id, q)
            val take = min(left, count(key))
            if (take > 0) {
                remove(key, take)
                repeat(take) { taken += q }
                left -= take
            }
        }
        return taken
    }

    fun select(key: String?) {
        state.selected = key
    }

    // ------------------------------------------------------------------ time

    /** Advances the clock by real seconds. Queues [FarmEvent.Exhausted] once at midnight. */
    fun advanceTime(seconds: Double) {
        if (state.minutes >= DAY_END) return
        state.minutes = min(DAY_END.toDouble(), state.minutes + seconds / SECONDS_PER_GAME_MINUTE)
        if (state.minutes >= DAY_END && !exhaustedSent) {
            exhaustedSent = true
            events += FarmEvent.Exhausted
        }
    }

    // ------------------------------------------------------------------ field actions

    private fun spend(cost: Int): Outcome.Fail? {
        if (state.energy < cost) return Outcome.Fail(Msg("msg.too_tired"))
        state.energy -= cost
        return null
    }

    private fun fieldPlot(cell: Cell): Pair<PlotState?, Outcome.Fail?> {
        val p = plot(cell) ?: return null to Outcome.Fail(Msg("msg.not_field"))
        if (!isPlotUnlocked(cell)) return null to Outcome.Fail(Msg("msg.field_locked"))
        return p to null
    }

    fun till(cell: Cell): Outcome {
        val (p, fail) = fieldPlot(cell)
        if (fail != null || p == null) return fail!!
        if (p.tilled) return Outcome.Fail(Msg("msg.already_tilled"))
        spend(COST_TILL)?.let { return it }
        p.tilled = true
        return Outcome.Ok()
    }

    fun water(cell: Cell): Outcome {
        val (p, fail) = fieldPlot(cell)
        if (fail != null || p == null) return fail!!
        if (!p.tilled) return Outcome.Fail(Msg("msg.till_first"))
        if (p.dead) return Outcome.Fail(Msg("msg.crop_dead"))
        if (p.watered) return Outcome.Fail(Msg("msg.already_watered"))
        if (state.canWater <= 0) return Outcome.Fail(Msg("msg.can_empty"))
        spend(COST_WATER)?.let { return it }
        p.watered = true
        state.canWater--
        return Outcome.Ok()
    }

    fun plant(cell: Cell, seedId: String): Outcome {
        val (p, fail) = fieldPlot(cell)
        if (fail != null || p == null) return fail!!
        val crop = ItemDb.cropBySeed[seedId] ?: return Outcome.Fail(Msg("msg.pick_seed"))
        if (!p.tilled) return Outcome.Fail(Msg("msg.till_first"))
        if (p.crop != null) return Outcome.Fail(Msg("msg.plot_taken"))
        if (count(seedId) <= 0) return Outcome.Fail(Msg("msg.no_seeds", Msg.item(seedId)))
        spend(COST_PLANT)?.let { return it }
        remove(seedId, 1)
        p.crop = crop.id
        p.growth = 0
        p.dryNights = 0
        p.missedWater = false
        p.dead = false
        p.harvests = 0
        return Outcome.Ok()
    }

    fun fertilize(cell: Cell): Outcome {
        val (p, fail) = fieldPlot(cell)
        if (fail != null || p == null) return fail!!
        if (!p.tilled) return Outcome.Fail(Msg("msg.till_first"))
        if (p.dead) return Outcome.Fail(Msg("msg.crop_dead"))
        if (p.fertilized) return Outcome.Fail(Msg("msg.already_fertilized"))
        if (count(ItemDb.FERTILIZER) <= 0) return Outcome.Fail(Msg("msg.no_seeds", Msg.item(ItemDb.FERTILIZER)))
        spend(COST_FERTILIZE)?.let { return it }
        remove(ItemDb.FERTILIZER, 1)
        p.fertilized = true
        return Outcome.Ok()
    }

    fun harvest(cell: Cell): Outcome {
        val (p, fail) = fieldPlot(cell)
        if (fail != null || p == null) return fail!!
        if (!isRipe(p)) return Outcome.Fail(Msg("msg.not_ripe"))
        spend(COST_HARVEST)?.let { return it }
        val def = ItemDb.crop(p.crop!!)
        val rng = Random(state.seed * 7919 + state.day * 104_729L + cell.x * 131 + cell.y * 7 + p.harvests)
        val amount = if (def.yieldMax > def.yieldMin) rng.nextInt(def.yieldMin, def.yieldMax + 1) else def.yieldMin
        val quality = expectedQuality(p)
        val key = ItemKey.of(def.produce, quality)
        add(key, amount)
        state.stats.harvested += amount
        addXp(def.xp)
        if (def.regrowDays > 0) {
            p.growth = 0
            p.harvests++
            p.missedWater = false
        } else {
            p.crop = null
            p.growth = 0
            p.harvests = 0
            p.fertilized = false
            p.missedWater = false
        }
        p.dryNights = 0
        return Outcome.Ok(gains = listOf(Gain(key, amount)))
    }

    fun clear(cell: Cell): Outcome {
        val (p, fail) = fieldPlot(cell)
        if (fail != null || p == null) return fail!!
        if (!p.dead) return Outcome.Fail(Msg("msg.nothing_to_clear"))
        spend(COST_CLEAR)?.let { return it }
        p.crop = null
        p.dead = false
        p.growth = 0
        p.harvests = 0
        p.fertilized = false
        return Outcome.Ok()
    }

    fun refillCan(): Outcome {
        if (state.canWater >= canCapacity) return Outcome.Fail(Msg("msg.can_full"))
        state.canWater = canCapacity
        return Outcome.Ok(Msg("msg.can_refilled"))
    }

    // ------------------------------------------------------------------ animals

    fun trough(id: String): Int = state.troughs[id] ?: 0

    /** Fills a trough from the bag with the feed that trough takes. */
    fun fillTrough(troughId: String): Outcome {
        val def = ItemDb.troughs[troughId] ?: return Outcome.Fail(Msg("msg.not_field"))
        val space = def.capacity - trough(troughId)
        if (space <= 0) return Outcome.Fail(Msg("msg.trough_full"))
        val kinds = ItemDb.feedAliases[def.feed] ?: listOf(def.feed)
        val available = kinds.sumOf { countAll(it) }
        if (available <= 0) return Outcome.Fail(Msg("msg.no_feed", Msg("feed.${def.feed}")))
        spend(COST_FILL)?.let { return it }
        var left = space
        for (kind in kinds) {
            val take = min(left, countAll(kind))
            if (take > 0) {
                removeAnyQuality(kind, take)
                left -= take
            }
        }
        val added = space - left
        state.troughs[troughId] = trough(troughId) + added
        return Outcome.Ok(Msg("msg.trough_filled", added, Msg("feed.${def.feed}")))
    }

    fun animalById(id: Int): AnimalState? = state.animals.firstOrNull { it.id == id }

    fun pet(id: Int): Outcome {
        val a = animalById(id) ?: return Outcome.Fail(Msg("msg.not_field"))
        if (a.arriving) return Outcome.Fail(Msg("msg.not_field"))
        if (a.pettedToday) return Outcome.Fail(Msg("msg.already_petted", a.name))
        a.pettedToday = true
        a.mood = min(MAX_MOOD, a.mood + 1)
        return Outcome.Ok(gains = listOf(Gain("heart", 1)))
    }

    fun basket(housing: Housing): MutableMap<String, Int> =
        state.baskets.getOrPut(housing.name.lowercase()) { mutableMapOf() }

    fun collectBasket(housing: Housing): Outcome {
        val b = basket(housing)
        if (b.isEmpty()) return Outcome.Fail(Msg(if (housing == Housing.COOP) "msg.no_eggs" else "msg.no_products"))
        val gains = b.map { (k, n) -> Gain(k, n) }
        var total = 0
        b.forEach { (k, n) -> add(k, n); total += n }
        b.clear()
        state.stats.productsCollected += total
        addXp(total * 2)
        return Outcome.Ok(gains = gains)
    }

    fun buyAnimal(kind: String): Outcome {
        val def = ItemDb.animals[kind] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (!isUnlocked(kind)) return Outcome.Fail(Msg("msg.needs_level", def.unlockLevel))
        if (animalsIn(def.housing).size >= capacity(def.housing)) {
            return Outcome.Fail(Msg(if (def.housing == Housing.COOP) "msg.coop_full" else "msg.barn_full"))
        }
        if (state.money < def.price) return Outcome.Fail(Msg("msg.no_money"))
        pay(def.price)
        val names = when (kind) {
            "chicken" -> CHICKEN_NAMES
            "cow" -> COW_NAMES
            else -> SHEEP_NAMES
        }
        val used = state.animals.map { it.name }.toSet()
        val name = names.firstOrNull { it !in used } ?: "${names.first()} ${state.nextAnimalId}"
        state.animals += AnimalState(state.nextAnimalId++, kind, name, arriving = true)
        addXp(5)
        return Outcome.Ok(Msg("msg.animal_bought", Msg.animal(kind), name))
    }

    /** Product quality from mood: 8+ gold, 5+ silver. */
    fun moodQuality(mood: Int): Int = when {
        mood >= 8 -> 2
        mood >= 5 -> 1
        else -> 0
    }

    // ------------------------------------------------------------------ machines

    fun owns(machine: String): Boolean = state.machines.containsKey(machine)

    fun machine(id: String): MachineState? = state.machines[id]

    fun loadMachine(machineId: String, recipeId: String, qty: Int): Outcome {
        val m = machine(machineId) ?: return Outcome.Fail(Msg("msg.not_owned"))
        val recipe = ItemDb.recipes[recipeId] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (recipe.machine != machineId) return Outcome.Fail(Msg("msg.not_field"))
        val free = ItemDb.machine(machineId).slots - m.queue.size
        if (free <= 0) return Outcome.Fail(Msg("msg.machine_full"))
        val n = min(qty, min(free, countAll(recipe.input)))
        if (n <= 0) return Outcome.Fail(Msg("msg.no_seeds", Msg.item(recipe.input)))
        val qualities = removeAnyQuality(recipe.input, n)!!
        qualities.forEach { q -> m.queue += MachineJob(recipeId, if (recipe.keepsQuality) q else 0) }
        addXp(n)
        return Outcome.Ok(Msg("msg.machine_loaded", n, Msg.item(recipe.input)))
    }

    fun collectMachine(machineId: String): Outcome {
        val m = machine(machineId) ?: return Outcome.Fail(Msg("msg.not_owned"))
        if (m.ready.isEmpty()) return Outcome.Fail(Msg("msg.machine_empty"))
        val gains = m.ready.map { (k, n) -> Gain(k, n) }
        m.ready.forEach { (k, n) -> add(k, n) }
        m.ready.clear()
        return Outcome.Ok(gains = gains)
    }

    // ------------------------------------------------------------------ food

    fun eat(key: String): Outcome {
        val def = ItemDb.items[ItemKey.id(key)] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (def.energy <= 0) return Outcome.Fail(Msg("msg.not_edible"))
        if (state.energy >= MAX_ENERGY) return Outcome.Fail(Msg("msg.not_hungry"))
        if (!remove(key, 1)) return Outcome.Fail(Msg("msg.no_seeds", Msg.item(def.id)))
        val before = state.energy
        state.energy = min(MAX_ENERGY, state.energy + def.energy)
        return Outcome.Ok(gains = listOf(Gain("energy", state.energy - before)))
    }

    fun canCook(mealId: String): Boolean =
        ItemDb.meals[mealId]?.ingredients?.groupingBy { it }?.eachCount()?.all { (id, n) -> countAll(id) >= n } ?: false

    fun cook(mealId: String): Outcome {
        val meal = ItemDb.meals[mealId] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (!canCook(mealId)) return Outcome.Fail(Msg("msg.missing_ingredients"))
        meal.ingredients.forEach { removeAnyQuality(it, 1) }
        add(mealId, 1)
        addXp(2)
        return Outcome.Ok(gains = listOf(Gain(mealId, 1)))
    }

    // ------------------------------------------------------------------ market

    private fun pay(amount: Int) {
        state.money -= amount
        state.stats.spent += amount
    }

    fun buy(itemId: String, qty: Int): Outcome {
        val def = ItemDb.items[itemId] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (def.buyPrice <= 0 || qty <= 0) return Outcome.Fail(Msg("msg.not_field"))
        if (!isUnlocked(itemId)) return Outcome.Fail(Msg("msg.needs_level", def.unlockLevel))
        val total = def.buyPrice * qty
        if (state.money < total) return Outcome.Fail(Msg("msg.no_money"))
        pay(total)
        add(itemId, qty)
        if (state.selected == null && def.category == Category.SEED) state.selected = itemId
        return Outcome.Ok(gains = listOf(Gain(itemId, qty)))
    }

    private fun saturation(id: String, extra: Int = 0): Float =
        max(SATURATION_FLOOR, 1f - SATURATION_STEP * ((state.soldToday[id] ?: 0) + extra))

    /** Market multiplier for an item today, before quality and saturation. */
    fun marketMultiplier(id: String): Float {
        val m = state.prices[id]?.mult ?: 1f
        return if (state.hotItem == id) m * HOT_BONUS else m
    }

    /** What Leyla pays for the next unit of this inventory key right now. */
    fun priceOf(key: String, extraSold: Int = 0): Int {
        val id = ItemKey.id(key)
        val def = ItemDb.items[id] ?: return 0
        if (def.sellPrice <= 0) return 0
        val q = ItemDb.qualityMultiplier[ItemKey.quality(key)]
        return max(1, (def.sellPrice * q * marketMultiplier(id) * saturation(id, extraSold)).roundToInt())
    }

    /** Total for selling qty units now (each unit sold lowers the price a little). */
    fun quote(key: String, qty: Int): Int = (0 until qty).sumOf { priceOf(key, it) }

    /** -1, 0 or 1 compared to yesterday. */
    fun trend(id: String): Int {
        val p = state.prices[id] ?: return 0
        val d = p.mult - p.prevMult
        return when {
            d > 0.03f -> 1
            d < -0.03f -> -1
            else -> 0
        }
    }

    fun sell(key: String, qty: Int): Outcome {
        val id = ItemKey.id(key)
        val def = ItemDb.items[id] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (def.sellPrice <= 0) return Outcome.Fail(Msg("msg.cannot_sell"))
        if (qty <= 0 || count(key) < qty) return Outcome.Fail(Msg("msg.no_seeds", Msg.item(id)))
        val total = quote(key, qty)
        remove(key, qty)
        state.soldToday[id] = (state.soldToday[id] ?: 0) + qty
        state.money += total
        state.stats.earned += total
        return Outcome.Ok(Msg("msg.sold", qty, Msg.item(id), total), listOf(Gain("coins", total)))
    }

    // ------------------------------------------------------------------ workshop

    fun buyUpgrade(id: String): Outcome {
        val def = ItemDb.upgrades[id] ?: return Outcome.Fail(Msg("msg.not_field"))
        val current = upgradeLevel(id)
        if (current >= def.maxLevel) return Outcome.Fail(Msg("msg.max_level"))
        val next = def.levels[current]
        if (level < next.unlockLevel) return Outcome.Fail(Msg("msg.needs_level", next.unlockLevel))
        if (state.money < next.price) return Outcome.Fail(Msg("msg.no_money"))
        pay(next.price)
        state.upgrades[id] = current + 1
        if (id == ItemDb.UPGRADE_CAN) state.canWater = canCapacity
        return Outcome.Ok(Msg("msg.upgraded", Msg.upgrade(id)))
    }

    fun buyMachine(id: String): Outcome {
        val def = ItemDb.machines[id] ?: return Outcome.Fail(Msg("msg.not_field"))
        if (owns(id)) return Outcome.Fail(Msg("msg.already_owned"))
        if (level < def.unlockLevel) return Outcome.Fail(Msg("msg.needs_level", def.unlockLevel))
        if (state.money < def.price) return Outcome.Fail(Msg("msg.no_money"))
        pay(def.price)
        state.machines[id] = MachineState(id)
        return Outcome.Ok(Msg("msg.machine_bought", Msg.machine(id)))
    }

    // ------------------------------------------------------------------ orders

    fun canDeliver(order: Order): Boolean = order.lines.all { countAll(it.item) >= it.qty }

    fun deliver(orderId: Int): Outcome {
        val order = state.orders.firstOrNull { it.id == orderId } ?: return Outcome.Fail(Msg("msg.not_field"))
        if (!canDeliver(order)) return Outcome.Fail(Msg("msg.missing_items"))
        order.lines.forEach { removeAnyQuality(it.item, it.qty) }
        state.orders.remove(order)
        state.money += order.reward
        state.stats.earned += order.reward
        state.stats.ordersDone++
        addXp(order.xp)
        return Outcome.Ok(Msg("msg.order_done", Msg.client(order.client), order.reward), listOf(Gain("coins", order.reward)))
    }

    /** Items an order can ask for: produce of unlocked crops, unlocked animals' products, owned machines' goods. */
    fun orderCandidates(): List<String> {
        val out = LinkedHashSet<String>()
        ItemDb.crops.values.filter { isUnlocked(it.seed) && it.id != "clover" }.forEach { out += it.produce }
        ItemDb.animals.values.filter { isUnlocked(it.id) }.forEach { out += it.product }
        if (owns("cheese_press")) out += "cheese"
        if (owns("loom")) out += "cloth"
        return out.toList()
    }

    internal fun generateOrder(rng: Random): Order {
        val pool = orderCandidates()
        val lineCount = if (pool.size >= 3 && rng.nextFloat() < 0.4f) 2 else 1
        val picks = pool.shuffled(rng).take(lineCount)
        val target = 120 + 60 * level + rng.nextInt(0, 120)
        val lines = picks.map { id ->
            val base = ItemDb.item(id).sellPrice
            OrderLine(id, (target / lineCount / base.toFloat()).roundToInt().coerceIn(1, 20))
        }
        val value = lines.sumOf { ItemDb.item(it.item).sellPrice * it.qty }
        val reward = ((value * (1.5f + rng.nextFloat() * 0.4f)) / 5f).roundToInt() * 5
        return Order(
            id = state.nextOrderId++,
            // Each client posts one order at a time.
            client = (ItemDb.orderClients - state.orders.map { it.client }.toSet()).ifEmpty { ItemDb.orderClients }.let { it[rng.nextInt(it.size)] },
            lines = lines,
            reward = reward,
            xp = 10 + value / 30,
            daysLeft = rng.nextInt(3, 6),
        )
    }

    internal fun refillOrders(): Int {
        val rng = rng("orders")
        var added = 0
        while (state.orders.size < MAX_ORDERS) {
            state.orders += generateOrder(rng)
            added++
        }
        return added
    }

    // ------------------------------------------------------------------ XP

    fun addXp(n: Int) {
        if (n <= 0) return
        val before = level
        state.xp += n
        val after = level
        for (lv in before + 1..after) events += FarmEvent.LevelUp(lv, unlocksAt(lv))
    }

    /** Ids of everything that unlocks at a level (items, animals, machines, upgrade steps). */
    fun unlocksAt(lv: Int): List<String> = buildList {
        ItemDb.items.values.filter { it.unlockLevel == lv && it.buyPrice > 0 }.forEach { add("item.${it.id}") }
        ItemDb.animals.values.filter { it.unlockLevel == lv }.forEach { add("animal.${it.id}") }
        ItemDb.machines.values.filter { it.unlockLevel == lv && it.price > 0 }.forEach { add("machine.${it.id}") }
        ItemDb.upgrades.values.forEach { u ->
            if (u.levels.drop(1).any { it.unlockLevel == lv }) add("upgrade.${u.id}")
        }
    }

    // ------------------------------------------------------------------ the night

    internal fun rng(purpose: String, day: Int = state.day): Random =
        Random(state.seed * 1_000_003L + day * 7_919L + purpose.hashCode())

    internal fun rollWeather(rng: Random): Weather {
        val r = rng.nextFloat()
        return when {
            r < 0.18f -> Weather.RAIN
            r < 0.40f -> Weather.CLOUDY
            else -> Weather.SUNNY
        }
    }

    internal fun rollHotItem(rng: Random): String? {
        val pool = orderCandidates()
        return if (pool.isEmpty()) null else pool[rng.nextInt(pool.size)]
    }

    /**
     * Sleeps through the night: crops grow or dry out, animals eat from their troughs and
     * make products, machines finish, prices move, orders refresh and the weather changes.
     */
    fun endDay(passedOut: Boolean = false, forcedWeather: Weather? = null): MorningReport {
        var grew = 0
        var withered = 0
        for (p in state.plots) {
            val crop = p.crop
            if (crop != null && !p.dead && !isRipe(p)) {
                if (p.watered) {
                    p.growth++
                    p.dryNights = 0
                    grew++
                } else {
                    p.dryNights++
                    p.missedWater = true
                    if (p.dryNights >= ItemDb.crop(crop).witherDays) {
                        p.dead = true
                        withered++
                    }
                }
            }
            p.watered = false
        }

        val products = LinkedHashMap<String, Int>()
        val hungry = ArrayList<String>()
        val arrived = ArrayList<String>()
        for (a in state.animals) {
            if (a.arriving) {
                a.arriving = false
                arrived += a.name
                continue
            }
            val def = ItemDb.animal(a.kind)
            val troughId = ItemDb.troughByFeed.getValue(def.feed).id
            if (trough(troughId) >= def.feedPerDay) {
                state.troughs[troughId] = trough(troughId) - def.feedPerDay
                a.fedLastNight = true
                a.hungryDays = 0
                a.mood = min(MAX_MOOD, a.mood + 1)
                a.productProgress++
                if (a.productProgress >= def.productEvery) {
                    a.productProgress = 0
                    val key = ItemKey.of(def.product, moodQuality(a.mood))
                    val b = basket(def.housing)
                    b[key] = (b[key] ?: 0) + 1
                    products[def.product] = (products[def.product] ?: 0) + 1
                }
            } else {
                a.fedLastNight = false
                a.hungryDays++
                a.mood = max(0, a.mood - 2)
                hungry += a.name
            }
            a.pettedToday = false
        }

        val machineGoods = LinkedHashMap<String, Int>()
        for (m in state.machines.values) {
            for (job in m.queue) {
                val r = ItemDb.recipe(job.recipe)
                val key = ItemKey.of(r.output, job.quality)
                m.ready[key] = (m.ready[key] ?: 0) + r.outputQty
                machineGoods[r.output] = (machineGoods[r.output] ?: 0) + r.outputQty
            }
            m.queue.clear()
        }

        state.day++
        state.stats.daysPlayed++
        state.weather = forcedWeather ?: state.tomorrowWeather
        state.tomorrowWeather = rollWeather(rng("weather"))
        if (state.weather == Weather.RAIN) state.plots.forEach { if (it.tilled) it.watered = true }

        val priceRng = rng("prices")
        for (id in ItemDb.sellable) {
            val p = state.prices.getOrPut(id) { PriceState() }
            p.prevMult = p.mult
            val noise = (priceRng.nextFloat() + priceRng.nextFloat() + priceRng.nextFloat() - 1.5f) * 0.12f
            p.mult = (p.mult + noise + 0.2f * (1f - p.mult)).coerceIn(PRICE_MIN, PRICE_MAX)
        }
        state.soldToday.clear()
        state.hotItem = state.tomorrowHotItem
        state.tomorrowHotItem = rollHotItem(rng("hot"))

        var expired = 0
        state.orders.forEach { it.daysLeft-- }
        state.orders.removeAll { o -> (o.daysLeft <= 0).also { if (it) expired++ } }
        val newOrders = refillOrders()

        state.minutes = DAY_START.toDouble()
        state.energy = if (passedOut) PASS_OUT_ENERGY else MAX_ENERGY
        exhaustedSent = false

        val ripe = state.plots.count { isRipe(it) }
        return MorningReport(
            day = state.day,
            weather = state.weather,
            passedOut = passedOut,
            cropsGrew = grew,
            cropsRipe = ripe,
            cropsWithered = withered,
            products = products,
            hungry = hungry,
            arrived = arrived,
            machineGoods = machineGoods,
            ordersExpired = expired,
            newOrders = newOrders,
            hotItem = state.hotItem,
        )
    }
}

/** Inventory keys encode quality: "carrot", "carrot#1" (silver), "carrot#2" (gold). */
object ItemKey {
    fun of(id: String, quality: Int): String = if (quality <= 0) id else "$id#$quality"
    fun id(key: String): String = key.substringBefore('#')
    fun quality(key: String): Int = key.substringAfter('#', "0").toIntOrNull()?.coerceIn(0, 2) ?: 0
}
