package dev.mursal.dewfield.world

import dev.mursal.dewfield.model.Cell
import dev.mursal.dewfield.model.FieldLayout

/**
 * The three areas of the game. Maps are ASCII (legend in [Location]); objects use
 * map coordinates with y growing upward from the bottom row.
 *
 * The game is played in portrait, so the outdoor maps are taller than they are wide: the farm
 * stacks the animals, the home and the field from top to bottom, and the market is a narrow
 * square with the stalls along its north side.
 */
object Locations {
    const val HOUSE = "house"
    const val FARM = "farm"
    const val MARKET = "market"

    private val FARM_ROWS = listOf(
        "TTTTTTTTTTTTTTTTTTTTTT",
        "T................,...T",
        "T..,....FFFFFFF......T",
        "T....XXXF.....F*..t..T",
        "T....XXXF.....F......T",
        "T.t.....F.....F.t...*T",
        "T.......FFGFXFF....T.T",
        "T....*...............T",
        "T.,.........,......*.T",
        "T.......FFFFFFFFFF...T",
        "T...,...F........F...T",
        "T*......F........F.X.T",
        "T...XXXXF........F...T",
        "T...XXXXF........F...T",
        "T...XXXXF........F.X.T",
        "T.......FFGFFXFXFF...T",
        "T.b.................,T",
        "T......,.............T",
        "T..........*......,..T",
        "T.......,........b...T",
        "T.XXXXX......XXX....,T",
        "T.XXXXX......XXX.....T",
        "T*XXXXX..*...XXX.....T",
        "T...=..............X.T",
        "T.====================",
        "T.====================",
        "T....................T",
        "T..o..ffff.ffff.....,T",
        "T,....ffff.ffff...*..T",
        "T.....ffff.ffff....T.T",
        "T..X..ffff.ffff..,...T",
        "T.....ffff.ffff......T",
        "T.....ffff.ffff..~~..T",
        "T.,.............~~~~.T",
        "T*....ffff.ffff.~~~~~T",
        "T.....ffff.ffff.~~~~~T",
        "T...*.ffff.ffff..~~~~T",
        "T.....ffff.ffff..~~~.T",
        "T.b...ffff.ffff......T",
        "T.....ffff.ffff.*...bT",
        "T...,.....*....,.....T",
        "TTTTTTTTTTTTTTTTTTTTTT",
    )

    private val MARKET_ROWS = listOf(
        "TTTTTTTTTTTTTTTT",
        "T.....,.....*..T",
        "T,......b.,...bT",
        "Tb..*..........T",
        "T:QQQ:QQQ:QQQ::T",
        "T:QQQ:QQQ:QQQ::T",
        "T::::::::::::::T",
        "T::::::::::::::T",
        "TQ::::::::::::QT",
        "T::::::::::::::T",
        "T::::::::::::::T",
        "T::::::::::::::T",
        "T:QQ:::::QQQQQ:T",
        "T::::::::QQQQQ:T",
        "T::::::::QQQQQ:T",
        "T::::::::::::::T",
        "T::QQQQ::::::::T",
        "T::QQQQ::::::::T",
        "T::QQQQ::::QQ::T",
        "T::::::::::::::T",
        "==:::::::::::::T",
        "==:::::::::::::T",
        "T::::::::QQ::::T",
        "T::::::::::::::T",
        "TQ::::::::::::QT",
        "T::::::::::::::T",
        "T..*.......*...T",
        "T,.......,....bT",
        "T....,.......,.T",
        "TTTTTTTTTTTTTTTT",
    )

    /**
     * The house is tall and narrow for portrait: a bed nook at the top left, a kitchen nook on the
     * right under its own lower wall (so the stove has a wall behind it), the living room below.
     */
    private val HOUSE_ROWS = listOf(
        "##########",
        "##########",
        "#WWwWW####",
        "#WWwww####",
        "#wwwww####",
        "#wwwww####",
        "#wwwwwWWw#",
        "#wwwwwwww#",
        "#wwwwwwww#",
        "#wwwwwwww#",
        "#wwwWWwww#",
        "#wwwwwwww#",
        "#wwwwwwww#",
        "#WwwwwwwW#",
        "#####w####",
    )

