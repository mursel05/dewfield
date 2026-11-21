package dev.mursal.dewfield.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.MathUtils
import dev.mursal.dewfield.gfx.Assets
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A short-lived sprite in world units: dirt clods, water drops, sparkles, smoke, rain. */
class Particle {
    lateinit var region: TextureRegion
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var gravity = 0f
    var drag = 0f
    var life = 0f
    var maxLife = 1f
    var w = 0.2f
    var h = 0.2f
    var grow = 0f
    var rot = 0f
    var spin = 0f
    var r = 1f
    var g = 1f
    var b = 1f
    var alpha = 1f
    /** Fades in over the first part of the life instead of popping in. */
    var fadeIn = 0f
    /** Ground height: the particle stops falling here (for clods and drops). */
    var floor = Float.NEGATIVE_INFINITY
    var splash = false
    var flapping = false
}

/**
 * Particles and ambient life. Bursts come from farm actions; ambient systems (rain, fireflies,
 * butterflies) keep a population alive inside the camera view.
 */
class Effects(private val assets: Assets) {
    private val live = ArrayList<Particle>()
    private val spare = ArrayList<Particle>()
    private val butterflies = ArrayList<Butterfly>()
    private val fireflies = ArrayList<Firefly>()
    private var rainAcc = 0f

    private val clod = assets.region("fx/clod")
    private val drop = assets.region("fx/drop")
    private val leaf = assets.region("fx/leaf")
    private val sparkle = assets.region("fx/sparkle")
    private val smoke = assets.region("fx/smoke")
    private val ripple = assets.region("fx/ripple")
    private val rain = assets.region("fx/rain")
    private val dot = assets.region("fx/dot")
    private val heart = assets.region("icon/heart")
    private val wings = listOf(assets.region("fx/butterfly_0"), assets.region("fx/butterfly_1"))

    private fun spawn(region: TextureRegion, x: Float, y: Float, life: Float, size: Float): Particle {
        val p = if (spare.isEmpty()) Particle() else spare.removeAt(spare.size - 1)
        p.region = region
        p.x = x
        p.y = y
        p.vx = 0f
        p.vy = 0f
        p.gravity = 0f
        p.drag = 0f
        p.life = life
        p.maxLife = life
        p.w = size
        p.h = size * region.regionHeight / region.regionWidth
        p.grow = 0f
        p.rot = 0f
        p.spin = 0f
        p.r = 1f
        p.g = 1f
        p.b = 1f
        p.alpha = 1f
        p.fadeIn = 0f
        p.floor = Float.NEGATIVE_INFINITY
        p.splash = false
        p.flapping = false
        live += p
        return p
    }

    /** A burst for a farm action at a world point (usually a plot center). */
    fun burst(kind: String, x: Float, y: Float) {
        when (kind) {
            "till", "clear" -> repeat(7) {
                spawn(clod, x + MathUtils.random(-0.3f, 0.3f), y, 0.7f, MathUtils.random(0.08f, 0.15f)).apply {
                    vx = MathUtils.random(-1.6f, 1.6f); vy = MathUtils.random(1.5f, 3.2f); gravity = -9f
                    floor = y - MathUtils.random(0.05f, 0.35f)
                }
            }
            "water" -> repeat(9) {
                spawn(drop, x + MathUtils.random(-0.35f, 0.35f), y + 0.55f, 0.6f, MathUtils.random(0.07f, 0.12f)).apply {
                    vx = MathUtils.random(-0.8f, 0.8f); vy = MathUtils.random(-0.5f, 1.2f); gravity = -8f
                    floor = y - MathUtils.random(0.1f, 0.35f); splash = true; alpha = 0.9f
                }
            }
            "plant" -> repeat(5) {
                spawn(clod, x + MathUtils.random(-0.2f, 0.2f), y, 0.45f, 0.07f).apply {
                    vx = MathUtils.random(-0.8f, 0.8f); vy = MathUtils.random(1f, 2f); gravity = -8f; floor = y - 0.2f
                }
            }
            "harvest" -> repeat(7) {
                spawn(leaf, x + MathUtils.random(-0.3f, 0.3f), y + 0.3f, 1.1f, MathUtils.random(0.15f, 0.24f)).apply {
                    vx = MathUtils.random(-1.4f, 1.4f); vy = MathUtils.random(1.5f, 3f); gravity = -4.5f; drag = 1.5f
                    spin = MathUtils.random(-360f, 360f); floor = y - MathUtils.random(0f, 0.4f)
                }
            }
            "sparkle", "fertilize" -> repeat(6) {
                val a = MathUtils.random(MathUtils.PI2)
                spawn(sparkle, x + cos(a) * 0.2f, y + 0.4f + sin(a) * 0.2f, 0.8f, MathUtils.random(0.16f, 0.28f)).apply {
                    vx = cos(a) * 1.2f; vy = sin(a) * 1.2f + 0.6f; drag = 3f; spin = 200f; g = 0.95f; b = 0.6f
                }
            }
            "heart" -> spawn(heart, x - 0.15f, y, 1.2f, 0.34f).apply { vy = 1.1f; drag = 0.8f; fadeIn = 0.15f }
            "pop" -> repeat(5) {
                spawn(sparkle, x + MathUtils.random(-0.3f, 0.3f), y + MathUtils.random(0.2f, 0.6f), 0.5f, 0.18f).apply {
                    vy = 1f; drag = 2f; spin = 180f
                }
            }
        }
    }

