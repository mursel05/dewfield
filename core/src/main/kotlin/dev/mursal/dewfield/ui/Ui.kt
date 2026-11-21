package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import dev.mursal.dewfield.audio.Audio
import dev.mursal.dewfield.gfx.Assets
import dev.mursal.dewfield.i18n.Strings
import dev.mursal.dewfield.model.ItemKey
import dev.mursal.dewfield.model.Msg

/**
 * The game's widget kit on top of scene2d: soft cream panels, chunky buttons with a lip,
 * item icons with quality stars and counts. All sizes are in the 1280x720 virtual UI.
 */
class Ui(val assets: Assets, private val stringsRef: () -> Strings, val audio: Audio) {
    val strings: Strings get() = stringsRef()

    fun t(key: String): String = strings[key]
    fun f(key: String, vararg args: Any): String = strings.format(key, *args)
    fun m(msg: Msg): String = strings.msg(msg)

    // ------------------------------------------------------------------ drawables

    private fun nine(name: String, l: Int, r: Int, t: Int, b: Int, pad: Float? = null): NinePatchDrawable =
        NinePatchDrawable(assets.nine(name, l, r, t, b)).apply {
            if (pad != null) {
                leftWidth = pad; rightWidth = pad; topHeight = pad; bottomHeight = pad
            }
        }

    val panel = nine("panel", 32, 32, 32, 32, 22f)
    val inset = nine("panel_inset", 28, 28, 28, 28, 12f)
    val dark = nine("dark", 32, 32, 32, 32, 10f)
    val header = nine("header", 30, 30, 30, 30, 10f)
    val slot = nine("slot", 26, 26, 26, 26, 8f)
    val slotSel = nine("slot_sel", 26, 26, 26, 26, 8f)
    val pill = nine("pill", 30, 30, 30, 30, 8f).apply { leftWidth = 14f; rightWidth = 16f }
    val tabOn = nine("tab_on", 26, 26, 26, 26, 10f).apply { leftWidth = 22f; rightWidth = 22f }
    val tabOff = nine("tab_off", 26, 26, 26, 26, 10f).apply { leftWidth = 22f; rightWidth = 22f }
    val badge = TextureRegionDrawable(assets.region("ui/badge"))
    val white = TextureRegionDrawable(assets.region("ui/white"))
    private val buttons = HashMap<String, Pair<Drawable, Drawable>>()

    private fun buttonSkin(color: String): Pair<Drawable, Drawable> = buttons.getOrPut(color) {
        val up = nine("btn_$color", 30, 30, 30, 34).apply { leftWidth = 22f; rightWidth = 22f; topHeight = 8f; bottomHeight = 12f }
        val down = nine("btn_${color}_down", 30, 30, 34, 30).apply { leftWidth = 22f; rightWidth = 22f; topHeight = 12f; bottomHeight = 8f }
        up to down
    }

    // ------------------------------------------------------------------ text

    val small = Label.LabelStyle(assets.fonts.small, INK)
    val text = Label.LabelStyle(assets.fonts.text, INK)
    val head = Label.LabelStyle(assets.fonts.head, INK)
    val title = Label.LabelStyle(assets.fonts.title, INK)
    val outline = Label.LabelStyle(assets.fonts.outline, Color.WHITE)
    val outlineBig = Label.LabelStyle(assets.fonts.outlineBig, Color.WHITE)
    val light = Label.LabelStyle(assets.fonts.text, Color.WHITE)
    val lightHead = Label.LabelStyle(assets.fonts.head, Color.WHITE)
    val soft = Label.LabelStyle(assets.fonts.small, INK_SOFT)

    fun label(s: String, style: Label.LabelStyle = text, wrap: Boolean = false, align: Int = Align.left): Label =
        Label(s, style).apply {
            this.wrap = wrap
            setAlignment(align)
        }

    // ------------------------------------------------------------------ images

    /**
     * An icon. Glyph icons (check, close, plus...) are drawn white so they can be tinted: they get
     * [tint], or a readable default for cream backgrounds. Full-colour icons ignore [tint].
     */
    fun icon(name: String, size: Float, tint: Color? = null): Image = Image(TextureRegionDrawable(assets.icon(name)), Scaling.fit).apply {
        setSize(size, size)
        touchable = Touchable.disabled
        if (name in GLYPHS) color.set(tint ?: glyphTint(name))
    }

    private fun glyphTint(name: String): Color = when (name) {
        "check" -> GOOD
        "close" -> BAD
        else -> INK
    }

    fun image(region: String): Image = Image(TextureRegionDrawable(assets.region(region)), Scaling.fit).apply { touchable = Touchable.disabled }

    /** An item icon with its quality star and, optionally, a count in the corner. */
    fun item(key: String, size: Float, count: Int? = null): Stack {
        val id = ItemKey.id(key)
        val q = ItemKey.quality(key)
        val stack = Stack()
        stack.add(Table().apply { add(icon(id, size)).size(size) })
        if (q > 0) stack.add(Table().apply { top().left(); add(icon(if (q == 2) "star_gold" else "star_silver", size * 0.42f)).size(size * 0.42f) })
        if (count != null) stack.add(Table().apply {
            bottom().right()
            add(label(count.toString(), outline)).padRight(-2f).padBottom(-4f)
        })
        stack.touchable = Touchable.disabled
        return stack
    }

