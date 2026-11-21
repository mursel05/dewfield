package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
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
 */
abstract class Modal(
    protected val ctx: GameUi,
    private val titleText: String,
    private val iconName: String?,
    val prefW: Float = 1060f,
    val prefH: Float = 620f,
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
        top.add(ctx.ui.label(titleText, ctx.ui.title)).left().expandX()
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
    }

    protected abstract fun fill(body: Table)

    /** Called by the host when the panel is shown. */
    open fun shown() = rebuild()

    open val canClose: Boolean get() = closable
}

/** Shows one modal at a time over a dimmed screen; later ones wait in line. */
class ModalHost(ui: Ui) : Group() {
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

    fun layoutModal() {
        val s = stage ?: return
        setSize(s.width, s.height)
        dim.setSize(s.width, s.height)
        current?.let { m ->
            val w = minOf(m.prefW, s.width - 32f)
            val h = minOf(m.prefH, s.height - 24f)
            m.setSize(w, h)
            m.setPosition((s.width - w) / 2f, (s.height - h) / 2f)
            m.invalidateHierarchy()
            m.validate()
        }
    }

    val hasWaiting: Boolean get() = waiting.isNotEmpty()

    private companion object {
        const val DIM = 0.55f
    }
}