    /** A single glint, used on ripe crops. */
    fun twinkle(x: Float, y: Float) {
        spawn(sparkle, x, y, 0.7f, 0.22f).apply { spin = 120f; fadeIn = 0.3f; g = 0.97f; b = 0.75f }
    }

    /** Feedback ring where the player tapped. */
    fun tapRipple(x: Float, y: Float) {
        spawn(ripple, x, y, 0.45f, 0.35f).apply { grow = 1.4f; alpha = 0.85f; h = w * 0.55f }
    }

    fun smokePuff(x: Float, y: Float) {
        spawn(smoke, x + MathUtils.random(-0.05f, 0.05f), y, 3.2f, 0.32f).apply {
            vx = 0.12f + MathUtils.random(0.1f); vy = 0.55f; grow = 0.35f; alpha = 0.45f; fadeIn = 0.2f
            r = 0.95f; g = 0.95f; b = 0.97f
        }
    }

    fun fountainSpray(x: Float, y: Float) {
        spawn(drop, x, y, 1.1f, 0.07f).apply {
            vx = MathUtils.random(-1.2f, 1.2f); vy = MathUtils.random(2.4f, 3.2f); gravity = -6.5f; alpha = 0.85f
            floor = y - 1.25f; splash = true
        }
    }

    fun waterRipple(x: Float, y: Float) {
        spawn(ripple, x, y, 1.6f, 0.3f).apply { grow = 0.5f; alpha = 0.35f; h = w * 0.5f; fadeIn = 0.3f }
    }

    /**
     * Keeps ambient life inside the view rectangle: rain streaks, butterflies by day,
     * fireflies at night. [night] is 0..1.
     */
    fun ambient(dt: Float, l: Float, b: Float, r: Float, t: Float, raining: Boolean, night: Float, outdoors: Boolean, time: Float) {
        if (raining && outdoors) {
            rainAcc += dt * (r - l) * (t - b) * 2.4f
            while (rainAcc >= 1f) {
                rainAcc -= 1f
                val x = MathUtils.random(l - 1f, r + 2f)
                val y = MathUtils.random(b, t + 2f)
                spawn(rain, x, y, MathUtils.random(0.25f, 0.42f), 0.08f).apply {
                    h = 0.6f; vx = -2.4f; vy = -13f; alpha = 0.68f; splash = true
                    rot = atan2(vy, vx) * MathUtils.radiansToDegrees + 90f
                }
            }
        }
        val wantButterflies = if (outdoors && !raining && night < 0.2f) 4 else 0
        while (butterflies.size < wantButterflies) butterflies += Butterfly(MathUtils.random(l, r), MathUtils.random(b, t))
        if (butterflies.size > wantButterflies) butterflies.clear()
        val wantFireflies = if (outdoors && !raining && night > 0.5f) 16 else 0
        while (fireflies.size < wantFireflies) fireflies += Firefly(MathUtils.random(l, r), MathUtils.random(b, t))
        if (fireflies.size > wantFireflies) fireflies.clear()
        for (f in butterflies) f.update(dt, time, l, b, r, t)
        for (f in fireflies) f.update(dt, time, l, b, r, t)
    }

