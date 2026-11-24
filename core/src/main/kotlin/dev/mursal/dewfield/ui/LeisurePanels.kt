package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import dev.mursal.dewfield.model.DailyGift
import dev.mursal.dewfield.model.Gain
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.KeepsakeSource
import dev.mursal.dewfield.model.Leisure
import dev.mursal.dewfield.model.Msg
import dev.mursal.dewfield.model.Outcome
import dev.mursal.dewfield.model.Wish
import dev.mursal.dewfield.world.ObjKind
import dev.mursal.dewfield.world.WorldEvent

// ---------------------------------------------------------------------- daily gift

/**
 * The daily gift calendar: seven days in a row, today's highlighted, and one big button to open it.
 * After opening, the panel offers reminders once, at the moment they make the most sense.
 */
class GiftPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("gift.title"), "gift", prefH = Modal.FIT) {
    /** Calendar position of the gift opened in this panel, or null before opening. */
    private var opened: Int? = null

    override fun fill(body: Table) {
        val s = farm.state
        val today = opened ?: DailyGift.nextIndex(s)
        body.add(ui.label(ui.t(if (opened == null) "gift.intro" else "gift.thanks"), ui.text, wrap = true, align = Align.center))
            .growX().padBottom(14f).row()
        val row = Table()
        for (i in ItemDb.dailyGifts.indices) row.add(dayCard(i, today)).width(DAY_W).pad(3f)
        body.add(row).padBottom(16f).row()
        val claimable = DailyGift.canClaim(s, ctx.today())
        when {
            opened == null && claimable -> body.add(ui.button(ui.t("gift.open"), "green", "gift") { claim() }).growX().height(72f)
            opened != null && ctx.notifier.available && !ctx.settings.remindersAsked -> {
                body.add(ui.info("bell", ui.t("gift.remind_offer"))).growX().padBottom(12f).row()
                val buttons = Table()
                buttons.add(ui.button(ui.t("gift.remind_no"), "cream") {
                    ctx.settings.remindersAsked = true
                    ctx.close(this)
                }).growX().height(66f).padRight(10f)
                buttons.add(ui.button(ui.t("gift.remind_yes"), "blue", "bell") {
                    ctx.askReminders { ok ->
                        ctx.toast(Msg(if (ok) "msg.reminders_on" else "msg.reminders_blocked"))
                        ctx.close(this)
                    }
                }).growX().height(66f)
                body.add(buttons).growX()
            }
            else -> body.add(ui.button(ui.t(if (opened != null) "gift.see_you" else "gift.come_back"), "green", "check") { ctx.close(this) })
                .growX().height(72f)
        }
    }

    private fun dayCard(i: Int, today: Int): Table {
        val done = i < today || (opened != null && i == today)
        val card = Table().apply {
            background = if (i == today) ui.slotSel else ui.slot
            pad(8f, 2f, 8f, 2f)
        }
        card.add(ui.label(ui.f("gift.day", i + 1), ui.small, align = Align.center)).row()
        val gifts = ItemDb.dailyGifts[i]
        // The last day is a wrapped surprise until it is opened.
        val big = gifts.size > 1
        val icon = Stack()
        icon.add(ui.item(if (big && !done) "gift" else iconOf(gifts.first()), 52f))
        if (done) icon.add(Table().apply { bottom().right(); add(ui.icon("check", 28f, Ui.GOOD)).size(28f) })
        card.add(icon).size(52f).pad(4f, 0f, 4f, 0f).row()
        val amount = if (big && !done) "?" else amountOf(gifts.first()) + if (big) "+" else ""
        card.add(ui.label(amount, ui.head, align = Align.center))
        if (done && i != today) card.color = Color(1f, 1f, 1f, 0.6f)
        return card
    }

    private fun iconOf(g: Gain) = if (g.key == "coins") "coin" else g.key

    private fun amountOf(g: Gain) = if (g.key == "coins") g.amount.toString() else "×${g.amount}"

