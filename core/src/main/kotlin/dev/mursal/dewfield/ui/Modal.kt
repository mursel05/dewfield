package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Align
import dev.mursal.dewfield.app.Notifier
import dev.mursal.dewfield.app.Settings
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.Gain
import dev.mursal.dewfield.model.Msg
import dev.mursal.dewfield.model.Outcome
import dev.mursal.dewfield.world.World

/** What panels may do to the game. Implemented by the game screen. */
interface GameUi {
    val ui: Ui
    val farm: Farm
    val world: World
    fun toast(msg: Msg)
    fun gain(g: Gain)
    fun open(modal: Modal)
    fun close(modal: Modal)
    fun refreshHud()
    fun sleep()
    fun relabel()
    fun exitToTitle()

    /** Player settings (language, sound, reminders). */
    val settings: Settings

    /** Local notifications; [Notifier.None] where the platform has none. */
    val notifier: Notifier

    /** Today's calendar day in the player's time zone, for the daily gift. */
    fun today(): Long

    /** Asks the system for notification permission and turns reminders on or off with the answer. */
    fun askReminders(done: (Boolean) -> Unit)

    /** Shows the result of a rules call: message, floating gains and a sound. */
    fun report(result: Outcome, okSound: String = "coin"): Boolean {
        when (result) {
            is Outcome.Ok -> {
                ui.audio.play(okSound)
                result.msg?.let { toast(it) }
                result.gains.forEach { gain(it) }
            }
            is Outcome.Fail -> {
                ui.audio.play("error")
                toast(result.msg)
            }
        }
        refreshHud()
        return result.ok
    }
}

/**
 * A centered panel with a title bar and a close button. Subclasses fill [body] in [fill];
 * [rebuild] redraws it after every action so numbers are always current.
 *
 * Panels are sized for portrait: as wide as the screen allows up to [prefW], and [prefH] tall.
 * [FILL] takes all the height the screen has (lists, shops, the bag); [FIT] is as tall as the
 * content, so short notices like the bedtime check don't float in empty space.
 */
abstract class Modal(
    protected val ctx: GameUi,
    private val titleText: String,
    private val iconName: String?,
    val prefW: Float = WIDE,
    val prefH: Float = FILL,
    private val closable: Boolean = true,
    showMoney: Boolean = false,
) : Table() {
    protected val ui: Ui get() = ctx.ui
    protected val farm: Farm get() = ctx.farm
    protected val body = Table()
    var onClosed: (() -> Unit)? = null
    private val money = if (showMoney) ctx.ui.label("", ctx.ui.head) else null

    init {
        background = ctx.ui.panel
        touchable = Touchable.enabled
        val top = Table()
        if (iconName != null) top.add(ctx.ui.icon(iconName, 52f)).size(52f).padRight(12f)
        top.add(FitLabel(titleText, ctx.ui.title)).left().expandX().fillX().minWidth(0f)
        if (money != null) top.add(Table().apply {
            background = ctx.ui.inset
            add(ctx.ui.icon("coin", 34f)).size(34f).padRight(6f)
            add(money).minWidth(70f)
        }).padRight(14f)
        if (closable) top.add(ctx.ui.roundButton("close", 64f) { ctx.close(this) }).size(64f)
        add(top).growX().padBottom(10f).row()
        add(body).grow()
    }

    /** Clears and refills the body. */
    fun rebuild() {
        money?.setText(ctx.farm.state.money.toString())
        body.clear()
        fill(body)
        if (prefH == FIT) (parent as? ModalHost)?.layoutModal()
    }

    protected abstract fun fill(body: Table)

    /** Called by the host when the panel is shown. */
    open fun shown() = rebuild()

    open val canClose: Boolean get() = closable

    companion object {
        /** [prefH] value for panels that take all the height the screen has. */
        const val FILL = 0f

        /** [prefH] value for panels as tall as their content, up to the height the screen has. */
        const val FIT = -1f

        /** Default panel width: the whole 720-unit portrait width minus a small margin. */
        const val WIDE = 700f
    }
}