    fun clearAmbient() {
        butterflies.clear()
        fireflies.clear()
        live.clear()
    }

    fun update(dt: Float) {
        var i = live.size - 1
        while (i >= 0) {
            val p = live[i]
            p.life -= dt
            if (p.life <= 0f) {
                if (p.splash) spawnSplash(p.x, p.y)
                live.removeAt(i)
                spare += p
                i--
                continue
            }
            p.vy += p.gravity * dt
            if (p.drag > 0f) {
                val k = 1f / (1f + p.drag * dt)
                p.vx *= k
                p.vy *= k
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.y < p.floor) {
                p.y = p.floor
                if (p.splash) {
                    p.life = 0.0001f
                } else {
                    p.vx *= 0.3f
                    p.vy = 0f
                    p.gravity = 0f
                    p.spin = 0f
                }
            }
            p.rot += p.spin * dt
            if (p.grow != 0f) {
                val f = 1f + p.grow * dt
                p.w *= f
                p.h *= f
            }
            i--
        }
    }

    private fun spawnSplash(x: Float, y: Float) {
        spawn(ripple, x, y, 0.28f, 0.14f).apply { grow = 2.2f; alpha = 0.5f; h = w * 0.45f }
    }

    fun draw(batch: Batch, time: Float) {
        for (p in live) {
            val t = 1f - p.life / p.maxLife
            var a = p.alpha * if (p.life < p.maxLife * 0.35f) p.life / (p.maxLife * 0.35f) else 1f
            if (p.fadeIn > 0f && t < p.fadeIn) a *= t / p.fadeIn
            batch.setColor(p.r, p.g, p.b, a)
            batch.draw(p.region, p.x - p.w / 2f, p.y - p.h / 2f, p.w / 2f, p.h / 2f, p.w, p.h, 1f, 1f, p.rot)
        }
        for (f in butterflies) {
            val wing = wings[((time * 10f + f.seed).toInt()) and 1]
            batch.setColor(f.color)
            batch.draw(wing, f.x - 0.17f, f.y - 0.14f, 0.34f, 0.28f)
        }
        for (f in fireflies) {
            batch.setColor(0.85f, 1f, 0.45f, f.glow)
            batch.draw(dot, f.x - 0.08f, f.y - 0.08f, 0.16f, 0.16f)
        }
        batch.setColor(Color.WHITE)
    }

    /** Fireflies also light up the night map. */
    fun drawLights(batch: Batch) {
        for (f in fireflies) {
            batch.setColor(0.6f, 0.9f, 0.3f, f.glow * 0.8f)
            batch.draw(dot, f.x - 0.5f, f.y - 0.5f, 1f, 1f)
        }
        batch.setColor(Color.WHITE)
    }

    private class Butterfly(var x: Float, var y: Float) {
        val seed = MathUtils.random(10f)
        val color: Color = listOf(Color.valueOf("fff3a8"), Color.valueOf("ffd0e6"), Color.valueOf("cfe8ff"), Color.WHITE).random()
        private var tx = x
        private var ty = y

        fun update(dt: Float, time: Float, l: Float, b: Float, r: Float, t: Float) {
            if (MathUtils.random() < dt * 0.4f || x < l - 2f || x > r + 2f || y < b - 2f || y > t + 2f) {
                tx = MathUtils.random(l, r)
                ty = MathUtils.random(b, t)
            }
            x += ((tx - x) * 0.35f + sin(time * 3f + seed) * 0.6f) * dt
            y += ((ty - y) * 0.35f + cos(time * 4.1f + seed) * 0.7f) * dt
        }
    }

    private class Firefly(var x: Float, var y: Float) {
        private val seed = MathUtils.random(10f)
        var glow = 0f

        fun update(dt: Float, time: Float, l: Float, b: Float, r: Float, t: Float) {
            x += sin(time * 0.7f + seed * 3f) * 0.35f * dt
            y += cos(time * 0.5f + seed) * 0.3f * dt
            if (x < l - 1f) x = r
            if (x > r + 1f) x = l
            if (y < b - 1f) y = t
            if (y > t + 1f) y = b
            glow = (0.5f + 0.5f * sin(time * 2.2f + seed * 5f)).let { it * it }
        }
    }
}
