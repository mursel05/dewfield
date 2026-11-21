package dev.mursal.dewfield.world

import dev.mursal.dewfield.model.AnimalState
import dev.mursal.dewfield.model.Category
import dev.mursal.dewfield.model.Cell
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.Gain
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.Msg
import dev.mursal.dewfield.model.Outcome
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

enum class Panel { SHOP_SEEDS, SHOP_FEED, SELL, WORKSHOP, ORDERS, MACHINE, COOK, TV, SLEEP }

/** Things the world asks the screen to show or do. The screen drains [World.events] every frame. */
sealed interface WorldEvent {
    data class Toast(val msg: Msg) : WorldEvent
    data class Float(val x: kotlin.Float, val y: kotlin.Float, val gain: Gain) : WorldEvent
    data class Sound(val id: String) : WorldEvent
    data class Open(val panel: Panel, val arg: String = "") : WorldEvent
    data class Travel(val to: String, val spawn: String) : WorldEvent
}

enum class PlotAction(val queueable: Boolean, val tool: String) {
    TILL(true, "hoe"), PLANT(true, "seed"), WATER(true, "can"), FERTILIZE(true, "seed"),
    HARVEST(true, "hand"), CLEAR(true, "hoe"), INFO(false, ""), LOCKED(false, ""),
}

sealed interface Task {
    data class Move(val cell: Cell) : Task
    data class Plot(val cell: Cell) : Task
    data class Use(val obj: WorldObject) : Task
    data class Pet(val animalId: Int) : Task
}

class Player {
    var x = 0f
    var y = 0f
    var facing = Facing.DOWN
    var moving = false
    /** Seconds spent walking, for the walk animation. */
    var walkTime = 0f
    /** Seconds left in the current tool animation, and which tool. */
    var actTime = 0f
    var actTool = ""
    val path = ArrayDeque<Pair<Float, Float>>()

    val cell: Cell get() = Cell(floor(x).toInt(), floor(y).toInt())

    companion object {
        const val SPEED = 3.6f
        const val ACT_TIME = 0.32f
    }
}

class AnimalActor(val id: Int, val kind: String, var x: Float, var y: Float, val pen: Pen) {
    var tx = x
    var ty = y
    var wait = 0f
    var faceRight = true
    var moving = false
    var animTime = 0f
    /** Held still while the farmer walks over to pet it. */
    var held = false
    /** Bounce timer after being petted. */
    var happy = 0f

    val cell: Cell get() = Cell(floor(x).toInt(), floor(y).toInt())

    val speed: Float get() = when (kind) {
        "chicken" -> 1.3f
        "sheep" -> 0.8f
        else -> 0.65f
    }

    /** Tap radius around the animal's feet, a bit bigger than the sprite for fat fingers. */
    val hitRadius: Float get() = if (kind == "chicken") 0.6f else 0.85f
}

/**
 * The play session's logic: the current location, the farmer, animals and the task queue.
 * Taps become tasks; tasks walk the farmer with [PathFinder] and then call [Farm] rules.
 * No rendering here, so it is tested headless.
 */
class World(val farm: Farm, private val rng: Random = Random(farm.state.seed xor 0x5DEECE66DL)) {
    lateinit var location: Location
        private set
    private lateinit var finder: PathFinder
    val player = Player()
    val animals = ArrayList<AnimalActor>()
    val events = ArrayDeque<WorldEvent>()

    var current: Task? = null
        private set
    val queue = ArrayDeque<Task>()
    /** Where the current walk ends (for the destination marker), or null. */
    var destination: Pair<Float, Float>? = null
        private set
    private var arrived = false
    private var waterRetry: Cell? = null

    val state get() = farm.state

    // ------------------------------------------------------------------ locations

    /** Enters a location at a spawn point, or at the saved position when [useSaved] is set and valid. */
    fun enter(id: String, spawnName: String?, useSaved: Boolean = false) {
        location = Locations.create(id)
        finder = PathFinder(location)
        state.location = id
        clearTasks()
        val spawn = location.spawns[spawnName] ?: location.spawns.values.first()
        player.x = spawn.cell.x + 0.5f
        player.y = spawn.cell.y + 0.5f
        player.facing = spawn.facing
        if (useSaved && state.playerX.isFinite() && state.playerY.isFinite()) {
            val c = Cell(floor(state.playerX).toInt(), floor(state.playerY).toInt())
            if (location.inBounds(c) && location.walkable(c) && location.exitAt(c) == null) {
                player.x = state.playerX
                player.y = state.playerY
                player.facing = Facing.entries.getOrElse(state.facing) { Facing.DOWN }
            }
        }
        player.moving = false
        player.actTime = 0f
        spawnAnimals()
    }

