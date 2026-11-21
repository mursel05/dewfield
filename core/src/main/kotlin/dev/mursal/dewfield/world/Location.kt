package dev.mursal.dewfield.world

import dev.mursal.dewfield.model.Cell

/** Ground types. Each one is drawn with smooth, rounded edges against grass. */
enum class Terrain { GRASS, PATH, STONE, WATER, SOIL, FLOOR, WALL }

/** Decorations placed straight from the ASCII map. */
enum class Prop { NONE, TREE, PINE, BUSH, ROCK, FLOWERS, TUFT, FENCE, GATE }

enum class ObjKind {
    HOUSE, DOOR, EXIT, BED, TV, STOVE, WELL, MILL, TROUGH, COOP, BARN, MACHINE,
    SHOP_SEEDS, SHOP_FEED, BUYER, WORKSHOP, BOARD, SIGN, DECOR,
}

/**
 * A placed object. [x], [y] is the bottom-left cell of its footprint (y grows upward).
 * [standCells] are where the farmer stands to use it; empty means any walkable neighbor.
 * [overhang] is how many cells the sprite reaches above the footprint, for tap hit-testing.
 */
data class WorldObject(
    val id: String,
    val kind: ObjKind,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val solid: Boolean = true,
    val standCells: List<Cell> = emptyList(),
    val overhang: Float = 0f,
    /** Travel target for doors and exits: location id to spawn name. */
    val target: Pair<String, String>? = null,
    /** Free-form argument: machine id, trough id, decoration sprite, sign text key. */
    val arg: String = "",
) {
    fun covers(c: Cell) = c.x in x until x + w && c.y in y until y + h
    val footprint: List<Cell> get() = (x until x + w).flatMap { cx -> (y until y + h).map { cy -> Cell(cx, cy) } }
    val centerX: Float get() = x + w / 2f
    val centerY: Float get() = y + h / 2f

    /** True if a world point is on the footprint or the sprite above it. */
    fun hit(px: Float, py: Float): Boolean =
        px >= x && px < x + w && py >= y && py < y + h + overhang
}

/** A rectangle of cells animals wander in (inclusive bounds). */
data class Pen(val id: String, val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {
    fun contains(c: Cell) = c.x in minX..maxX && c.y in minY..maxY
}

data class Spawn(val cell: Cell, val facing: Facing)

enum class Facing(val dx: Int, val dy: Int) {
    DOWN(0, -1), UP(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    companion object {
        fun of(dx: Float, dy: Float): Facing =
            if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) (if (dx > 0) RIGHT else LEFT) else (if (dy > 0) UP else DOWN)
    }
}

/**
 * One area of the game (house, farm, market): terrain grid parsed from ASCII rows
 * (first row is the top of the map), props, objects, pens and spawn points.
 */
class Location(
    val id: String,
    rows: List<String>,
    val objects: List<WorldObject>,
    val spawns: Map<String, Spawn>,
    val pens: List<Pen> = emptyList(),
    val indoor: Boolean = false,
    /** Field blocks (farm only), in unlock order. */
    val fieldBlocks: List<List<Cell>> = emptyList(),
) {
    val width: Int = rows.first().length
    val height: Int = rows.size
    private val terrain = Array(width * height) { Terrain.GRASS }
    private val props = Array(width * height) { Prop.NONE }
    private val solid = BooleanArray(width * height)

    init {
        require(rows.all { it.length == width }) { "$id: ragged map rows" }
        rows.forEachIndexed { row, line ->
            val y = height - 1 - row
            line.forEachIndexed { x, ch ->
                val i = y * width + x
                val (t, p, s) = LEGEND[ch] ?: error("$id: unknown map char '$ch' at $x,$y")
                terrain[i] = t
                props[i] = p
                solid[i] = s
            }
        }
        for (o in objects) if (o.solid) o.footprint.forEach { if (inBounds(it)) solid[it.y * width + it.x] = true }
    }

    fun inBounds(c: Cell) = c.x in 0 until width && c.y in 0 until height
    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun terrain(x: Int, y: Int): Terrain = if (inBounds(x, y)) terrain[y * width + x] else Terrain.GRASS
    fun prop(x: Int, y: Int): Prop = if (inBounds(x, y)) props[y * width + x] else Prop.NONE
    fun isSolid(x: Int, y: Int): Boolean = !inBounds(x, y) || solid[y * width + x]
    fun walkable(c: Cell): Boolean = !isSolid(c.x, c.y)

    fun objectAt(c: Cell): WorldObject? = objects.firstOrNull { it.covers(c) }

    /** The object whose footprint or sprite contains a world point (front-most first). */
    fun objectHit(px: Float, py: Float): WorldObject? =
        objects.filter { it.kind != ObjKind.DECOR && it.hit(px, py) }.minByOrNull { it.y }

    fun exitAt(c: Cell): WorldObject? = objects.firstOrNull { it.kind == ObjKind.EXIT && it.covers(c) }

    /** Cells the farmer may stand on to use an object. */
    fun standCellsFor(o: WorldObject): List<Cell> {
        if (o.standCells.isNotEmpty()) return o.standCells.filter { walkable(it) }
        if (!o.solid) return o.footprint.filter { walkable(it) }
        val out = LinkedHashSet<Cell>()
        for (c in o.footprint) for (d in NEIGHBORS4) {
            val n = c + d
            if (!o.covers(n) && walkable(n)) out += n
        }
        return out.toList()
    }

    fun penFor(id: String): Pen? = pens.firstOrNull { it.id == id }

    companion object {
        val NEIGHBORS4 = listOf(Cell(1, 0), Cell(-1, 0), Cell(0, 1), Cell(0, -1))

        private data class L(val t: Terrain, val p: Prop, val s: Boolean)

        private val LEGEND: Map<Char, L> = mapOf(
            '.' to L(Terrain.GRASS, Prop.NONE, false),
            ',' to L(Terrain.GRASS, Prop.TUFT, false),
            '*' to L(Terrain.GRASS, Prop.FLOWERS, false),
            '=' to L(Terrain.PATH, Prop.NONE, false),
            ':' to L(Terrain.STONE, Prop.NONE, false),
            '~' to L(Terrain.WATER, Prop.NONE, true),
            'f' to L(Terrain.SOIL, Prop.NONE, false),
            'w' to L(Terrain.FLOOR, Prop.NONE, false),
            '#' to L(Terrain.WALL, Prop.NONE, true),
            'T' to L(Terrain.GRASS, Prop.TREE, true),
            't' to L(Terrain.GRASS, Prop.PINE, true),
            'b' to L(Terrain.GRASS, Prop.BUSH, true),
            'o' to L(Terrain.GRASS, Prop.ROCK, true),
            'F' to L(Terrain.GRASS, Prop.FENCE, true),
            'G' to L(Terrain.GRASS, Prop.GATE, false),
            'X' to L(Terrain.GRASS, Prop.NONE, true),
            'P' to L(Terrain.PATH, Prop.NONE, true),
            'Q' to L(Terrain.STONE, Prop.NONE, true),
            'W' to L(Terrain.FLOOR, Prop.NONE, true),
        )
    }
}
