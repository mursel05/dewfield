package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The sky's two small surprises, laid over the world and under the HUD.
 *
 * - On clear nights from 20:30 a shooting star crosses the screen now and then, more often while the
 *   farmer sits on a bench. Tapping it makes a wish ([dev.mursal.dewfield.model.Leisure.catchStar]).
 * - On the morning after rain a soft rainbow hangs in the sky until 10:00. Tapping its band finds the
 *   coins at its end ([dev.mursal.dewfield.model.Leisure.takeRainbow]).
 *
 * Both show only outdoors and never while a panel is open. Only taps on the star or the band are
 * taken; every other tap falls through to the world. Timing here is visual and uses its own random
 * numbers; what a catch gives comes from the rules.
 */
class Sky(private val ctx: GameUi) : Actor() {
    private val star = ctx.ui.assets.region("fx/shooting_star")
    private val bow = ctx.ui.assets.region("fx/rainbow")
    private val spark = ctx.ui.assets.region("fx/sparkle")

    /** False while a panel is open or the screen is fading: nothing new appears and nothing is caught. */
    var active = true

    /** How many stars have crossed the sky this session (the screen shows a tip after the first). */
    var starsSeen = 0
        private set

    private var wait = MathUtils.random(FIRST_WAIT_MIN, FIRST_WAIT_MAX)
    private var life = -1f
    private var headX = 0f
    private var headY = 0f
    private var velX = 0f
    private var velY = 0f
    private var bowAlpha = 0f
    private var clock = 0f