    /** Stores the farmer's position in the save state. */
    fun storePosition() {
        state.playerX = player.x
        state.playerY = player.y
        state.facing = player.facing.ordinal
    }

    private fun spawnAnimals() {
        animals.clear()
        if (location.id != Locations.FARM) return
        for (a in state.animals) {
            if (a.arriving) continue
            val pen = location.penFor(ItemDb.animal(a.kind).housing.name.lowercase()) ?: continue
            val x = pen.minX + 0.5f + rng.nextFloat() * (pen.maxX - pen.minX)
            val y = pen.minY + 0.5f + rng.nextFloat() * (pen.maxY - pen.minY)
            animals += AnimalActor(a.id, a.kind, x, y, pen).also { it.wait = rng.nextFloat() * 2f; it.faceRight = rng.nextBoolean() }
        }
    }

    /** Rebuilds animal actors after the night (new arrivals). */
    fun refreshAnimals() = spawnAnimals()

    // ------------------------------------------------------------------ input

    fun animalAt(wx: Float, wy: Float): AnimalActor? =
        animals.filter { hypot(it.x - wx, it.y + 0.3f - wy) < it.hitRadius }.minByOrNull { hypot(it.x - wx, it.y - wy) }

    /** What a tap on this field cell would do right now. */
    fun plotAction(cell: Cell): PlotAction? {
        val p = farm.plot(cell) ?: return null
        if (!farm.isPlotUnlocked(cell)) return PlotAction.LOCKED
        val selected = state.selected
        val fertilizer = selected == ItemDb.FERTILIZER && farm.count(ItemDb.FERTILIZER) > 0
        return when {
            p.dead -> PlotAction.CLEAR
            p.crop != null && farm.isRipe(p) -> PlotAction.HARVEST
            p.crop != null && !p.watered -> PlotAction.WATER
            fertilizer && p.tilled && !p.fertilized -> PlotAction.FERTILIZE
            p.crop != null -> PlotAction.INFO
            !p.tilled -> PlotAction.TILL
            selected != null && ItemDb.cropBySeed.containsKey(selected) && farm.count(selected) > 0 -> PlotAction.PLANT
            else -> PlotAction.INFO
        }
    }

    fun isQueued(cell: Cell): Boolean =
        queue.any { it is Task.Plot && it.cell == cell } || (current as? Task.Plot)?.cell == cell

    /** Plot cells waiting in the queue with the action they will get, for markers. */
    fun queuedPlots(): List<Pair<Cell, PlotAction>> = buildList {
        (current as? Task.Plot)?.let { t -> plotAction(t.cell)?.let { add(t.cell to it) } }
        queue.forEach { t -> if (t is Task.Plot) plotAction(t.cell)?.let { add(t.cell to it) } }
    }

    /** A single tap at a world position. */
    fun tap(wx: Float, wy: Float) {
        val cell = Cell(floor(wx).toInt(), floor(wy).toInt())
        animalAt(wx, wy)?.let { a ->
            replaceWith(Task.Pet(a.id))
            return
        }
        location.objectHit(wx, wy)?.let { o ->
            replaceWith(Task.Use(o))
            return
        }
        val action = plotAction(cell)
        if (action != null) {
            when {
                action == PlotAction.LOCKED -> toast(Msg("msg.field_locked_hint"))
                isQueued(cell) -> {
                    queue.removeAll { it is Task.Plot && it.cell == cell }
                    sound("tick")
                }
                action.queueable -> addPlot(cell)
                else -> plotInfo(cell)
            }
            return
        }
        if (location.inBounds(cell) && location.walkable(cell)) {
            replaceWith(Task.Move(cell))
        }
    }

    /** True if a drag starting here paints plot tasks. */
    fun canPaint(wx: Float, wy: Float): Boolean {
        val a = plotAction(Cell(floor(wx).toInt(), floor(wy).toInt()))
        return a != null && a.queueable
    }

    /** Adds the plot under a dragging finger to the queue. */
    fun paint(wx: Float, wy: Float) {
        val cell = Cell(floor(wx).toInt(), floor(wy).toInt())
        val a = plotAction(cell) ?: return
        if (a.queueable && !isQueued(cell)) addPlot(cell)
    }

