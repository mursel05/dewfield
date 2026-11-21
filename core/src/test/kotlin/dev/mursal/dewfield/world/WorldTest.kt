package dev.mursal.dewfield.world

import dev.mursal.dewfield.model.Cell
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.Housing
import dev.mursal.dewfield.model.Msg
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapTest {

    private fun reachable(loc: Location, from: Cell): Set<Cell> {
        val seen = HashSet<Cell>()
        val todo = ArrayDeque(listOf(from))
        while (todo.isNotEmpty()) {
            val c = todo.removeFirst()
            if (!seen.add(c)) continue
            for (d in Location.NEIGHBORS4) {
                val n = c + d
                if (n !in seen && loc.walkable(n)) todo += n
            }
        }
        return seen
    }

    @Test
    fun `every object, exit, pen and field cell can be reached from every spawn`() {
        for (id in Locations.ALL) {
            val loc = Locations.create(id)
            for ((name, spawn) in loc.spawns) {
                assertTrue(loc.walkable(spawn.cell), "$id spawn $name is walkable")
                val r = reachable(loc, spawn.cell)
                for (o in loc.objects) {
                    if (o.kind == ObjKind.DECOR) continue
                    val stands = loc.standCellsFor(o)
                    assertTrue(stands.isNotEmpty(), "$id ${o.id} has stand cells")
                    assertTrue(stands.any { it in r }, "$id ${o.id} reachable from $name")
                }
                for (pen in loc.pens) {
                    assertTrue(Cell(pen.minX, pen.minY) in r, "$id pen ${pen.id} reachable from $name")
                }
                for (block in loc.fieldBlocks) for (c in block) {
                    assertTrue(c in r, "$id field cell $c reachable from $name")
                }
            }
        }
    }

    @Test
    fun `doors and exits lead to spawns that exist`() {
        for (id in Locations.ALL) {
            val loc = Locations.create(id)
            loc.objects.mapNotNull { it.target }.forEach { (to, spawn) ->
                assertNotNull(Locations.create(to).spawns[spawn], "$id -> $to/$spawn")
            }
        }
    }

    @Test
    fun `field blocks match the farm map soil`() {
        val farm = Locations.farm()
        assertEquals(4, Locations.FIELD.blocks.size)
        Locations.FIELD.allCells.forEach { assertEquals(Terrain.SOIL, farm.terrain(it.x, it.y), "$it") }
        var soil = 0
        for (x in 0 until farm.width) for (y in 0 until farm.height) if (farm.terrain(x, y) == Terrain.SOIL) soil++
        assertEquals(96, soil)
    }

    @Test
    fun `pens are enclosed so animals stay inside`() {
        val farm = Locations.farm()
        for (pen in farm.pens) {
            for (x in pen.minX - 1..pen.maxX + 1) for (y in pen.minY - 1..pen.maxY + 1) {
                val border = x == pen.minX - 1 || x == pen.maxX + 1 || y == pen.minY - 1 || y == pen.maxY + 1
                if (!border) assertTrue(farm.walkable(Cell(x, y)), "pen ${pen.id} interior $x,$y open")
            }
        }
    }
}

class PathFinderTest {
    private val open = listOf(
        ".....",
        ".###.",
        ".#...",
        ".#.#.",
        "...#.",
    )
    private val walk = { x: Int, y: Int -> open[4 - y][x] != '#' }

    @Test
    fun `finds the shortest way around walls`() {
        val pf = PathFinder(walk, 5, 5)
        val cells = assertNotNull(pf.findCells(Cell(0, 0), listOf(Cell(2, 1))))
        assertEquals(Cell(0, 0), cells.first())
        assertEquals(Cell(2, 1), cells.last())
        cells.zipWithNext().forEach { (a, b) -> assertTrue(kotlin.math.abs(a.x - b.x) <= 1 && kotlin.math.abs(a.y - b.y) <= 1) }
        cells.forEach { assertTrue(walk(it.x, it.y)) }
    }

    @Test
    fun `never cuts a wall corner diagonally`() {
        val pf = PathFinder(walk, 5, 5)
        val cells = assertNotNull(pf.findCells(Cell(0, 4), listOf(Cell(4, 0))))
        cells.zipWithNext().forEach { (a, b) ->
            if (a.x != b.x && a.y != b.y) {
                assertTrue(walk(b.x, a.y) && walk(a.x, b.y), "diagonal $a->$b squeezes past a wall")
            }
        }
    }

