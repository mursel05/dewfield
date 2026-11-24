package dev.mursal.dewfield.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import dev.mursal.dewfield.gfx.Assets
import dev.mursal.dewfield.model.Cell
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.PlotState
import dev.mursal.dewfield.model.Weather
import dev.mursal.dewfield.world.Facing
import dev.mursal.dewfield.world.Location
import dev.mursal.dewfield.world.Locations
import dev.mursal.dewfield.world.ObjKind
import dev.mursal.dewfield.world.Player
import dev.mursal.dewfield.world.PlotAction
import dev.mursal.dewfield.world.Prop
import dev.mursal.dewfield.world.Terrain
import dev.mursal.dewfield.world.World
import dev.mursal.dewfield.world.WorldObject
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a [Location] and everything on it, in tile units:
 * grass, dual-grid terrain with rounded edges, ground decals and plots, then every standing
 * thing (buildings, trees, fences, crops, animals, the farmer) sorted back to front by the
 * y of its feet, then particles. Night lighting is a separate pass in [Lighting].
 */
class WorldRenderer(private val assets: Assets, private val world: World) {
    val effects = Effects(assets)
    var time = 0f
        private set

    private var built: Location? = null
    private var width = 0
    private var height = 0
    private lateinit var base: Array<TextureRegion?>
    private val layers = ArrayList<Array<TextureRegion?>>()
    private val statics = ArrayList<Spr>()
    private val decals = ArrayList<Spr>()
    private val lights = ArrayList<Light>()
    private val pool = SprPool()
    private val frame = ArrayList<Spr>(512)
    private val bySort = Comparator<Spr> { a, b -> b.sort.compareTo(a.sort) }

    private var smokeAcc = 0f
    private var sprayAcc = 0f
    private var twinkleAcc = 0f
    private var rippleAcc = 0f
    private var sparkleAcc = 0f

    /** Water cells of the map, for ripples, and the top of the fountain's spout, for its spray. */
    private val waterCells = ArrayList<Cell>()
    private var spoutX = Float.NaN
    private var spoutY = Float.NaN

    /** Centre of each field block (x, y pairs), for the "for sale" signs on locked ones. */
    private var blockCentres = FloatArray(0)

    private val farm get() = world.farm
    private val state get() = world.state
    private val location get() = world.location

    private class Light(val x: Float, val y: Float, val size: Float, val color: Color, val kind: Int)

    // ------------------------------------------------------------------ building a location

    private fun ensureBuilt() {
        if (built === location) return
        built = location
        build(location)
    }

    private fun build(loc: Location) {
        width = loc.width
        height = loc.height
        statics.clear()
        decals.clear()
        lights.clear()
        layers.clear()
        effects.clearAmbient()
        base = arrayOfNulls(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val t = loc.terrain(x, y)
            base[y * width + x] = when (t) {
                Terrain.FLOOR -> assets.region("terrain/floor_${hash(x, y) % 2}")
                Terrain.WALL -> if (y > 0 && loc.terrain(x, y - 1) != Terrain.WALL) assets.region("obj/wall_face") else assets.region("obj/wall_top")
                else -> if (loc.indoor) null else assets.region("terrain/grass_${hash(x, y) % 4}")
            }
        }
        if (!loc.indoor) {
            for ((terrain, name) in listOf(Terrain.WATER to "water", Terrain.SOIL to "soil", Terrain.PATH to "path", Terrain.STONE to "stone")) {
                val arr = arrayOfNulls<TextureRegion>((width + 1) * (height + 1))
                var any = false
                for (vy in 0..height) for (vx in 0..width) {
                    var mask = 0
                    if (terrainAt(loc, vx - 1, vy) == terrain) mask = mask or 1
                    if (terrainAt(loc, vx, vy) == terrain) mask = mask or 2
                    if (terrainAt(loc, vx, vy - 1) == terrain) mask = mask or 4
                    if (terrainAt(loc, vx - 1, vy - 1) == terrain) mask = mask or 8
                    if (mask == 0) continue
                    any = true
                    val suffix = if (mask == 15 && hash(vx, vy) % 3 == 0) "15b" else mask.toString()
                    arr[vy * (width + 1) + vx] = assets.region("terrain/${name}_$suffix")
                }
                if (any) layers += arr
            }
        }
        for (y in 0 until height) for (x in 0 until width) buildProp(loc, x, y)
        for (o in loc.objects) buildObject(o)
        buildLights(loc)
        waterCells.clear()
        for (y in 0 until height) for (x in 0 until width) if (loc.terrain(x, y) == Terrain.WATER) waterCells += Cell(x, y)
        // The spout sits 3.4 tiles above the fountain's footprint, in the middle (see obj/fountain).
        val fountain = loc.objects.firstOrNull { it.kind == ObjKind.FOUNTAIN }
        spoutX = fountain?.centerX ?: Float.NaN
        spoutY = fountain?.let { it.y + 3.4f } ?: Float.NaN
        blockCentres = FloatArray(loc.fieldBlocks.size * 2)
        loc.fieldBlocks.forEachIndexed { i, block ->
            if (block.isEmpty()) return@forEachIndexed
            blockCentres[i * 2] = (block.minOf { it.x } + block.maxOf { it.x } + 1) / 2f
            blockCentres[i * 2 + 1] = (block.minOf { it.y } + block.maxOf { it.y } + 1) / 2f
        }
    }