    private fun addPlot(cell: Cell) {
        if (current != null && current !is Task.Plot) clearTasks()
        if (queue.size >= MAX_QUEUE) {
            toast(Msg("msg.queue_full"))
            return
        }
        queue += Task.Plot(cell)
        sound("tick")
    }

    private fun replaceWith(task: Task) {
        clearTasks()
        queue += task
    }

    fun clearTasks() {
        queue.clear()
        current?.let { if (it is Task.Pet) animals.firstOrNull { a -> a.id == it.animalId }?.held = false }
        current = null
        player.path.clear()
        destination = null
        waterRetry = null
    }

    // ------------------------------------------------------------------ update

    /** Advances the world. [clockRunning] is false while a panel is open or the screen fades. */
    fun update(dt: Float, clockRunning: Boolean) {
        if (clockRunning) farm.advanceTime(dt.toDouble())
        updateAnimals(dt)
        if (player.actTime > 0f) {
            player.actTime -= dt
            return
        }
        if (current == null) {
            val next = queue.removeFirstOrNull() ?: run {
                player.moving = false
                return
            }
            start(next)
        }
        if (current != null && player.path.isNotEmpty()) walk(dt) else if (current != null) arrive()
    }

    private fun goalsFor(task: Task): List<Cell> = when (task) {
        is Task.Move -> listOf(task.cell)
        is Task.Plot -> {
            val n4 = Location.NEIGHBORS4.map { task.cell + it }.filter { location.walkable(it) }
            n4.ifEmpty { listOf(task.cell) }
        }
        is Task.Use -> location.standCellsFor(task.obj)
        is Task.Pet -> {
            val a = animals.firstOrNull { it.id == task.animalId }
            if (a == null) emptyList() else {
                val c = a.cell
                (Location.NEIGHBORS4.map { c + it } + Location.NEIGHBORS4.map { c + it + it })
                    .filter { a.pen.contains(it) && location.walkable(it) }
            }
        }
    }

    private fun start(task: Task) {
        current = task
        arrived = false
        if (task is Task.Pet) animals.firstOrNull { it.id == task.animalId }?.held = true
        val goals = goalsFor(task)
        val path = finder.findPath(player.x, player.y, goals)
        if (path == null) {
            if (task !is Task.Plot) toast(Msg("msg.cant_reach"))
            finishTask()
            return
        }
        player.path.clear()
        val (fx, fy) = path.first()
        val alreadyThere = path.size == 1 && Cell(floor(fx).toInt(), floor(fy).toInt()) == player.cell
        if (!alreadyThere) player.path.addAll(path)
        destination = path.last()
    }

    private fun walk(dt: Float) {
        var budget = Player.SPEED * dt
        player.moving = true
        player.walkTime += dt
        while (budget > 0f && player.path.isNotEmpty()) {
            val (tx, ty) = player.path.first()
            val dx = tx - player.x
            val dy = ty - player.y
            val d = hypot(dx, dy)
            if (d > 1e-4f) player.facing = Facing.of(dx, dy)
            if (d <= budget) {
                player.x = tx
                player.y = ty
                budget -= d
                player.path.removeFirst()
            } else {
                player.x += dx / d * budget
                player.y += dy / d * budget
                budget = 0f
            }
            // Walking onto an exit leaves the area even if it was not the target.
            location.exitAt(player.cell)?.let { exit ->
                if (current !is Task.Use || (current as Task.Use).obj != exit || player.path.isEmpty()) {
                    travel(exit)
                    return
                }
            }
        }
        if (player.path.isEmpty()) {
            player.moving = false
            arrive()
        }
    }

    private fun arrive() {
        if (arrived) return
        arrived = true
        destination = null
        when (val t = current) {
            is Task.Move -> Unit
            is Task.Plot -> {
                face(t.cell.x + 0.5f, t.cell.y + 0.5f)
                doPlot(t.cell)
            }
            is Task.Use -> {
                face(t.obj.centerX, t.obj.y + 0.5f)
                use(t.obj)
            }
            is Task.Pet -> {
                val a = animals.firstOrNull { it.id == t.animalId }
                if (a != null) {
                    face(a.x, a.y)
                    a.held = false
                    petOrInspect(a)
                }
            }
            null -> Unit
        }
        finishTask()
    }

    private fun finishTask() {
        current = null
        player.path.clear()
        destination = null
    }