    @Test
    fun `unreachable goals return null`() {
        val boxed = listOf("...", "###", "...")
        val pf = PathFinder({ x, y -> boxed[2 - y][x] != '#' }, 3, 3)
        assertNull(pf.findCells(Cell(0, 0), listOf(Cell(2, 2))))
    }

    @Test
    fun `smoothing walks straight across open ground`() {
        val pf = PathFinder({ _, _ -> true }, 20, 20)
        val path = assertNotNull(pf.findPath(0.5f, 0.5f, listOf(Cell(15, 9))))
        assertEquals(1, path.size)
        assertEquals(15.5f to 9.5f, path.last())
    }

    @Test
    fun `picks the nearest of several goals`() {
        val pf = PathFinder({ _, _ -> true }, 20, 20)
        val cells = assertNotNull(pf.findCells(Cell(0, 0), listOf(Cell(19, 19), Cell(3, 0))))
        assertEquals(Cell(3, 0), cells.last())
    }
}

class WorldTest {

    private fun world(): World {
        val farm = Farm.newGame(5L, Locations.FIELD)
        farm.state.minutes = 9.0 * 60
        return World(farm).also { it.enter(Locations.FARM, "house_door") }
    }

    private fun World.run(seconds: Float = 30f) {
        var t = 0f
        while (t < seconds) {
            update(1 / 30f, clockRunning = false)
            t += 1 / 30f
            if (current == null && queue.isEmpty() && player.actTime <= 0f) break
        }
    }

    private fun World.drain(): List<WorldEvent> = events.toList().also { events.clear() }

    @Test
    fun `tapping a plot walks over and tills it`() {
        val w = world()
        val cell = Cell(12, 18)
        w.tap(12.5f, 18.5f)
        assertEquals(PlotAction.TILL, w.plotAction(cell))
        w.run()
        assertTrue(w.farm.plot(cell)!!.tilled)
        assertTrue(hypot(w.player.x - 12.5f, w.player.y - 18.5f) < 1.6f, "farmer stands next to the plot")
        assertEquals(Farm.MAX_ENERGY - Farm.COST_TILL, w.state.energy)
    }

    private fun hypot(a: Float, b: Float) = kotlin.math.hypot(a, b)

    @Test
    fun `dragging across plots queues them all and tapping a queued plot removes it`() {
        val w = world()
        for (x in 11..16) w.paint(x + 0.5f, 20.5f)
        assertEquals(6, w.queuedPlots().size)
        w.tap(16.5f, 20.5f)
        assertEquals(5, w.queuedPlots().size)
        w.run(60f)
        for (x in 11..15) assertTrue(w.farm.plot(Cell(x, 20))!!.tilled, "x=$x")
        assertFalse(w.farm.plot(Cell(16, 20))!!.tilled)
    }

    @Test
    fun `planting also waters, and an empty can sends the farmer to the well`() {
        val w = world()
        val a = Cell(11, 17)
        val b = Cell(12, 17)
        w.farm.till(a)
        w.farm.till(b)
        w.farm.select("radish_seeds")
        w.tap(11.5f, 17.5f)
        w.run()
        val pa = w.farm.plot(a)!!
        assertEquals("radish", pa.crop)
        assertTrue(pa.watered)

        w.farm.plant(b, "radish_seeds")
        w.state.canWater = 0
        w.tap(12.5f, 17.5f)
        w.run(60f)
        assertTrue(w.farm.plot(b)!!.watered, "watered after the well trip")
        assertEquals(w.farm.canCapacity - 1, w.state.canWater)
        assertTrue(w.drain().any { it is WorldEvent.Toast && it.msg.key == "msg.going_to_well" })
    }

    @Test
    fun `tapping a growing crop shows info without walking`() {
        val w = world()
        val c = Cell(13, 19)
        w.farm.till(c)
        w.farm.plant(c, "radish_seeds")
        w.farm.water(c)
        w.tap(13.5f, 19.5f)
        assertNull(w.current)
        assertTrue(w.queue.isEmpty())
        val toast = w.drain().filterIsInstance<WorldEvent.Toast>().single()
        assertEquals("msg.crop_info", toast.msg.key)
    }

