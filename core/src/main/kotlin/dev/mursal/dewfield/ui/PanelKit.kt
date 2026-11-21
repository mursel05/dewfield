package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align

/** Shared building blocks for the panels. */

/** A list row: icon on the left, a title and a sub line, and something on the right. */
fun Ui.row(selected: Boolean, iconKey: String, title: String, sub: String, right: Table?, locked: Boolean = false, onClick: () -> Unit): Button {
    val b = card(selected, onClick)
    b.pad(8f, 10f, 8f, 12f)
    b.add(item(iconKey, 56f)).size(56f).padRight(12f)
    val mid = Table().apply { left() }
    mid.add(label(title, head)).left().row()
    mid.add(label(sub, soft)).left()
    b.add(mid).growX().left()
    if (locked) b.add(icon("lock", 36f)).size(36f)
    else if (right != null) b.add(right).right()
    if (locked) b.color = Color(1f, 1f, 1f, 0.75f)
    return b
}

/** A line of the detail pane: small icon and wrapped text. */
fun Ui.info(iconName: String?, text: String, style: Label.LabelStyle = this.text, color: Color? = null): Table = Table().apply {
    if (iconName != null) add(icon(iconName, 30f, color)).size(30f).padRight(8f).top()
    add(label(text, style, wrap = true).also { if (color != null) it.color = color }).growX().left()
}

/** A "- 5 +" quantity picker with a Max shortcut. Calls back with the new value. */
fun Ui.stepper(value: Int, max: Int, onChange: (Int) -> Unit): Table = Table().apply {
    val top = max.coerceAtLeast(1)
    add(button("", "cream", "minus", 56f) { onChange((value - 1).coerceIn(1, top)) }).size(64f, 56f)
    add(label(value.toString(), title, align = Align.center)).width(84f)
    add(button("", "cream", "plus", 56f) { onChange((value + 1).coerceIn(1, top)) }).size(64f, 56f).padRight(8f)
    add(button("+10", "cream", null, 56f) { onChange((value + 10).coerceIn(1, top)) }).height(56f).padRight(8f)
    add(button(t("ui.max"), "cream", null, 56f) { onChange(top) }).height(56f)
}

/** The top of a detail pane: a big icon, then the name and a sub line, left-aligned. */
fun Ui.detailHeader(icon: Actor, name: String, sub: String): Table = Table().apply {
    left()
    add(icon).size(84f).padRight(14f)
    add(Table().apply {
        left()
        add(label(name, title, wrap = true)).growX().row()
        add(label(sub, soft, wrap = true)).growX()
    }).growX()
}

/** The info lines of a detail pane. Put them in [Ui.scroll] so long text never pushes the buttons out. */
fun Ui.lines(): Table = Table().apply {
    top()
    left()
}

/** Adds one full-width line to a [Ui.lines] table. */
fun Table.line(a: Actor) {
    add(a).growX().padBottom(6f).row()
}

/** The right-hand detail pane background. */
fun Ui.detailPane(): Table = Table().apply {
    background = inset
    pad(16f)
    top()
}

/** A two-column layout: a scrolling list on the left and details on the right. */
class ListDetail(private val ui: Ui) {
    var scrollY = 0f
    private var pane: ScrollPane? = null

    fun build(body: Table, list: Table, detail: Table, listWidth: Float = 540f) {
        list.top()
        val p = ui.scroll(list)
        pane = p
        body.add(p).width(listWidth).growY().top()
        body.add(detail).grow().padLeft(14f)
    }

    /** Call after the modal is laid out to keep the list where the player left it. */
    fun restore(owner: Table) {
        val p = pane ?: return
        owner.validate()
        p.layout()
        p.scrollY = scrollY
        p.updateVisualScroll()
    }

    fun remember() {
        pane?.let { scrollY = it.scrollY }
    }
}

/** Formats "2-3" style ranges. */
fun range(a: Int, b: Int): String = if (a == b) a.toString() else "$a–$b"