    private fun face(tx: Float, ty: Float) {
        val dx = tx - player.x
        val dy = ty - player.y
        if (hypot(dx, dy) > 0.05f) player.facing = Facing.of(dx, dy)
    }

    private fun animate(tool: String) {
        player.actTool = tool
        player.actTime = Player.ACT_TIME
    }

    // ------------------------------------------------------------------ actions

    private fun doPlot(cell: Cell) {
        val action = plotAction(cell) ?: return
        val fx = cell.x + 0.5f
        val fy = cell.y + 0.9f
        val result: Outcome = when (action) {
            PlotAction.TILL -> farm.till(cell).also { if (it.ok) sound("till") }
            PlotAction.PLANT -> {
                val seed = state.selected!!
                farm.plant(cell, seed).also {
                    if (it.ok) {
                        sound("plant")
                        // Planting waters the plot too when the can has water.
                        if (state.canWater > 0) farm.water(cell)
                    }
                }
            }
            PlotAction.WATER -> {
                if (state.canWater <= 0) {
                    refillThenWater(cell)
                    return
                }
                farm.water(cell).also { if (it.ok) sound("water") }
            }
            PlotAction.FERTILIZE -> farm.fertilize(cell).also { if (it.ok) sound("plant") }
            PlotAction.HARVEST -> farm.harvest(cell).also { if (it.ok) sound("harvest") }
            PlotAction.CLEAR -> farm.clear(cell).also { if (it.ok) sound("till") }
            PlotAction.INFO, PlotAction.LOCKED -> return
        }
        when (result) {
            is Outcome.Ok -> {
                animate(action.tool)
                result.gains.forEach { events += WorldEvent.Float(fx, fy, it) }
            }
            is Outcome.Fail -> {
                toast(result.msg)
                sound("error")
                // Out of energy or seeds: the rest of the queue would fail the same way.
                if (result.msg.key == "msg.too_tired" || result.msg.key == "msg.no_seeds") queue.clear()
            }
        }
    }

    /** The can ran dry mid-queue: walk to the well, refill, then come back to this plot. */
    private fun refillThenWater(cell: Cell) {
        val well = location.objects.firstOrNull { it.kind == ObjKind.WELL }
        if (well == null || waterRetry == cell) {
            toast(Msg("msg.can_empty"))
            queue.clear()
            return
        }
        waterRetry = cell
        toast(Msg("msg.going_to_well"))
        queue.addFirst(Task.Plot(cell))
        queue.addFirst(Task.Use(well))
    }

    private fun plotInfo(cell: Cell) {
        val p = farm.plot(cell) ?: return
        val crop = p.crop
        val msg = when {
            crop == null && p.tilled -> Msg("msg.pick_seed")
            crop == null -> Msg("msg.pick_seed")
            else -> {
                val q = farm.expectedQuality(p)
                Msg("msg.crop_info", Msg.item(ItemDb.crop(crop).produce), farm.daysLeft(p), Msg("quality.$q"),
                    Msg(if (p.watered) "msg.watered_yes" else "msg.watered_no"))
            }
        }
        toast(msg)
    }