    init {
        touchable = Touchable.enabled
        addListener(object : InputListener() {
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                if (pointer != 0) return false
                return when {
                    onStar(x, y) -> {
                        catchStar()
                        true
                    }
                    onBow(x, y) -> {
                        if (ctx.report(ctx.farm.leisure.takeRainbow(), "chime")) burst(x, y)
                        true
                    }
                    else -> false
                }
            }
        })
    }

    override fun act(delta: Float) {
        super.act(delta)
        val s = stage ?: return
        setSize(s.width, s.height)
        clock += delta
        val outdoors = !ctx.world.location.indoor
        val leisure = ctx.farm.leisure
        if (life >= 0f) {
            life += delta
            headX += velX * delta
            headY += velY * delta
            if (life >= STAR_LIFE) life = -1f
        } else if (active && outdoors && leisure.starsTonight()) {
            // Sitting still under the night sky is when the stars come out.
            wait -= delta * if (ctx.world.player.seat != null) SEATED_PACE else 1f
            if (wait <= 0f) launchStar()
        }
        val wantBow = active && outdoors && leisure.rainbowVisible()
        bowAlpha = MathUtils.clamp(bowAlpha + delta * (if (wantBow) 0.8f else -1.6f), 0f, 1f)
    }

    /** Sends a star across the sky now (also used by the desktop screenshot tour). */
    fun launchStar() {
        wait = MathUtils.random(WAIT_MIN, WAIT_MAX)
        life = 0f
        starsSeen++
        val leftToRight = MathUtils.randomBoolean()
        headX = width * MathUtils.random(0.1f, 0.45f).let { if (leftToRight) it else 1f - it }
        headY = height * MathUtils.random(0.6f, 0.8f)
        val angle = MathUtils.random(22f, 36f) * MathUtils.degreesToRadians
        val speed = MathUtils.random(460f, 560f)
        velX = MathUtils.cos(angle) * speed * if (leftToRight) 1f else -1f
        velY = -MathUtils.sin(angle) * speed
    }

    private fun catchStar() {
        val x = headX
        val y = headY
        life = -1f
        val r = ctx.farm.leisure.catchStar()
        ctx.report(r, "chime")
        burst(x, y)
    }

    /** A few sparkles that fly out of the caught star, in screen space. */
    private fun burst(x: Float, y: Float) {
        val s = stage ?: return
        repeat(BURST) { i ->
            val img = Image(spark).apply {
                setSize(34f, 34f)
                setOrigin(17f, 17f)
                setPosition(x - 17f, y - 17f)
                touchable = Touchable.disabled
            }
            val a = i * MathUtils.PI2 / BURST + MathUtils.random(-0.3f, 0.3f)
            val dist = MathUtils.random(50f, 90f)
            img.addAction(Actions.sequence(
                Actions.parallel(
                    Actions.moveBy(MathUtils.cos(a) * dist, MathUtils.sin(a) * dist, 0.6f, Interpolation.pow2Out),
                    Actions.rotateBy(MathUtils.random(-120f, 120f), 0.6f),
                    Actions.sequence(Actions.delay(0.25f), Actions.fadeOut(0.35f)),
                ),
                Actions.removeActor(),
            ))
            s.addActor(img)
        }
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        if (touchable && this.touchable != Touchable.enabled) return null
        if (!active || !isVisible) return null
        return if (onStar(x, y) || onBow(x, y)) this else null
    }

    /** Near the head or the bright first part of the tail: fingers are big and the star is fast. */
    private fun onStar(x: Float, y: Float): Boolean {
        if (life < 0f) return false
        val speed = hypot(velX, velY)
        val tailX = headX - velX / speed * STAR_REACH
        val tailY = headY - velY / speed * STAR_REACH
        return Intersector.distanceSegmentPoint(headX, headY, tailX, tailY, x, y) <= STAR_TAP
    }

    private fun onBow(x: Float, y: Float): Boolean {
        if (bowAlpha < 0.5f) return false
        val (cx, cy, w) = bowPlace()
        if (y < cy) return false
        val d = hypot(x - cx, y - cy)
        val outer = w / 2f * BOW_OUTER
        val inner = w / 2f * BOW_INNER
        return d in (inner - BOW_SLACK)..(outer + BOW_SLACK)
    }

    /** Centre of the arc (the middle of its bottom edge) and its width, in stage units. */
    private fun bowPlace(): Triple<Float, Float, Float> {
        val w = minOf(width * 0.96f, height * 0.62f)
        return Triple(width / 2f, height * 0.44f, w)
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        val base = color.a * parentAlpha
        if (bowAlpha > 0f) {
            val (cx, cy, w) = bowPlace()
            val shimmer = 0.9f + 0.1f * MathUtils.sin(clock * 1.7f)
            batch.setColor(1f, 1f, 1f, base * bowAlpha * BOW_ALPHA * shimmer)
            batch.draw(bow, cx - w / 2f, cy, w, w / 2f)
        }
        if (life >= 0f) {
            val fadeIn = MathUtils.clamp(life / 0.15f, 0f, 1f)
            val fadeOut = MathUtils.clamp((STAR_LIFE - life) / 0.4f, 0f, 1f)
            val angle = atan2(velY, velX) * MathUtils.radiansToDegrees
            batch.setColor(1f, 1f, 1f, base * fadeIn * fadeOut)
            // The head is the right end of the sprite, so the streak trails behind it.
            batch.draw(star, headX - STAR_W, headY - STAR_H / 2f, STAR_W, STAR_H / 2f, STAR_W, STAR_H, 1f, 1f, angle)
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private companion object {
        const val FIRST_WAIT_MIN = 6f
        const val FIRST_WAIT_MAX = 12f
        const val WAIT_MIN = 18f
        const val WAIT_MAX = 35f
        const val SEATED_PACE = 2.5f
        const val STAR_LIFE = 1.35f
        const val STAR_W = 300f
        const val STAR_H = 44f
        const val STAR_REACH = 170f
        const val STAR_TAP = 70f
        const val BOW_ALPHA = 0.55f
        /** The band's outer and inner radius as a share of half the sprite width (see tools/art). */
        const val BOW_OUTER = 0.98f
        const val BOW_INNER = 0.7f
        const val BOW_SLACK = 16f
        const val BURST = 6
    }
}