    /** Terrain lookups clamp to the map so roads and fields run cleanly off the edges. */
    private fun terrainAt(loc: Location, x: Int, y: Int): Terrain =
        loc.terrain(x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))

    private fun buildProp(loc: Location, x: Int, y: Int) {
        val h = hash(x, y)
        val jitter = ((h % 100) / 100f - 0.5f) * 0.3f
        when (loc.prop(x, y)) {
            Prop.TREE -> {
                val r = assets.region("obj/tree_${h % 3}")
                val s = 0.92f + (h % 17) / 100f
                statics += Spr().set(r, x + 0.5f - s + jitter, y + 0.18f, 2f * s, 2.6f * s, y + 0.42f).apply { sway = 1.1f; phase = h * 0.37f }
            }
            Prop.PINE -> statics += Spr().set(assets.region("obj/pine"), x - 0.25f + jitter, y + 0.15f, 1.5f, 2.6f, y + 0.4f).apply { sway = 0.8f; phase = h * 0.37f }
            Prop.BUSH -> statics += Spr().set(assets.region("obj/bush_${h % 2}"), x - 0.125f, y + 0.08f, 1.25f, 1.1f, y + 0.4f).apply { sway = 1.5f; phase = h * 0.5f }
            Prop.ROCK -> statics += Spr().set(assets.region("obj/rock_${h % 2}"), x.toFloat(), y + 0.05f, 1f, 0.85f, y + 0.4f)
            Prop.TUFT -> decals += Spr().set(assets.region("obj/tuft_${h % 2}"), x.toFloat(), y.toFloat(), 1f, 1f, 0f)
            Prop.FLOWERS -> decals += Spr().set(assets.region("obj/flowers_${h % 3}"), x.toFloat(), y.toFloat(), 1f, 1f, 0f)
            Prop.FENCE, Prop.GATE -> buildFence(loc, x, y)
            Prop.NONE -> Unit
        }
    }

    private fun isFence(loc: Location, x: Int, y: Int) = loc.prop(x, y).let { it == Prop.FENCE || it == Prop.GATE }

    private fun buildFence(loc: Location, x: Int, y: Int) {
        val fh = 1.35f
        if (loc.prop(x, y) == Prop.GATE) {
            statics += Spr().set(assets.region("obj/gate"), x.toFloat(), y.toFloat(), 1f, fh, y + 0.3f)
        } else {
            statics += Spr().set(assets.region("obj/fence_post"), x.toFloat(), y.toFloat(), 1f, fh, y + 0.3f)
        }
        if (isFence(loc, x + 1, y) && (loc.prop(x, y) == Prop.FENCE || loc.prop(x + 1, y) == Prop.FENCE)) {
            statics += Spr().set(assets.region("obj/fence_h"), x + 0.5f, y.toFloat(), 1f, fh, y + 0.31f)
        }
        if (isFence(loc, x, y + 1)) {
            statics += Spr().set(assets.region("obj/fence_v"), x.toFloat(), y.toFloat(), 1f, fh, y + 0.8f)
        }
    }

    private fun sized(name: String, o: WorldObject, sort: Float = o.y.toFloat()): Spr {
        val r = assets.region(name)
        val w = r.regionWidth / TILE
        val h = r.regionHeight / TILE
        return Spr().set(r, o.centerX - w / 2f, o.y.toFloat(), w, h, sort)
    }

    private fun buildObject(o: WorldObject) {
        when (o.kind) {
            ObjKind.HOUSE -> {
                statics += sized("obj/house", o)
                decals += Spr().set(assets.region("obj/doormat"), 4f, 17f, 1f, 1f, 0f)
            }
            ObjKind.WELL -> statics += sized("obj/well", o)
            ObjKind.MILL -> statics += sized("obj/mill", o)
            ObjKind.COOP -> statics += sized("obj/coop", o)
            ObjKind.BARN -> statics += sized("obj/barn", o)
            ObjKind.SIGN -> statics += sized("obj/sign", o)
            ObjKind.BOARD -> statics += sized("obj/board", o)
            ObjKind.BED -> statics += sized("obj/bed", o)
            ObjKind.TV -> statics += sized("obj/tv", o)
            ObjKind.STOVE -> statics += sized("obj/stove", o)
            ObjKind.SHOP_SEEDS, ObjKind.SHOP_FEED, ObjKind.BUYER -> {
                val k = when (o.kind) {
                    ObjKind.SHOP_SEEDS -> "seeds"
                    ObjKind.SHOP_FEED -> "feed"
                    else -> "produce"
                }
                statics += sized("obj/stall_$k", o, o.y + 0.9f)
                statics += sized("obj/stall_${k}_front", o, o.y.toFloat())
            }
            ObjKind.WORKSHOP -> {
                statics += sized("obj/workshop", o, o.y + 0.9f)
                statics += sized("obj/workshop_front", o, o.y.toFloat())
            }
            ObjKind.DECOR -> when (o.arg) {
                "rug" -> decals += sized("obj/rug", o, 0f)
                "window" -> decals += sized("obj/window", o, 0f)
                else -> statics += sized("obj/${o.arg}", o)
            }
            ObjKind.FOUNTAIN, ObjKind.BENCH, ObjKind.SHELF -> statics += sized("obj/${o.arg}", o)
            ObjKind.EXIT -> if (location.indoor) decals += Spr().set(assets.region("obj/doormat"), o.x.toFloat(), o.y.toFloat(), 1f, 1f, 0f)
            // Troughs, machines and the mill sails change, so they are drawn per frame.
            ObjKind.DOOR, ObjKind.TROUGH, ObjKind.MACHINE -> Unit
        }
    }

    /**
     * Lights hang off the objects that cast them (the offsets match the windows and lamps in each
     * sprite), so moving a building on the map moves its glow with it.
     */
    private fun buildLights(loc: Location) {
        val warm = Color(1f, 0.82f, 0.52f, 1f)
        fun each(kind: ObjKind, add: (WorldObject) -> Unit) = loc.objects.filter { it.kind == kind }.forEach(add)
        when (loc.id) {
            Locations.FARM -> {
                each(ObjKind.HOUSE) { o ->
                    lights += Light(o.x + 1.22f, o.y + 1.73f, 2.6f, warm, NIGHT)
                    lights += Light(o.x + 3.78f, o.y + 1.73f, 2.6f, warm, NIGHT)
                    lights += Light(o.x + 2.5f, o.y + 0.4f, 2.2f, warm, NIGHT)
                }
                each(ObjKind.BARN) { o ->
                    lights += Light(o.x + 0.81f, o.y + 2.1f, 2f, warm, NIGHT)
                    lights += Light(o.x + 3.19f, o.y + 2.1f, 2f, warm, NIGHT)
                }
                each(ObjKind.MILL) { o -> lights += Light(o.x + 1.5f, o.y + 2.4f, 1.6f, warm, NIGHT) }
            }
            Locations.MARKET -> loc.objects.filter { it.arg == "lamp" }.forEach { lights += Light(it.x + 0.5f, it.y + 2.12f, 5f, warm, NIGHT) }
            Locations.HOUSE -> {
                // A ceiling lamp over the bed corner and one over the living room.
                val room = Color(1f, 0.86f, 0.62f, 1f)
                lights += Light(loc.width * 0.35f, loc.height * 0.72f, 6.5f, room, NIGHT)
                lights += Light(loc.width * 0.5f, loc.height * 0.32f, 8.5f, room, NIGHT)
                each(ObjKind.TV) { o -> lights += Light(o.x + 0.5f, o.y + 0.9f, 2.6f, Color(0.55f, 0.75f, 1f, 1f), TV) }
                each(ObjKind.STOVE) { o -> lights += Light(o.x + o.w / 2f, o.y + 0.7f, 2.4f, warm, NIGHT) }
                loc.objects.filter { it.arg == "window" }.forEach { lights += Light(it.x + 0.5f, it.y.toFloat(), 3.2f, Color(1f, 0.98f, 0.9f, 1f), DAY) }
            }
        }
    }

    // ------------------------------------------------------------------ per frame

    fun update(dt: Float, viewL: Float, viewB: Float, viewR: Float, viewT: Float) {
        ensureBuilt()
        time += dt
        val m = state.minutes
        if (location.id == Locations.FARM) {
            val chimney = (m in 360.0..600.0) || (m in 1020.0..1380.0)
            if (chimney) {
                smokeAcc += dt
                while (smokeAcc > 0.5f) {
                    smokeAcc -= 0.5f
                    effects.smokePuff(5.75f, 22.45f)
                }
            }
            twinkleAcc += dt
            if (twinkleAcc > 0.35f) {
                twinkleAcc = 0f
                val ripe = state.plots.filter { farm.isRipe(it) && it.x + 1 >= viewL && it.x <= viewR && it.y + 1 >= viewB && it.y <= viewT }
                if (ripe.isNotEmpty()) ripe.random().let { effects.twinkle(it.x + MathUtils.random(0.2f, 0.8f), it.y + MathUtils.random(0.5f, 1.1f)) }
            }
        }
        if (waterCells.isNotEmpty()) {
            rippleAcc += dt
            if (rippleAcc > 0.7f) {
                rippleAcc = 0f
                val c = waterCells.random()
                if (c.x + 1 >= viewL && c.x <= viewR && c.y + 1 >= viewB && c.y <= viewT) effects.waterRipple(c.x + MathUtils.random(), c.y + MathUtils.random())
            }
        }
        if (!spoutX.isNaN()) {
            sprayAcc += dt
            while (sprayAcc > 0.04f) {
                sprayAcc -= 0.04f
                effects.fountainSpray(spoutX, spoutY)
            }
        }
        world.sparkleCell()?.let { c ->
            sparkleAcc += dt
            if (sparkleAcc > 0.3f) {
                sparkleAcc = 0f
                effects.twinkle(c.x + MathUtils.random(0.2f, 0.8f), c.y + MathUtils.random(0.2f, 0.8f))
            }
        }
        effects.ambient(dt, viewL, viewB, viewR, viewT, state.weather == Weather.RAIN, night(), !location.indoor, time)
        effects.update(dt)
    }

    fun draw(batch: Batch, rig: CameraRig) {
        ensureBuilt()
        val l = rig.left - 2f
        val r = rig.right + 2f
        val b = rig.bottom - 3f
        val t = rig.top + 1f
        val x0 = max(0, floor(l).toInt())
        val x1 = min(width - 1, floor(r).toInt())
        val y0 = max(0, floor(b).toInt())
        val y1 = min(height - 1, floor(t).toInt())

        batch.packedColor = Spr.WHITE
        // Ground: a hair of overlap hides seams between tiles at fractional zoom.
        val e = 1f / 64f
        for (y in y0..y1) for (x in x0..x1) base[y * width + x]?.let { batch.draw(it, x - e / 2, y - e / 2, 1f + e, 1f + e) }
        for (arr in layers) {
            for (vy in y0..min(height, y1 + 1)) for (vx in x0..min(width, x1 + 1)) {
                arr[vy * (width + 1) + vx]?.let { batch.draw(it, vx - 0.5f - e / 2, vy - 0.5f - e / 2, 1f + e, 1f + e) }
            }
        }
        for (d in decals) if (visible(d, l, b, r, t)) d.draw(batch, time)
        if (location.id == Locations.FARM) drawPlots(batch, l, b, r, t)
        drawGroundMarkers(batch)

        pool.reset()
        frame.clear()
        for (s in statics) if (visible(s, l, b, r, t)) frame += s
        addDynamic(l, b, r, t)
        frame.sortWith(bySort)
        for (s in frame) s.draw(batch, time)

        drawOverlays(batch, l, b, r, t)
        effects.draw(batch, time)
        batch.packedColor = Spr.WHITE
    }

    private fun visible(s: Spr, l: Float, b: Float, r: Float, t: Float) = s.x + s.w >= l && s.x <= r && s.y + s.h >= b && s.y <= t

    private fun drawPlots(batch: Batch, l: Float, b: Float, r: Float, t: Float) {
        val tilled = assets.region("obj/tilled")
        val wet = assets.region("obj/tilled_wet")
        val fert = assets.region("obj/fert")
        val tuft = assets.region("obj/tuft_0")
        for (p in state.plots) {
            if (p.x + 1 < l || p.x > r || p.y + 1 < b || p.y > t) continue
            if (!farm.isPlotUnlocked(p.cell)) {
                if (hash(p.x, p.y) % 3 == 0) batch.draw(tuft, p.x.toFloat(), p.y.toFloat(), 1f, 1f)
                continue
            }
            if (p.tilled) batch.draw(if (p.watered) wet else tilled, p.x.toFloat(), p.y.toFloat(), 1f, 1f)
            if (p.fertilized) batch.draw(fert, p.x.toFloat(), p.y.toFloat(), 1f, 1f)
        }
    }

    private fun drawGroundMarkers(batch: Batch) {
        val marker = assets.region("fx/marker")
        val pulse = 0.5f + 0.5f * sin(time * 5f)
        for ((cell, _) in world.queuedPlots()) {
            batch.setColor(1f, 1f, 1f, 0.45f + 0.25f * pulse)
            batch.draw(marker, cell.x + 0.05f, cell.y + 0.05f, 0.9f, 0.9f)
        }
        world.destination?.let { (dx, dy) ->
            if (world.current !is dev.mursal.dewfield.world.Task.Plot) {
                val s = 0.8f + 0.12f * pulse
                batch.setColor(1f, 1f, 1f, 0.85f)
                batch.draw(marker, dx - s / 2f, dy - s * 0.3f, s, s * 0.6f)
            }
        }
        // Today's sparkle: a slowly turning glint on the ground.
        world.sparkleCell()?.let { c ->
            val glint = assets.region("fx/sparkle")
            val s = 0.55f + 0.12f * pulse
            batch.setColor(1f, 0.97f, 0.75f, 0.75f + 0.25f * pulse)
            batch.draw(glint, c.x + 0.5f - s / 2f, c.y + 0.45f - s / 2f, s / 2f, s / 2f, s, s, 1f, 1f, time * 40f)
        }
        // Exits glow softly so the way out is easy to find.
        for (o in location.objects) if (o.kind == ObjKind.EXIT) {
            batch.setColor(1f, 0.95f, 0.7f, 0.25f + 0.2f * pulse)
            for (c in o.footprint) batch.draw(marker, c.x + 0.05f, c.y + 0.2f, 0.9f, 0.6f)
        }
        batch.packedColor = Spr.WHITE
    }

    private fun addDynamic(l: Float, b: Float, r: Float, t: Float) {
        // Locked field blocks get a sign, so it is clear they can be bought from Tural.
        if (blockCentres.isNotEmpty()) {
            val sign = assets.region("obj/lock_sign")
            val w = sign.regionWidth / TILE
            val h = sign.regionHeight / TILE
            for (i in farm.fieldBlocks until blockCentres.size / 2) {
                val cx = blockCentres[i * 2]
                val cy = blockCentres[i * 2 + 1] - 0.5f
                if (cx + w < l || cx - w > r || cy + h < b || cy > t) continue
                frame += pool.obtain().set(sign, cx - w / 2f, cy, w, h, cy)
            }
        }
        // Objects that change: troughs (fill level), machines (owned or a free pad), mill sails.
        for (o in location.objects) {
            when (o.kind) {
                ObjKind.TROUGH -> {
                    val cap = ItemDb.troughs.getValue(o.arg).capacity
                    val have = farm.trough(o.arg)
                    val lvl = when {
                        have <= 0 -> 0
                        have * 2 < cap -> 1
                        else -> 2
                    }
                    add(assets.region("obj/${o.arg}_$lvl"), o, o.y.toFloat())
                }
                ObjKind.MACHINE -> add(assets.region(if (farm.owns(o.arg)) "obj/${o.arg}" else "obj/pad"), o, o.y.toFloat())
                ObjKind.MILL -> {
                    val sails = assets.region("obj/mill_sails")
                    val size = sails.regionWidth / TILE
                    val busy = farm.machine("mill")?.queue?.isNotEmpty() == true
                    val cx = o.x + 1.5f
                    val cy = o.y + 3.91f
                    pool.obtain().set(sails, cx - size / 2f, cy - size / 2f, size, size, o.y - 0.001f).also {
                        it.oy = size / 2f
                        it.rot = -time * if (busy) 70f else 22f
                        frame += it
                    }
                }
                ObjKind.SHOP_SEEDS, ObjKind.SHOP_FEED, ObjKind.BUYER, ObjKind.WORKSHOP -> if (farm.isMarketOpen) {
                    val name = when (o.kind) {
                        ObjKind.SHOP_SEEDS -> "nargiz"
                        ObjKind.SHOP_FEED -> "orkhan"
                        ObjKind.BUYER -> "leyla"
                        else -> "tural"
                    }
                    val reg = assets.region("char/$name")
                    val s = if (o.kind == ObjKind.WORKSHOP) 0.95f else 1f
                    val w = reg.regionWidth / TILE * s
                    val h = reg.regionHeight / TILE * s
                    val bob = sin(time * 2f + o.x) * 0.02f
                    frame += pool.obtain().set(reg, o.centerX - w / 2f, o.y + 0.86f + bob, w, h, o.y + 0.5f)
                }
                else -> Unit
            }
        }
        if (location.id == Locations.FARM) addCrops(l, b, r, t)
        addAnimals()
        addPlayer()
    }

    private fun add(region: TextureRegion, o: WorldObject, sort: Float): Spr {
        val w = region.regionWidth / TILE
        val h = region.regionHeight / TILE
        return pool.obtain().set(region, o.centerX - w / 2f, o.y.toFloat(), w, h, sort).also { frame += it }
    }

    private fun cropRegion(p: PlotState): TextureRegion {
        val crop = p.crop!!
        if (p.dead) return assets.region("crop/dead")
        val stage = farm.growthStage(p)
        return if (stage == 2 && p.harvests > 0 && assets.has("crop/${crop}_regrow")) assets.region("crop/${crop}_regrow")
        else assets.region("crop/${crop}_$stage")
    }

    private fun addCrops(l: Float, b: Float, r: Float, t: Float) {
        for (p in state.plots) {
            if (p.crop == null || p.x + 1 < l || p.x > r || p.y + 3 < b || p.y > t) continue
            val reg = cropRegion(p)
            val h = reg.regionHeight / TILE
            frame += pool.obtain().set(reg, p.x.toFloat(), p.y + 0.04f, 1f, h, p.y + 0.45f).apply {
                if (!p.dead) {
                    sway = if (farm.growthStage(p) == 0) 1.2f else 2.4f
                    phase = p.x * 0.7f + p.y * 1.3f
                }
            }
        }
    }

    private fun addAnimals() {
        val sleeping = state.minutes >= 20 * 60
        for (a in world.animals) {
            val brown = a.kind == "chicken" && a.id % 2 == 0
            val frameIndex = when {
                a.moving -> 1 + ((a.animTime * 6f).toInt() and 1)
                !sleeping && ((a.animTime + a.id * 1.7f) % 6f) in 4.2f..5.2f -> 3
                else -> 0
            }
            val name = if (brown) "animal/chicken_brown_$frameIndex" else "animal/${a.kind}_$frameIndex"
            val reg = assets.region(name)
            val w = reg.regionWidth / TILE
            val h = reg.regionHeight / TILE
            val bounce = if (a.happy > 0f) abs(sin(a.happy * MathUtils.PI * 3f)) * 0.18f else 0f
            frame += pool.obtain().set(reg, a.x - w / 2f, a.y - 10f / TILE + bounce, w, h, a.y).apply { flip = a.faceRight }
            if (sleeping) {
                val z = assets.region("icon/zzz")
                val bob = sin(time * 2f + a.id) * 0.06f
                frame += pool.obtain().set(z, a.x + w * 0.15f, a.y + h * 0.75f + bob, 0.32f, 0.32f, a.y - 0.01f).apply { tint = Color(1f, 1f, 1f, 0.85f).toFloatBits() }
            }
        }
    }

    private fun addPlayer() {
        val p = world.player
        p.seat?.let { bench ->
            // The seat plank is 0.56 tiles up the bench sprite; the sitting farmer's hips are 0.3 up theirs.
            val reg = assets.region("char/farmer_sit")
            val h = reg.regionHeight / TILE
            frame += pool.obtain().set(reg, p.x - 0.5f, bench.y + SEAT_LIFT, 1f, h, bench.y - 0.01f)
            return
        }
        val acting = p.actTime > 0f
        val dir = when (p.facing) {
            Facing.DOWN -> "down"
            Facing.UP -> "up"
            else -> "side"
        }
        val f = when {
            acting -> 3
            p.moving -> intArrayOf(1, 0, 2, 0)[((p.walkTime * 9f).toInt()) and 3]
            else -> 0
        }
        val reg = assets.region("char/farmer_${dir}_$f")
        val w = 1f
        val h = reg.regionHeight / TILE
        frame += pool.obtain().set(reg, p.x - w / 2f, p.y - 10f / TILE, w, h, p.y).apply { flip = p.facing == Facing.RIGHT }
        if (acting && p.actTool.isNotEmpty()) {
            val tool = assets.region("icon/${if (p.actTool == "seed") (state.selected ?: "seed") else p.actTool}")
            val k = 1f - p.actTime / Player.ACT_TIME
            val swing = MathUtils.lerp(55f, -35f, k)
            val s = 0.44f
            val (tx, ty, sort) = when (p.facing) {
                Facing.RIGHT -> Triple(p.x + 0.18f, p.y + 0.42f, p.y - 0.01f)
                Facing.LEFT -> Triple(p.x - 0.62f, p.y + 0.42f, p.y - 0.01f)
                Facing.DOWN -> Triple(p.x - 0.02f, p.y + 0.3f, p.y - 0.01f)
                Facing.UP -> Triple(p.x - 0.42f, p.y + 0.55f, p.y + 0.01f)
            }
            frame += pool.obtain().set(tool, tx, ty, s, s, sort).apply {
                oy = s * 0.2f
                rot = if (p.facing == Facing.LEFT) -swing else swing
                flip = p.facing == Facing.LEFT
            }
        }
    }

    /** Icons above the world: queued actions and thirsty crops. */
    private fun drawOverlays(batch: Batch, l: Float, b: Float, r: Float, t: Float) {
        if (location.id != Locations.FARM) return
        val queued = HashSet<Cell>()
        for ((cell, action) in world.queuedPlots()) {
            queued += cell
            val icon = when (action) {
                PlotAction.TILL, PlotAction.CLEAR -> "hoe"
                PlotAction.WATER -> "can"
                PlotAction.PLANT -> state.selected ?: "seed"
                PlotAction.FERTILIZE -> "fertilizer"
                PlotAction.HARVEST -> "hand"
                else -> continue
            }
            val bob = sin(time * 4f + cell.x) * 0.05f
            batch.setColor(1f, 1f, 1f, 0.95f)
            batch.draw(assets.icon(icon), cell.x + 0.27f, cell.y + 0.9f + bob, 0.46f, 0.46f)
        }
        val drop = assets.icon("water")
        for (p in state.plots) {
            if (p.crop == null || p.dead || p.watered || farm.isRipe(p) || p.cell in queued) continue
            if (p.x + 1 < l || p.x > r || p.y + 2 < b || p.y > t) continue
            val bob = sin(time * 3f + p.x * 0.9f + p.y) * 0.04f
            batch.setColor(1f, 1f, 1f, 0.8f)
            batch.draw(drop, p.x + 0.34f, p.y + 1.05f + bob, 0.32f, 0.32f)
        }
        batch.packedColor = Spr.WHITE
    }

    // ------------------------------------------------------------------ lighting

    /** 0 by day, 1 at night, from the clock (indoors and outdoors alike). */
    fun night(): Float {
        val m = state.minutes.toFloat()
        return when {
            m < 390f -> 1f - (m - 360f) / 30f * 0.6f - 0.4f
            m < 1080f -> 0f
            m < 1290f -> (m - 1080f) / 210f
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    /** The light the world is multiplied by: white at noon, warm at dusk, deep blue at night. */
    fun ambient(out: Color): Color {
        val m = state.minutes.toFloat()
        if (location.indoor) {
            val n = night()
            out.set(MathUtils.lerp(1f, 0.42f, n), MathUtils.lerp(0.98f, 0.38f, n), MathUtils.lerp(0.95f, 0.5f, n), 1f)
            return out
        }
        keyframe(m, out)
        when (state.weather) {
            Weather.CLOUDY -> out.mul(0.9f, 0.92f, 0.97f, 1f)
            Weather.RAIN -> out.mul(0.72f, 0.76f, 0.86f, 1f)
            Weather.SUNNY -> Unit
        }
        return out
    }

    private fun keyframe(m: Float, out: Color) {
        val keys = KEYS
        var i = 0
        while (i < keys.size - 2 && m > keys[i + 1].first) i++
        val (m0, c0) = keys[i]
        val (m1, c1) = keys[i + 1]
        val k = ((m - m0) / (m1 - m0)).coerceIn(0f, 1f)
        out.set(c0).lerp(c1, k)
    }

    /** Whether lighting needs its own pass this frame. */
    fun needsLighting(): Boolean {
        val c = ambient(tmpColor)
        return c.r < 0.995f || c.g < 0.995f || c.b < 0.995f
    }

    private val tmpColor = Color()

    fun drawLights(batch: Batch) {
        val light = assets.region("fx/light")
        val n = night()
        for (li in lights) {
            val strength = when (li.kind) {
                NIGHT -> n
                TV -> n * (0.75f + 0.25f * sin(time * 9f) * sin(time * 3.3f))
                else -> (1f - n) * 0.35f
            }
            if (strength <= 0.01f) continue
            batch.setColor(li.color.r, li.color.g, li.color.b, strength)
            batch.draw(light, li.x - li.size / 2f, li.y - li.size / 2f, li.size, li.size)
        }
        if (!location.indoor && n > 0.05f) {
            // The farmer carries a small lantern after dark.
            val p = world.player
            batch.setColor(1f, 0.85f, 0.6f, n * 0.9f)
            batch.draw(light, p.x - 1.8f, p.y - 1.4f, 3.6f, 3.6f)
        }
        world.sparkleCell()?.let { c ->
            if (n > 0.05f) {
                batch.setColor(1f, 0.95f, 0.6f, n * 0.7f)
                batch.draw(light, c.x - 0.6f, c.y - 0.6f, 2.2f, 2.2f)
            }
        }
        effects.drawLights(batch)
        batch.packedColor = Spr.WHITE
    }

    companion object {
        const val TILE = 128f

        /** How far above the bench's footprint the sitting farmer sprite starts. */
        private const val SEAT_LIFT = 0.24f
        private const val NIGHT = 0
        private const val TV = 1
        private const val DAY = 2

        fun hash(x: Int, y: Int): Int {
            var h = x * 374761393 + y * 668265263
            h = (h xor (h ushr 13)) * 1274126177
            return (h xor (h ushr 16)) and 0x7fffffff
        }

        private val KEYS: List<Pair<Float, Color>> = listOf(
            360f to Color(0.78f, 0.74f, 0.86f, 1f),
            420f to Color(1f, 0.95f, 0.88f, 1f),
            480f to Color(1f, 1f, 1f, 1f),
            1020f to Color(1f, 1f, 1f, 1f),
            1110f to Color(1f, 0.86f, 0.7f, 1f),
            1200f to Color(0.62f, 0.55f, 0.78f, 1f),
            1290f to Color(0.36f, 0.4f, 0.66f, 1f),
            1440f to Color(0.32f, 0.36f, 0.62f, 1f),
        )
    }
}