    /** Opens today's gift (the big button; the desktop tour calls it directly). */
    fun claim() {
        val index = DailyGift.nextIndex(farm.state)
        if (ctx.report(DailyGift.claim(farm, ctx.today()), "levelup")) {
            opened = index
            rebuild()
        }
    }

    private companion object {
        const val DAY_W = 84f
    }
}

// ---------------------------------------------------------------------- the wishing fountain

/** Toss a coin and wish for rain, a little gift or energy. A few wishes a day; some come true. */
class WishPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("wish.title"), "fountain", prefH = Modal.FIT, showMoney = true) {
    private var last: Outcome? = null

    override fun fill(body: Table) {
        body.add(ui.label(ui.t("wish.intro"), ui.text, wrap = true)).growX().padBottom(10f).row()
        val leisure = farm.leisure
        body.add(ui.info("coin", ui.f("wish.left", leisure.wishesLeft, ItemDb.WISHES_PER_DAY), ui.head)).growX().padBottom(12f).row()
        last?.let { r ->
            val miss = r is Outcome.Ok && Leisure.isMiss(r.msg)
            val text = when (r) {
                is Outcome.Ok -> r.msg?.let { ui.m(it) }
                is Outcome.Fail -> ui.m(r.msg)
            }
            if (text != null) {
                body.add(Table().apply {
                    background = ui.inset
                    pad(12f)
                    add(ui.icon(if (miss || !r.ok) "water" else "star_gold", 40f)).size(40f).padRight(10f)
                    add(ui.label(text, ui.text, wrap = true).also { it.color = if (miss || !r.ok) Ui.INK_SOFT else Ui.GOOD }).growX()
                }).growX().padBottom(12f).row()
            }
        }
        for (w in Wish.entries) {
            val key = w.name.lowercase()
            val blocker = leisure.wishBlocker(w)
            val card = Table().apply {
                background = ui.inset
                pad(10f, 12f, 10f, 12f)
            }
            card.add(ui.icon(WISH_ICONS.getValue(w), 64f)).size(64f).padRight(12f)
            val mid = Table().apply { left() }
            mid.add(ui.label(ui.t("wish.$key"), ui.head)).left().row()
            mid.add(ui.label(blocker?.let { ui.m(it) } ?: ui.t("wish.$key.sub"), ui.soft, wrap = true)).growX().left()
            card.add(mid).growX().padRight(10f)
            card.add(ui.button(ItemDb.WISH_COST.toString(), "orange", "coin", 60f) { toss(w) }.apply { isDisabled = blocker != null })
                .width(140f).height(60f)
            body.add(card).growX().padBottom(10f).row()
        }
        body.add(ui.label(ui.t("wish.footer"), ui.soft, wrap = true, align = Align.center)).growX().padTop(4f)
    }

    private fun toss(w: Wish) {
        val r = farm.leisure.wish(w)
        val miss = r is Outcome.Ok && Leisure.isMiss(r.msg)
        // The panel shows the result itself, so only the gains float and the sound plays.
        when (r) {
            is Outcome.Ok -> {
                ui.audio.play(if (miss) "splash" else "chime")
                r.gains.forEach { ctx.gain(it) }
                ctx.world.location.objects.firstOrNull { it.kind == ObjKind.FOUNTAIN }?.let { f ->
                    ctx.world.events += WorldEvent.Fx(if (miss) "water" else "sparkle", f.centerX, f.y + 1.6f)
                }
            }
            is Outcome.Fail -> ui.audio.play("error")
        }
        ctx.refreshHud()
        last = r
        rebuild()
    }

    private companion object {
        val WISH_ICONS = mapOf(Wish.RAIN to "rain", Wish.GIFT to "gift", Wish.ENERGY to "energy")
    }
}

// ---------------------------------------------------------------------- keepsakes

/** The shelf at home: every keepsake, found or not, with its story or a hint where to look. */
class ShelfPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("shelf.title"), "keepsake_pocket_watch", prefH = Modal.FIT) {
    private var selected: String = ItemDb.keepsakes.firstOrNull { ctx.farm.leisure.has(it.id) }?.id ?: ItemDb.keepsakes.first().id