    /** A field block: 4 columns by 6 rows of plots, so two blocks side by side fit a portrait screen. */
    private fun block(x0: Int, y0: Int) = (x0 until x0 + 4).flatMap { x -> (y0 until y0 + 6).map { y -> Cell(x, y) } }

    /** Field blocks in unlock order: top-left, top-right, bottom-left, bottom-right. */
    val FIELD = FieldLayout(listOf(block(6, 9), block(11, 9), block(6, 2), block(11, 2)))

    /**
     * The landscape field of save version 2 (6x4 blocks). Same block order and size as [FIELD],
     * so old saves move each plot to the cell with the same index in the new layout.
     */
    val FIELD_V2 = FieldLayout(
        listOf(Cell(11, 17), Cell(18, 17), Cell(11, 12), Cell(18, 12)).map { o ->
            (o.x until o.x + 6).flatMap { x -> (o.y until o.y + 4).map { y -> Cell(x, y) } }
        },
    )

    fun farm() = Location(
        id = FARM,
        rows = FARM_ROWS,
        objects = listOf(
            WorldObject("house", ObjKind.HOUSE, 2, 19, 5, 3, standCells = listOf(Cell(4, 18)), overhang = 1.7f, target = HOUSE to "door"),
            WorldObject("well", ObjKind.WELL, 3, 11, overhang = 0.8f),
            WorldObject("mill", ObjKind.MILL, 13, 19, 3, 3, standCells = listOf(Cell(14, 18)), overhang = 2.4f, arg = "mill"),
            WorldObject("coop", ObjKind.COOP, 5, 37, 3, 2, standCells = listOf(Cell(6, 36)), overhang = 1.5f),
            WorldObject("hopper", ObjKind.TROUGH, 12, 35, standCells = listOf(Cell(12, 34)), overhang = 0.4f, arg = "hopper"),
            WorldObject("barn", ObjKind.BARN, 4, 27, 4, 3, standCells = listOf(Cell(5, 26), Cell(6, 26)), overhang = 1.8f),
            WorldObject("hay_rack", ObjKind.TROUGH, 13, 26, standCells = listOf(Cell(13, 25)), overhang = 0.4f, arg = "hay_rack"),
            WorldObject("clover_tray", ObjKind.TROUGH, 15, 26, standCells = listOf(Cell(15, 25)), overhang = 0.4f, arg = "clover_tray"),
            WorldObject("cheese_press", ObjKind.MACHINE, 19, 27, overhang = 0.6f, arg = "cheese_press"),
            WorldObject("loom", ObjKind.MACHINE, 19, 30, overhang = 0.6f, arg = "loom"),
            WorldObject("sign_market", ObjKind.SIGN, 19, 18, overhang = 0.6f, arg = "sign.market"),
            WorldObject("exit_market", ObjKind.EXIT, 21, 16, 1, 2, solid = false, target = MARKET to "farm_road"),
            // A bench above the pond, facing the water: the best seat for the sunset.
            WorldObject("bench3", ObjKind.BENCH, 17, 10, 2, 1, arg = "bench"),
        ),
        spawns = mapOf(
            "house_door" to Spawn(Cell(4, 18), Facing.DOWN),
            "market_road" to Spawn(Cell(20, 17), Facing.LEFT),
        ),
        pens = listOf(Pen("coop", 9, 36, 13, 38), Pen("barn", 9, 27, 16, 31)),
        fieldBlocks = FIELD.blocks,
    )