    // ------------------------------------------------------------------ buttons

    /** A chunky button with optional icon. [color] is green, orange, blue, red, grey or cream. */
    fun button(label: String, color: String = "green", icon: String? = null, height: Float = 64f, onClick: () -> Unit): Button {
        val (up, down) = buttonSkin(color)
        val disabled = buttonSkin("grey").first
        val b = Button(Button.ButtonStyle(up, down, null).apply { this.disabled = disabled })
        val dark = color == "cream" || color == "grey"
        if (icon != null) b.add(icon(icon, height * 0.56f, if (dark) null else Color.WHITE)).size(height * 0.56f).padRight(if (label.isEmpty()) 0f else 8f)
        if (label.isNotEmpty()) b.add(label(label, if (dark) head else lightHeadShadow()))
        b.height = height
        b.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                audio.play("click")
                onClick()
            }
        })
        return b
    }

    private val lightHeadStyle = Label.LabelStyle(assets.fonts.head, Color.WHITE)
    private fun lightHeadShadow() = lightHeadStyle

    /** A round icon button (HUD and close buttons). */
    fun roundButton(icon: String, size: Float = 72f, onClick: () -> Unit): Button {
        val up = TextureRegionDrawable(assets.region("ui/round"))
        val down = TextureRegionDrawable(assets.region("ui/round_down"))
        val b = Button(Button.ButtonStyle(up, down, null))
        b.add(icon(icon, size * 0.58f)).size(size * 0.58f).padBottom(size * 0.06f)
        b.setSize(size, size)
        b.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                audio.play("click")
                onClick()
            }
        })
        return b
    }

    /** A selectable card (list rows, inventory slots). */
    fun card(selected: Boolean, onClick: () -> Unit): Button {
        val b = Button(Button.ButtonStyle(if (selected) slotSel else slot, slotSel, null))
        b.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                audio.play("tick")
                onClick()
            }
        })
        return b
    }

    fun tab(text: String, on: Boolean, onClick: () -> Unit): Button {
        val b = Button(Button.ButtonStyle(if (on) tabOn else tabOff, tabOn, null))
        b.add(label(text, if (on) lightHead else head))
        b.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                audio.play("tick")
                onClick()
            }
        })
        return b
    }

    fun scroll(content: Actor): ScrollPane = ScrollPane(content.also { (it as? Table)?.top() }, ScrollPane.ScrollPaneStyle().apply {
        vScrollKnob = NinePatchDrawable(NinePatch(assets.region("ui/knob"), 6, 6, 6, 6)).apply { minWidth = 8f }
    }).apply {
        setFadeScrollBars(false)
        setScrollingDisabled(true, false)
        setOverscroll(false, true)
        setupFadeScrollBars(0f, 0f)
        setFlickScroll(true)
        setCancelTouchFocus(true)
    }

    /** A coin amount: icon plus number. */
    fun coins(amount: Int, style: Label.LabelStyle = head): Table = Table().apply {
        add(icon("coin", 30f)).size(30f).padRight(6f)
        add(label(amount.toString(), style))
    }

    fun bar(fill: String, width: Float, height: Float, value: () -> Float): Bar = Bar(
        NinePatch(assets.region("ui/bar_bg"), 13, 13, 13, 13).apply { scale(height / 28f, height / 28f) },
        NinePatch(assets.region("ui/bar_$fill"), 13, 13, 13, 13).apply { scale(height / 28f, height / 28f) },
        value,
    ).apply { setSize(width, height) }

    companion object {
        val INK: Color = Color.valueOf("5a4430")
        val INK_SOFT: Color = Color.valueOf("8a7258")
        val GOOD: Color = Color.valueOf("3f8a2e")
        val BAD: Color = Color.valueOf("c4473a")
        val GOLD: Color = Color.valueOf("d08f17")

        /** Icons drawn as plain white shapes, meant to be tinted. */
        val GLYPHS = setOf("check", "close", "plus", "minus", "play", "queue", "zzz")
    }
}

/** A rounded progress bar. */
class Bar(private val bg: NinePatch, private val fill: NinePatch, private val value: () -> Float) : Actor() {
    init {
        touchable = Touchable.disabled
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
        bg.draw(batch, x, y, width, height)
        val v = value().coerceIn(0f, 1f)
        val minW = fill.leftWidth + fill.rightWidth
        if (v > 0f) {
            val w = (width - 4f) * v
            if (w >= minW) fill.draw(batch, x + 2f, y + 2f, w, height - 4f)
            else {
                batch.setColor(color.r, color.g, color.b, color.a * parentAlpha * (w / minW))
                fill.draw(batch, x + 2f, y + 2f, minW, height - 4f)
            }
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }
}
