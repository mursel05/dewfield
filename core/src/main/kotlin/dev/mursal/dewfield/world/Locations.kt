package dev.mursal.dewfield.world

import dev.mursal.dewfield.model.Cell
import dev.mursal.dewfield.model.FieldLayout

/**
 * The three areas of the game. Maps are ASCII (legend in [Location]); objects use
 * map coordinates with y growing upward from the bottom row.
 */
object Locations {
    const val HOUSE = "house"
    const val FARM = "farm"
    const val MARKET = "market"

    private val FARM_ROWS = listOf(
        "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
        "T......................................T",
        "T........T..............T....FFFFFFF...T",
        "T.XXXXX*...ffffff.ffffff..XXXF.....FT..T",
        "T.XXXXX.*..ffffff.ffffff..XXXF.....F...T",
        "T.XXXXX....ffffff.ffffff.....F.....F..,T",
        "T*..=......ffffff.ffffff.....FFGFXFF...T",
        "T..,=...X.,.............,..............T",
        "T...=......ffffff.ffffff.....,....*..T.T",
        "T.T.=.XXX..ffffff.ffffff......b*.......T",
        "T...=.XXX..ffffff.ffffff.ob,.....b..*..T",
        "T...=.XXX..ffffff.ffffff........,....X.T",
        "T..o====================================",
        "T...====================================",
        "TT....,..*.*..........*...,............T",
        "T.*.....b.T...T...*.........FFGFFXFXFF.T",
        "T...~~~.....b...o......XXXX.F........F.T",
        "T..~~~~~.*.....,.b..X..XXXX.F........F.T",
        "Tb.~~~~~~..............XXXX.F........FTT",
        "T...~~~~~....T....,.X.......F........F.T",
        "T....~~~...*................F........F.T",
        "T.T.......T.....T..*.T...*..FFFFFFFFFF.T",
        "T...........,..........................T",
        "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
    )

    private val MARKET_ROWS = listOf(
        "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
        "T............................T",
        "TT.....T.....T.....T.XXXXX.T.T",
        "T..QQQ..*QQQ..*QQQ..*XXXXX*..T",
        "T.*QQQ...QQQ...QQQ...XXXXX*.TT",
        "T.::::::::::::::::::::::::::.T",
        "T.b::::::::::::::::::::::::b.T",
        "T.::::::Q::::::::::::Q::::::.T",
        "T.:::::::::::QQQQ:::::::::::.T",
        "==:::::::::::QQQQ:::::::::::.T",
        "==:::::::::::QQQQ:::::::::::TT",
        "T.::::::::::::::::::::::::::.T",
        "T.::::::::::::::::::::::::::.T",
        "TT:::QQ::::QQ::::QQ:::::::::.T",
        "T.::::::Q::::::::::::Q::::::TT",
        "T.b::::::::::::::::::::::::b.T",
        "TT.,..,...,..,..,...,...,....T",
        "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
    )

    private val HOUSE_ROWS = listOf(
        "############",
        "############",
        "#WWwwwWwwWW#",
        "#WWwwwwwwww#",
        "#wwwwwwwwww#",
        "#wwwwWWwwww#",
        "#wwwwwwwwww#",
        "#WwwwwwwwwW#",
        "######w#####",
    )

    private fun block(x0: Int, y0: Int) = (x0 until x0 + 6).flatMap { x -> (y0 until y0 + 4).map { y -> Cell(x, y) } }

    /** Field blocks in unlock order: top-left, top-right, bottom-left, bottom-right. */
    val FIELD = FieldLayout(listOf(block(11, 17), block(18, 17), block(11, 12), block(18, 12)))

    fun farm() = Location(
        id = FARM,
        rows = FARM_ROWS,
        objects = listOf(
            WorldObject("house", ObjKind.HOUSE, 2, 18, 5, 3, standCells = listOf(Cell(4, 17)), overhang = 1.7f, target = HOUSE to "door"),
            WorldObject("well", ObjKind.WELL, 8, 16, overhang = 0.8f),
            WorldObject("mill", ObjKind.MILL, 6, 12, 3, 3, standCells = listOf(Cell(7, 11)), overhang = 2.4f, arg = "mill"),
            WorldObject("coop", ObjKind.COOP, 26, 19, 3, 2, standCells = listOf(Cell(27, 18)), overhang = 1.5f),
            WorldObject("hopper", ObjKind.TROUGH, 33, 17, standCells = listOf(Cell(33, 16)), overhang = 0.4f, arg = "hopper"),
            WorldObject("barn", ObjKind.BARN, 23, 5, 4, 3, standCells = listOf(Cell(24, 4), Cell(25, 4)), overhang = 1.8f),
            WorldObject("hay_rack", ObjKind.TROUGH, 33, 8, standCells = listOf(Cell(33, 9)), overhang = 0.4f, arg = "hay_rack"),
            WorldObject("clover_tray", ObjKind.TROUGH, 35, 8, standCells = listOf(Cell(35, 9)), overhang = 0.4f, arg = "clover_tray"),
            WorldObject("cheese_press", ObjKind.MACHINE, 20, 6, overhang = 0.6f, arg = "cheese_press"),
            WorldObject("loom", ObjKind.MACHINE, 20, 4, overhang = 0.6f, arg = "loom"),
            WorldObject("sign_market", ObjKind.SIGN, 37, 12, overhang = 0.6f, arg = "sign.market"),
            WorldObject("exit_market", ObjKind.EXIT, 39, 10, 1, 2, solid = false, target = MARKET to "farm_road"),
        ),
        spawns = mapOf(
            "house_door" to Spawn(Cell(4, 17), Facing.DOWN),
            "market_road" to Spawn(Cell(38, 11), Facing.LEFT),
        ),
        pens = listOf(Pen("coop", 30, 18, 34, 20), Pen("barn", 29, 3, 36, 7)),
        fieldBlocks = FIELD.blocks,
    )

