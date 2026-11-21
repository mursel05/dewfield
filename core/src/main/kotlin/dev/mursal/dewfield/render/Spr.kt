package dev.mursal.dewfield.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import kotlin.math.sin

/**
 * One sprite in world units, anchored at its bottom-left corner. Rotation and mirroring
 * happen around the bottom-center, so plants sway from the root and animals turn in place.
 * [sort] is the depth: larger values are farther away (higher on the map) and drawn first.
 */
class Spr {
    lateinit var region: TextureRegion
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    var rot = 0f
    var flip = false
    var tint = WHITE
    var sort = 0f
    /** Wind sway amplitude in degrees, and its phase. */
    var sway = 0f
    var phase = 0f
    /** Rotation origin height above [y] (0 = the bottom edge). */
    var oy = 0f

    fun set(region: TextureRegion, x: Float, y: Float, w: Float, h: Float, sort: Float): Spr {
        this.region = region
        this.x = x
        this.y = y
        this.w = w
        this.h = h
        this.sort = sort
        rot = 0f
        flip = false
        tint = WHITE
        sway = 0f
        phase = 0f
        oy = 0f
        return this
    }

    fun draw(batch: Batch, time: Float) {
        batch.packedColor = tint
        val r = rot + if (sway != 0f) sway * sin(time * 1.3f + phase) else 0f
        if (r == 0f && !flip) batch.draw(region, x, y, w, h)
        else batch.draw(region, x, y, w / 2f, oy, w, h, if (flip) -1f else 1f, 1f, r)
    }

    companion object {
        val WHITE = Color.WHITE.toFloatBits()
    }
}

/** A reusable pool of [Spr] so the per-frame sprite list allocates nothing. */
class SprPool {
    private val items = ArrayList<Spr>()
    private var used = 0

    fun obtain(): Spr {
        if (used == items.size) items += Spr()
        return items[used++]
    }

    fun reset() {
        used = 0
    }
}