/** Shows one modal at a time over a dimmed screen; later ones wait in line. */
class ModalHost(private val ui: Ui) : Group() {
    private val dim = Image(ui.white).apply {
        color = Color(0.13f, 0.09f, 0.05f, 0f)
        touchable = Touchable.enabled
        addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) = Unit
        })
    }
    var current: Modal? = null
        private set
    private val waiting = ArrayDeque<Modal>()

    val isOpen: Boolean get() = current != null

    init {
        touchable = Touchable.childrenOnly
    }

    fun show(m: Modal, queue: Boolean = false) {
        if (current != null) {
            if (queue) {
                waiting += m
                return
            }
            dismiss(current!!, notify = true)
        }
        current = m
        // The dim may still be fading out from a panel that just closed: take it back.
        dim.clearActions()
        if (dim.parent == null) {
            addActorAt(0, dim)
            dim.color.a = 0f
        }
        dim.addAction(Actions.alpha(DIM, 0.15f))
        addActor(m)
        layoutModal()
        m.shown()
        if (m.prefH == Modal.FIT) layoutModal()
        m.isTransform = true
        m.setOrigin(Align.center)
        m.setScale(0.92f)
        m.color.a = 0f
        m.addAction(Actions.sequence(
            Actions.parallel(Actions.scaleTo(1f, 1f, 0.18f, Interpolation.swingOut), Actions.fadeIn(0.12f)),
            Actions.run { m.isTransform = false },
        ))
    }

    fun close(m: Modal) {
        if (current !== m) {
            waiting.remove(m)
            return
        }
        dismiss(m, notify = true)
        current = null
        val next = waiting.removeFirstOrNull()
        if (next != null) show(next) else dim.addAction(Actions.sequence(Actions.alpha(0f, 0.12f), Actions.removeActor()))
    }

    private fun dismiss(m: Modal, notify: Boolean) {
        m.clearActions()
        m.isTransform = true
        m.addAction(Actions.sequence(Actions.parallel(Actions.fadeOut(0.1f), Actions.scaleTo(0.95f, 0.95f, 0.1f)), Actions.removeActor()))
        if (notify) m.onClosed?.invoke()
    }

    /** Closes everything, including waiting panels (used when leaving the game). */
    fun clearAll() {
        waiting.clear()
        current?.let { close(it) }
    }

    /**
     * Sizes the open panel inside the cutout-safe area. A strip stays free at the top for
     * toasts, so messages about what just happened never cover the panel's title.
     */
    fun layoutModal() {
        val s = stage ?: return
        setSize(s.width, s.height)
        dim.setSize(s.width, s.height)
        current?.let { m ->
            val safe = ui.safe
            val areaW = s.width - safe.left - safe.right
            val w = minOf(m.prefW, areaW - SIDE * 2)
            val bottomGap = safe.bottom + BOTTOM_GAP
            val maxH = s.height - safe.top - TOP_GAP - bottomGap
            val h = when {
                m.prefH == Modal.FIT -> fitHeight(m, w, maxH)
                m.prefH <= Modal.FILL -> maxH
                else -> minOf(m.prefH, maxH)
            }
            m.setSize(w, h)
            m.setPosition(safe.left + (areaW - w) / 2f, bottomGap + (maxH - h) / 2f)
            m.invalidateHierarchy()
            m.validate()
        }
    }

    val hasWaiting: Boolean get() = waiting.isNotEmpty()

    /**
     * The content height of [m] at width [w]. Wrapped labels only know their height once they
     * have a width, so the panel is laid out once at full height, its cached sizes are dropped,
     * and then it is measured.
     */
    private fun fitHeight(m: Modal, w: Float, maxH: Float): Float {
        m.setSize(w, maxH)
        m.invalidateHierarchy()
        m.validate()
        invalidateSizes(m)
        return minOf(m.prefHeight, maxH)
    }

    private fun invalidateSizes(a: Actor) {
        if (a is Layout) a.invalidate()
        if (a is Group) a.children.forEach { invalidateSizes(it) }
    }

    companion object {
        private const val DIM = 0.55f
        private const val SIDE = 10f
        private const val BOTTOM_GAP = 16f

        /** Room above a panel for one line of toasts. */
        const val TOP_GAP = 72f
    }
}