    fun market() = Location(
        id = MARKET,
        rows = MARKET_ROWS,
        objects = listOf(
            WorldObject("stall_seeds", ObjKind.SHOP_SEEDS, 2, 24, 3, 2, standCells = listOf(Cell(3, 23), Cell(2, 23), Cell(4, 23)), overhang = 1.6f),
            WorldObject("stall_feed", ObjKind.SHOP_FEED, 6, 24, 3, 2, standCells = listOf(Cell(7, 23), Cell(6, 23), Cell(8, 23)), overhang = 1.6f),
            WorldObject("stall_produce", ObjKind.BUYER, 10, 24, 3, 2, standCells = listOf(Cell(11, 23), Cell(10, 23), Cell(12, 23)), overhang = 1.6f),
            WorldObject("workshop", ObjKind.WORKSHOP, 9, 15, 5, 3, standCells = listOf(Cell(11, 14)), overhang = 1.6f),
            WorldObject("board", ObjKind.BOARD, 9, 7, 2, 1, standCells = listOf(Cell(9, 6), Cell(10, 6)), overhang = 1.3f),
            WorldObject("fountain", ObjKind.FOUNTAIN, 3, 11, 4, 3, overhang = 0.9f, arg = "fountain"),
            WorldObject("lamp1", ObjKind.DECOR, 1, 21, overhang = 1.6f, arg = "lamp"),
            WorldObject("lamp2", ObjKind.DECOR, 14, 21, overhang = 1.6f, arg = "lamp"),
            WorldObject("lamp3", ObjKind.DECOR, 1, 5, overhang = 1.6f, arg = "lamp"),
            WorldObject("lamp4", ObjKind.DECOR, 14, 5, overhang = 1.6f, arg = "lamp"),
            WorldObject("bench1", ObjKind.BENCH, 2, 17, 2, 1, arg = "bench"),
            WorldObject("bench2", ObjKind.BENCH, 11, 11, 2, 1, arg = "bench"),
            WorldObject("exit_farm", ObjKind.EXIT, 0, 8, 1, 2, solid = false, target = FARM to "market_road"),
        ),
        spawns = mapOf("farm_road" to Spawn(Cell(1, 8), Facing.RIGHT)),
    )

    fun house() = Location(
        id = HOUSE,
        rows = HOUSE_ROWS,
        objects = listOf(
            WorldObject("rug", ObjKind.DECOR, 2, 3, 6, 3, solid = false, arg = "rug"),
            WorldObject("bed", ObjKind.BED, 1, 11, 2, 2, standCells = listOf(Cell(3, 11), Cell(3, 12), Cell(1, 10), Cell(2, 10)), overhang = 0.3f),
            WorldObject("shelf", ObjKind.SHELF, 4, 12, standCells = listOf(Cell(4, 11)), overhang = 1.2f, arg = "shelf"),
            WorldObject("tv", ObjKind.TV, 5, 12, standCells = listOf(Cell(5, 11)), overhang = 0.9f),
            WorldObject("stove", ObjKind.STOVE, 6, 8, 2, 1, standCells = listOf(Cell(6, 7), Cell(7, 7)), overhang = 0.9f),
            WorldObject("table", ObjKind.DECOR, 4, 4, 2, 1, overhang = 0.4f, arg = "table"),
            WorldObject("plant", ObjKind.DECOR, 1, 1, overhang = 0.8f, arg = "plant"),
            WorldObject("plant2", ObjKind.DECOR, 8, 1, overhang = 0.8f, arg = "plant"),
            WorldObject("window1", ObjKind.DECOR, 3, 13, solid = false, arg = "window"),
            WorldObject("window2", ObjKind.DECOR, 8, 9, solid = false, arg = "window"),
            WorldObject("door", ObjKind.EXIT, 5, 0, solid = false, target = FARM to "house_door"),
        ),
        spawns = mapOf(
            "door" to Spawn(Cell(5, 1), Facing.UP),
            "bed" to Spawn(Cell(3, 11), Facing.DOWN),
        ),
        indoor = true,
    )

    /**
     * Places where a sparkle may turn up on the ground, one a day at most (see `Leisure.sparkleIndex`):
     * quiet corners by rocks, bushes, flowers and the pond, never on a plot, a pen or a path.
     */
    val SPARKLES: List<Pair<String, Cell>> = listOf(
        FARM to Cell(2, 14),
        FARM to Cell(19, 40),
        FARM to Cell(19, 3),
        FARM to Cell(2, 24),
        FARM to Cell(20, 23),
        FARM to Cell(1, 8),
        FARM to Cell(17, 34),
        MARKET to Cell(1, 28),
        MARKET to Cell(12, 2),
        MARKET to Cell(13, 27),
    )

    fun create(id: String): Location = when (id) {
        HOUSE -> house()
        FARM -> farm()
        MARKET -> market()
        else -> error("Unknown location $id")
    }

    val ALL = listOf(HOUSE, FARM, MARKET)
}
