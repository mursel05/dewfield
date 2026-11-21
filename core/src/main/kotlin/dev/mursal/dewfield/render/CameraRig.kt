package dev.mursal.dewfield.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.exp

/**
 * The world camera, in tile units. It follows the farmer smoothly; a finger drag pans it
 * freely until the farmer walks again, and a pinch zooms between [MIN_VIEW] and [MAX_VIEW]
 * tiles of height. It never shows past the edges of the map.
 */
class CameraRig {
    val cam = OrthographicCamera()
    /** Visible height in tiles. */
    var view = DEFAULT_VIEW
        private set
    private var mapW = 1f
    private var mapH = 1f
    /** True after a manual pan; cleared when the farmer starts walking. */
    var manual = false
    private val tmp = Vector3()

    fun setMap(w: Int, h: Int, defaultView: Float) {
        mapW = w.toFloat()
        mapH = h.toFloat()
        view = defaultView
        manual = false
        resize()
    }

    fun resize() {
        val sw = Gdx.graphics.width.coerceAtLeast(1)
        val sh = Gdx.graphics.height.coerceAtLeast(1)
        cam.viewportHeight = view
        cam.viewportWidth = view * sw / sh
        clamp()
        cam.update()
    }

    fun snap(x: Float, y: Float) {
        cam.position.set(x, y, 0f)
        manual = false
        clamp()
        cam.update()
    }

    fun follow(x: Float, y: Float, dt: Float, walking: Boolean) {
        if (walking) manual = false
        if (!manual) {
            // Frame-rate independent ease toward the farmer, looking a little ahead of the feet.
            val k = 1f - exp(-dt * 6f)
            cam.position.x += (x - cam.position.x) * k
            cam.position.y += (y + 0.4f - cam.position.y) * k
        }
        clamp()
        cam.update()
    }

    /** Pans by a screen-space delta (pixels, y down). */
    fun pan(dx: Float, dy: Float) {
        val perPixel = cam.viewportHeight / Gdx.graphics.height.coerceAtLeast(1)
        cam.position.x -= dx * perPixel
        cam.position.y += dy * perPixel
        manual = true
        clamp()
        cam.update()
    }

    fun zoomTo(newView: Float) {
        view = newView.coerceIn(MIN_VIEW, MAX_VIEW)
        resize()
    }

    fun toWorld(sx: Float, sy: Float, out: Vector2): Vector2 {
        tmp.set(sx, sy, 0f)
        cam.unproject(tmp, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        return out.set(tmp.x, tmp.y)
    }

    /** World point to screen pixels with y down, for placing UI over the world. */
    fun toScreen(wx: Float, wy: Float, out: Vector2): Vector2 {
        tmp.set(wx, wy, 0f)
        cam.project(tmp, 0f, 0f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        return out.set(tmp.x, Gdx.graphics.height - tmp.y)
    }

    private fun clamp() {
        val hw = cam.viewportWidth / 2f
        val hh = cam.viewportHeight / 2f
        cam.position.x = if (mapW <= hw * 2f) mapW / 2f else cam.position.x.coerceIn(hw, mapW - hw)
        cam.position.y = if (mapH <= hh * 2f) mapH / 2f else cam.position.y.coerceIn(hh, mapH - hh)
    }

    val left get() = cam.position.x - cam.viewportWidth / 2f
    val right get() = cam.position.x + cam.viewportWidth / 2f
    val bottom get() = cam.position.y - cam.viewportHeight / 2f
    val top get() = cam.position.y + cam.viewportHeight / 2f

    companion object {
        const val DEFAULT_VIEW = 10.5f
        const val MIN_VIEW = 6.5f
        const val MAX_VIEW = 15f
    }
}