    @Test
    fun `locked field blocks explain how to unlock`() {
        val w = world()
        w.tap(19.5f, 18.5f)
        assertEquals("msg.field_locked_hint", (w.drain().single() as WorldEvent.Toast).msg.key)
    }

    @Test
    fun `walking to the road exit travels to the market`() {
        val w = world()
        w.tap(39.5f, 10.5f)
        w.run(60f)
        val travel = w.drain().filterIsInstance<WorldEvent.Travel>().single()
        assertEquals(WorldEvent.Travel(Locations.MARKET, "farm_road"), travel)
    }

    @Test
    fun `the house door, bed and market stalls open the right things`() {
        val w = world()
        w.tap(4.5f, 19.5f) // house wall/roof area
        w.run()
        assertEquals(WorldEvent.Travel(Locations.HOUSE, "door"), w.drain().filterIsInstance<WorldEvent.Travel>().single())

        w.enter(Locations.HOUSE, "door")
        w.tap(1.5f, 5.5f)
        w.run()
        assertTrue(w.drain().contains(WorldEvent.Open(Panel.SLEEP)))

        w.enter(Locations.MARKET, "farm_road")
        w.state.minutes = 6.5 * 60
        w.tap(4.5f, 13.5f)
        w.run()
        assertTrue(w.drain().any { it is WorldEvent.Toast && it.msg.key == "msg.market_closed" })
        w.state.minutes = 10.0 * 60
        w.tap(4.5f, 13.5f)
        w.run()
        assertTrue(w.drain().contains(WorldEvent.Open(Panel.SHOP_SEEDS)))
        w.tap(16.5f, 13.5f)
        w.run()
        assertTrue(w.drain().contains(WorldEvent.Open(Panel.SELL)))
        w.tap(5.5f, 4.5f)
        w.run()
        assertTrue(w.drain().contains(WorldEvent.Open(Panel.ORDERS, "board")))
    }

    @Test
    fun `filling the hopper and collecting eggs at the coop`() {
        val w = world()
        w.tap(33.5f, 17.5f)
        w.run()
        assertEquals(14, w.farm.trough("hopper"), "4 already in + 10 grain from the bag")
        assertEquals(0, w.farm.count("grain"))
        w.farm.basket(Housing.COOP)["egg#1"] = 2
        w.tap(27.5f, 19.5f)
        w.run()
        assertEquals(2, w.farm.count("egg#1"))
        val float = w.drain().filterIsInstance<WorldEvent.Float>().single()
        assertEquals(dev.mursal.dewfield.model.Gain("egg#1", 2), float.gain)
    }

    @Test
    fun `petting a chicken walks into the yard through the gate`() {
        val w = world()
        val hen = w.animals.first()
        val before = w.farm.animalById(hen.id)!!.mood
        w.tap(hen.x, hen.y)
        assertTrue(w.queue.single() is Task.Pet)
        w.run(60f)
        assertEquals(before + 1, w.farm.animalById(hen.id)!!.mood)
        assertTrue(hen.pen.contains(w.player.cell), "farmer ended inside the yard: ${w.player.cell}")
    }

    @Test
    fun `machines that are not bought say where to get them`() {
        val w = world()
        w.tap(20.5f, 6.5f)
        w.run()
        val t = w.drain().filterIsInstance<WorldEvent.Toast>().single()
        assertEquals(Msg("msg.machine_not_owned", Msg.machine("cheese_press")), t.msg)
    }

    @Test
    fun `running out of energy stops the queue`() {
        val w = world()
        w.state.energy = 4
        for (x in 11..14) w.paint(x + 0.5f, 19.5f)
        w.run(60f)
        assertEquals(1, (11..14).count { w.farm.plot(Cell(it, 19))!!.tilled })
        assertTrue(w.queue.isEmpty())
    }

    @Test
    fun `saved position is restored when valid`() {
        val w = world()
        w.player.x = 30.5f
        w.player.y = 12.5f
        w.storePosition()
        w.enter(Locations.FARM, null, useSaved = true)
        assertEquals(30.5f, w.player.x)
        w.state.playerX = 2.5f
        w.state.playerY = 19.5f // inside the house walls
        w.enter(Locations.FARM, "house_door", useSaved = true)
        assertEquals(4.5f, w.player.x)
    }
}