    private fun use(o: WorldObject) {
        val farmX = o.centerX
        val farmY = o.y + o.h + 0.2f
        fun market(panel: Panel) {
            if (farm.isMarketOpen) {
                events += WorldEvent.Open(panel)
            } else {
                toast(Msg("msg.market_closed", hhmm(Farm.MARKET_OPEN), hhmm(Farm.MARKET_CLOSE)))
            }
        }
        when (o.kind) {
            ObjKind.HOUSE, ObjKind.DOOR, ObjKind.EXIT -> travel(o)
            ObjKind.BED -> events += WorldEvent.Open(Panel.SLEEP)
            ObjKind.TV -> events += WorldEvent.Open(Panel.TV)
            ObjKind.STOVE -> events += WorldEvent.Open(Panel.COOK)
            ObjKind.WELL -> {
                val r = farm.refillCan()
                if (r is Outcome.Ok) {
                    animate("can")
                    sound("water")
                    events += WorldEvent.Float(farmX, farmY, Gain("water", state.canWater))
                    waterRetry = null
                } else if (r is Outcome.Fail) toast(r.msg)
            }
            ObjKind.MILL, ObjKind.MACHINE -> {
                if (farm.owns(o.arg)) events += WorldEvent.Open(Panel.MACHINE, o.arg)
                else toast(Msg("msg.machine_not_owned", Msg.machine(o.arg)))
            }
            ObjKind.TROUGH -> {
                when (val r = farm.fillTrough(o.arg)) {
                    is Outcome.Ok -> {
                        animate("hand")
                        sound("pop")
                        r.msg?.let { toast(it) }
                    }
                    is Outcome.Fail -> {
                        val def = ItemDb.troughs.getValue(o.arg)
                        toast(Msg("msg.trough_status", Msg("trough.${o.arg}"), farm.trough(o.arg), def.capacity))
                        if (r.msg.key != "msg.trough_full") toast(r.msg)
                    }
                }
            }
            ObjKind.COOP, ObjKind.BARN -> {
                val housing = if (o.kind == ObjKind.COOP) dev.mursal.dewfield.model.Housing.COOP else dev.mursal.dewfield.model.Housing.BARN
                when (val r = farm.collectBasket(housing)) {
                    is Outcome.Ok -> {
                        animate("hand")
                        sound("harvest")
                        r.gains.forEachIndexed { i, g -> events += WorldEvent.Float(farmX, farmY + i * 0.45f, g) }
                    }
                    is Outcome.Fail -> toast(r.msg)
                }
            }
            ObjKind.SHOP_SEEDS -> market(Panel.SHOP_SEEDS)
            ObjKind.SHOP_FEED -> market(Panel.SHOP_FEED)
            ObjKind.BUYER -> market(Panel.SELL)
            ObjKind.WORKSHOP -> market(Panel.WORKSHOP)
            ObjKind.BOARD -> events += WorldEvent.Open(Panel.ORDERS, "board")
            ObjKind.SIGN -> toast(Msg(o.arg))
            ObjKind.DECOR -> Unit
        }
    }

    private fun petOrInspect(a: AnimalActor) {
        val s = farm.animalById(a.id) ?: return
        val r = farm.pet(a.id)
        if (r is Outcome.Ok) {
            animate("hand")
            sound("pet_${a.kind}")
            a.happy = 0.6f
            events += WorldEvent.Float(a.x, a.y + 1f, Gain("heart", 1))
        }
        toast(animalStatus(s))
    }

    fun animalStatus(s: AnimalState): Msg {
        val def = ItemDb.animal(s.kind)
        val feed = Msg("feed.${def.feed}")
        return when {
            s.hungryDays > 0 -> Msg("msg.animal_hungry", s.name, feed, Msg("trough.${ItemDb.troughByFeed.getValue(def.feed).id}"))
            else -> Msg("msg.animal_happy", s.name, s.mood, Farm.MAX_MOOD, feed)
        }
    }

    private fun travel(o: WorldObject) {
        val (to, spawn) = o.target ?: return
        clearTasks()
        player.moving = false
        sound("door")
        events += WorldEvent.Travel(to, spawn)
    }

    // ------------------------------------------------------------------ animals

    private fun updateAnimals(dt: Float) {
        val night = state.minutes >= 20 * 60
        for (a in animals) {
            a.animTime += dt
            if (a.happy > 0f) a.happy -= dt
            if (a.held || night) {
                a.moving = false
                continue
            }
            if (a.wait > 0f) {
                a.wait -= dt
                a.moving = false
                continue
            }
            val dx = a.tx - a.x
            val dy = a.ty - a.y
            val d = hypot(dx, dy)
            if (d < 0.05f) {
                a.wait = 1.5f + rng.nextFloat() * 4f
                a.tx = a.pen.minX + 0.5f + rng.nextFloat() * (a.pen.maxX - a.pen.minX)
                a.ty = a.pen.minY + 0.5f + rng.nextFloat() * (a.pen.maxY - a.pen.minY)
                a.moving = false
            } else {
                val step = minOf(d, a.speed * dt)
                a.x += dx / d * step
                a.y += dy / d * step
                if (kotlin.math.abs(dx) > 0.02f) a.faceRight = dx > 0
                a.moving = true
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun toast(msg: Msg) {
        events += WorldEvent.Toast(msg)
    }

    private fun sound(id: String) {
        events += WorldEvent.Sound(id)
    }

    /** Seed or fertilizer items the bottom bar offers, in shop order. */
    fun barItems(): List<String> = ItemDb.shopSeeds.filter { farm.count(it) > 0 }

    /** True if an item is something the bar can select. */
    fun isBarItem(id: String): Boolean = ItemDb.items[id]?.category.let { it == Category.SEED || it == Category.SUPPLY }

    companion object {
        const val MAX_QUEUE = 30
        fun hhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)
    }
}