    fun market() = Location(
        id = MARKET,
        rows = MARKET_ROWS,
        objects = listOf(
            WorldObject("stall_seeds", ObjKind.SHOP_SEEDS, 3, 13, 3, 2, standCells = listOf(Cell(4, 12), Cell(3, 12), Cell(5, 12)), overhang = 1.6f),
            WorldObject("stall_feed", ObjKind.SHOP_FEED, 9, 13, 3, 2, standCells = listOf(Cell(10, 12), Cell(9, 12), Cell(11, 12)), overhang = 1.6f),
            WorldObject("stall_produce", ObjKind.BUYER, 15, 13, 3, 2, standCells = listOf(Cell(16, 12), Cell(15, 12), Cell(17, 12)), overhang = 1.6f),
            WorldObject("workshop", ObjKind.WORKSHOP, 21, 13, 5, 3, standCells = listOf(Cell(23, 12)), overhang = 1.6f),
            WorldObject("board", ObjKind.BOARD, 5, 4, 2, 1, standCells = listOf(Cell(5, 3), Cell(6, 3)), overhang = 1.3f),
            WorldObject("fountain", ObjKind.DECOR, 13, 7, 4, 3, overhang = 0.9f, arg = "fountain"),
            WorldObject("lamp1", ObjKind.DECOR, 8, 10, overhang = 1.6f, arg = "lamp"),
            WorldObject("lamp2", ObjKind.DECOR, 21, 10, overhang = 1.6f, arg = "lamp"),
            WorldObject("lamp3", ObjKind.DECOR, 8, 3, overhang = 1.6f, arg = "lamp"),
            WorldObject("lamp4", ObjKind.DECOR, 21, 3, overhang = 1.6f, arg = "lamp"),
            WorldObject("bench1", ObjKind.DECOR, 11, 4, 2, 1, arg = "bench"),
            WorldObject("bench2", ObjKind.DECOR, 17, 4, 2, 1, arg = "bench"),
            WorldObject("exit_farm", ObjKind.EXIT, 0, 7, 1, 2, solid = false, target = FARM to "market_road"),
        ),
        spawns = mapOf("farm_road" to Spawn(Cell(1, 8), Facing.RIGHT)),
    )

    fun house() = Location(
        id = HOUSE,
        rows = HOUSE_ROWS,
        objects = listOf(
            WorldObject("rug", ObjKind.DECOR, 3, 2, 6, 3, solid = false, arg = "rug"),
            WorldObject("bed", ObjKind.BED, 1, 5, 2, 2, standCells = listOf(Cell(3, 5), Cell(3, 6), Cell(1, 4), Cell(2, 4)), overhang = 0.3f),
            WorldObject("tv", ObjKind.TV, 6, 6, standCells = listOf(Cell(6, 5)), overhang = 0.9f),
            WorldObject("stove", ObjKind.STOVE, 9, 6, 2, 1, standCells = listOf(Cell(9, 5), Cell(10, 5)), overhang = 0.9f),
            WorldObject("table", ObjKind.DECOR, 5, 3, 2, 1, overhang = 0.4f, arg = "table"),
            WorldObject("plant", ObjKind.DECOR, 10, 1, overhang = 0.8f, arg = "plant"),
            WorldObject("shelf", ObjKind.DECOR, 1, 1, overhang = 1.2f, arg = "shelf"),
            WorldObject("window1", ObjKind.DECOR, 3, 7, solid = false, arg = "window"),
            WorldObject("window2", ObjKind.DECOR, 8, 7, solid = false, arg = "window"),
            WorldObject("door", ObjKind.EXIT, 6, 0, solid = false, target = FARM to "house_door"),
        ),
        spawns = mapOf(
            "door" to Spawn(Cell(6, 1), Facing.UP),
            "bed" to Spawn(Cell(3, 5), Facing.DOWN),
        ),
        indoor = true,
    )

    fun create(id: String): Location = when (id) {
        HOUSE -> house()
        FARM -> farm()
        MARKET -> market()
        else -> error("Unknown location $id")
    }

    val ALL = listOf(HOUSE, FARM, MARKET)
}