    override fun fill(body: Table) {
        val leisure = farm.leisure
        body.add(ui.label(ui.f("shelf.count", leisure.found, ItemDb.keepsakes.size), ui.head)).left().padBottom(10f).row()
        val grid = Table()
        ItemDb.keepsakes.forEachIndexed { i, k ->
            val card = ui.card(k.id == selected) {
                selected = k.id
                rebuild()
            }
            card.add(keepsakeIcon(k.id, 76f)).size(76f)
            grid.add(card).size(CELL).pad(5f)
            if (i % COLUMNS == COLUMNS - 1) grid.row()
        }
        body.add(grid).padBottom(12f).row()

        val def = ItemDb.keepsakes.first { it.id == selected }
        val found = leisure.has(def.id)
        val pane = ui.detailPane()
        pane.add(ui.detailHeader(keepsakeIcon(def.id, 84f), if (found) ui.t("keepsake.${def.id}") else ui.t("shelf.unknown"),
            ui.t(if (found) "shelf.found" else "shelf.not_found"))).growX().padBottom(10f).row()
        val text = if (found) ui.t("keepsake.${def.id}.story") else ui.t("shelf.hint.${def.source.name.lowercase()}")
        pane.add(ui.label(text, ui.text, wrap = true)).growX().padBottom(10f).row()
        pane.add(ui.info("coin", ui.f(if (farm.state.leisure.shelfRewarded) "shelf.reward_done" else "shelf.reward",
            ItemDb.SHELF_REWARD_COINS, ItemDb.SHELF_REWARD_XP), ui.small, Ui.GOLD)).growX()
        body.add(pane).growX()
    }

    /** A found keepsake in colour; a missing one as a soft silhouette. */
    private fun keepsakeIcon(id: String, size: Float) = ui.icon("keepsake_$id", size).also {
        if (!farm.leisure.has(id)) it.color.set(0.35f, 0.25f, 0.16f, 0.3f)
    }

    private companion object {
        const val COLUMNS = 3
        const val CELL = 104f
    }
}

/** Shown when a keepsake turns up: what it is, its little story, and the shelf reward when it completes the set. */
class KeepsakePanel(ctx: GameUi, private val id: String, private val shelfDone: Boolean) :
    Modal(ctx, ctx.ui.t("keepsake.found_title"), "star_gold", prefH = Modal.FIT) {
    override fun fill(body: Table) {
        body.add(ui.icon("keepsake_$id", 128f)).size(128f).padBottom(10f).row()
        body.add(ui.label(ui.t("keepsake.$id"), ui.title, wrap = true, align = Align.center)).growX().padBottom(8f).row()
        body.add(ui.label(ui.t("keepsake.$id.story"), ui.text, wrap = true, align = Align.center)).growX().padBottom(12f).row()
        body.add(Table().apply {
            add(ui.icon("xp", 30f)).size(30f).padRight(8f)
            add(ui.label(ui.f("keepsake.xp", ItemDb.KEEPSAKE_XP), ui.head))
        }).padBottom(6f).row()
        val leisure = farm.leisure
        body.add(ui.label(ui.f("shelf.count", leisure.found, ItemDb.keepsakes.size), ui.soft)).padBottom(12f).row()
        if (shelfDone) {
            body.add(ui.info("star_gold", ui.f("keepsake.shelf_done", ItemDb.SHELF_REWARD_COINS, ItemDb.SHELF_REWARD_XP), ui.head, Ui.GOLD))
                .growX().padBottom(12f).row()
        }
        body.add(ui.button(ui.t("keepsake.keep"), "green", "check") { ctx.close(this) }).growX().height(72f)
    }

    companion object {
        /** Hint lines exist for every source, and the shelf shows them for missing keepsakes. */
        val SOURCES = KeepsakeSource.entries
    }
}
