package dev.mursal.dewfield.ui

import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import dev.mursal.dewfield.model.Category
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.ItemKey

// ---------------------------------------------------------------------- orders board

class OrdersPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("orders.title"), "orders", prefH = Modal.FIT, showMoney = true) {
    override fun fill(body: Table) {
        val orders = farm.state.orders
        if (orders.isEmpty()) {
            body.add(ui.label(ui.t("orders.none"), ui.head, wrap = true, align = Align.center)).growX().pad(20f)
            return
        }
        // One full-width card per order, stacked: the client, what they want, then reward and Deliver.
        val list = Table().apply { top() }
        for (o in orders) {
            val card = Table().apply { background = ui.inset; pad(16f) }
            card.add(Table().apply {
                add(ui.icon("orders", 44f)).size(44f).padRight(10f)
                add(Table().apply {
                    left()
                    add(ui.label(ui.t("client.${o.client}"), ui.head)).left().row()
                    add(ui.label(ui.f("orders.days_left", o.daysLeft), ui.soft).also { if (o.daysLeft <= 1) it.color = Ui.BAD }).left()
                }).left().growX()
            }).growX().padBottom(10f).row()
            for (line in o.lines) {
                val have = farm.countAll(line.item)
                card.add(Table().apply {
                    add(ui.item(line.item, 56f)).size(56f).padRight(10f)
                    add(ui.label(ui.t("item.${line.item}"), ui.text)).left().growX()
                    add(ui.label("${minOf(have, line.qty)}/${line.qty}", ui.head).also { it.color = if (have >= line.qty) Ui.GOOD else Ui.BAD })
                }).growX().padBottom(8f).row()
            }
            card.add(Table().apply {
                add(ui.coins(o.reward)).padRight(16f)
                add(ui.icon("xp", 30f)).size(30f).padRight(4f)
                add(ui.label("+${o.xp}", ui.head)).left().expandX()
                add(ui.button(ui.t("orders.deliver"), "green", "check") {
                    ctx.report(farm.deliver(o.id), "levelup")
                    rebuild()
                }.apply { isDisabled = !farm.canDeliver(o) }).width(250f).height(66f)
            }).growX().padTop(4f)
            list.add(card).growX().padBottom(12f).row()
        }
        body.add(ui.scroll(list)).grow().row()
        body.add(ui.label(ui.t("orders.tip"), ui.soft, wrap = true, align = Align.center)).growX().padTop(8f)
    }
}

// ---------------------------------------------------------------------- machines (mill, press, loom)

class MachinePanel(ctx: GameUi, private val machineId: String) :
    Modal(ctx, ctx.ui.t("machine.$machineId"), "machine_$machineId", prefH = Modal.FIT) {

    override fun fill(body: Table) {
        val def = ItemDb.machine(machineId)
        val state = farm.machine(machineId) ?: return
        val recipes = Table().apply { top() }
        recipes.add(ui.label(ui.t("machine.recipes"), ui.head)).left().padBottom(8f).row()
        for (r in ItemDb.recipesFor(machineId)) {
            val have = farm.countAll(r.input)
            val card = Table().apply { background = ui.inset; pad(12f) }
            card.add(ui.item(r.input, 60f, have)).size(60f)
            card.add(ui.icon("play", 26f)).size(26f).pad(0f, 6f, 0f, 6f)
            val output = Stack()
            output.add(ui.item(r.output, 60f))
            output.add(Table().apply {
                bottom().right()
                add(ui.label("×${r.outputQty}", ui.outline)).padRight(-6f).padBottom(-4f)
            })
            card.add(output).size(60f).padRight(14f)
            val mid = Table().apply { left() }
            mid.add(ui.label(ui.f("machine.makes", ui.t("item.${r.input}"), r.outputQty, ui.t("item.${r.output}")), ui.text, wrap = true)).growX().left().row()
            if (r.keepsQuality) mid.add(ui.label(ui.t("machine.keeps_quality"), ui.soft, wrap = true)).growX().left().padTop(2f)
            card.add(mid).growX().padRight(10f)
            val free = def.slots - state.queue.size
            val buttons = Table()
            buttons.add(ui.button("1", "green", "plus", 52f) {
                ctx.report(farm.loadMachine(machineId, r.id, 1), "pop")
                rebuild()
            }.apply { isDisabled = have <= 0 || free <= 0 }).size(112f, 52f).padBottom(6f).row()
            buttons.add(ui.button(ui.t("ui.all"), "orange", null, 52f) {
                ctx.report(farm.loadMachine(machineId, r.id, minOf(have, free)), "pop")
                rebuild()
            }.apply { isDisabled = have <= 0 || free <= 0 }).size(112f, 52f)
            card.add(buttons)
            recipes.add(card).growX().padBottom(10f).row()
        }

        // What is in the machine sits below the recipes, with Collect where the thumb rests.
        val status = ui.detailPane()
        status.add(ui.label(ui.f("machine.slots", state.queue.size, def.slots), ui.head)).left().padBottom(8f).row()
        val slots = Table()
        for (i in 0 until def.slots) {
            val cell = Table().apply { background = ui.slot }
            state.queue.getOrNull(i)?.let { job -> cell.add(ui.item(ItemKey.of(ItemDb.recipe(job.recipe).input, job.quality), 44f)).size(44f) }
            slots.add(cell).size(60f).pad(3f)
            if (i % SLOTS_PER_ROW == SLOTS_PER_ROW - 1) slots.row()
        }
        status.add(slots).left().padBottom(8f).row()
        status.add(ui.label(ui.t(if (state.queue.isEmpty()) "machine.idle" else "machine.ready_tomorrow"), ui.soft, wrap = true)).growX().padBottom(12f).row()
        status.add(ui.label(ui.t("machine.ready"), ui.head)).left().padBottom(6f).row()
        val ready = Table()
        state.ready.entries.forEachIndexed { i, (k, n) ->
            ready.add(ui.item(k, 56f, n)).size(56f).pad(4f)
            if (i % READY_PER_ROW == READY_PER_ROW - 1) ready.row()
        }
        if (state.ready.isEmpty()) ready.add(ui.label(ui.t("machine.nothing_ready"), ui.soft))
        status.add(ready).left().row()
        status.add(ui.button(ui.t("machine.collect"), "green", "basket") {
            ctx.report(farm.collectMachine(machineId), "harvest")
            rebuild()
        }.apply { isDisabled = state.ready.isEmpty() }).growX().height(66f).padTop(14f)

        body.add(ui.scroll(recipes)).grow().row()
        body.add(status).growX().padTop(12f)
    }

    private companion object {
        const val SLOTS_PER_ROW = 5
        const val READY_PER_ROW = 8
    }
}

// ---------------------------------------------------------------------- stove

class CookPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("cook.title"), "fried_egg", prefH = Modal.FIT) {
    override fun fill(body: Table) {
        body.add(Table().apply {
            add(ui.icon("energy", 36f)).size(36f).padRight(8f)
            add(ui.bar("energy", 260f, 24f) { farm.state.energy / Farm.MAX_ENERGY.toFloat() }).size(260f, 24f).padRight(10f)
            add(ui.label("${farm.state.energy}/${Farm.MAX_ENERGY}", ui.head))
        }).left().padBottom(10f).row()
        val grid = Table()
        for (meal in ItemDb.meals.values) {
            val card = Table().apply { background = ui.inset; pad(12f) }
            card.add(ui.item(meal.id, 72f, farm.count(meal.id).takeIf { it > 0 })).size(72f).padRight(10f)
            val mid = Table().apply { left() }
            mid.add(ui.label(ui.t("item.${meal.id}"), ui.head)).left().row()
            mid.add(Table().apply {
                add(ui.icon("energy", 24f)).size(24f).padRight(4f)
                add(ui.label("+${ItemDb.item(meal.id).energy}", ui.text))
            }).left().row()
            val ing = Table()
            for (id in meal.ingredients) {
                val ok = farm.countAll(id) >= meal.ingredients.count { it == id }
                ing.add(ui.icon(id, 34f).also { if (!ok) it.color.a = 0.35f }).size(34f).padRight(2f)
                ing.add(ui.icon(if (ok) "check" else "close", 18f)).size(18f).padRight(8f).bottom()
            }
            mid.add(ing).left()
            card.add(mid).growX()
            val buttons = Table()
            buttons.add(ui.button(ui.t("cook.cook"), "orange") {
                ctx.report(farm.cook(meal.id), "cook")
                rebuild()
            }.apply { isDisabled = !farm.canCook(meal.id) }).width(150f).height(56f).row()
            if (farm.count(meal.id) > 0) buttons.add(ui.button(ui.t("bag.eat"), "green", "energy") {
                ctx.report(farm.eat(meal.id), "cook")
                rebuild()
            }.apply { isDisabled = farm.state.energy >= Farm.MAX_ENERGY }).width(150f).height(56f).padTop(6f)
            card.add(buttons)
            grid.add(card).growX().padBottom(10f).row()
        }
        body.add(ui.scroll(grid)).grow()
    }
}

// ---------------------------------------------------------------------- bag: items and animals

class BagPanel(ctx: GameUi) : ListModal(ctx, ctx.ui.t("bag.title"), "bag") {
    private var animalsTab = false
    private var selected: String? = null

    private fun keys(): List<String> = farm.state.inventory.keys.filter { (farm.state.inventory[it] ?: 0) > 0 }
        .sortedWith(compareBy({ CATEGORY_ORDER.indexOf(ItemDb.item(ItemKey.id(it)).category) }, { ItemKey.id(it) }, { ItemKey.quality(it) }))

    override fun fill(body: Table) {
        val wrap = Table()
        val tabs = Table()
        tabs.add(ui.tab(ui.t("bag.tab_items"), !animalsTab) { animalsTab = false; ld.scrollY = 0f; rebuild() }).height(58f).padRight(8f)
        tabs.add(ui.tab(ui.t("bag.tab_animals"), animalsTab) { animalsTab = true; ld.scrollY = 0f; rebuild() }).height(58f)
        wrap.add(tabs).left().padBottom(10f).row()
        val inner = Table()
        if (animalsTab) animals(inner) else items(inner)
        wrap.add(inner).grow()
        body.add(wrap).grow()
    }

    private fun items(inner: Table) {
        val keys = keys()
        if (selected !in keys) selected = keys.firstOrNull()
        if (keys.isEmpty()) {
            inner.add(ui.label(ui.t("bag.empty"), ui.head))
            return
        }
        val grid = Table()
        keys.forEachIndexed { i, key ->
            val card = ui.card(key == selected) { selected = key; refreshKeep() }
            card.add(ui.item(key, 60f, farm.count(key))).size(60f)
            grid.add(card).size(GRID_CELL).pad(4f)
            if (i % GRID_COLUMNS == GRID_COLUMNS - 1) grid.row()
        }
        ld.build(inner, grid, itemDetail(), DETAIL_BAG)
    }

    private fun itemDetail(): Table {
        val d = ui.detailPane()
        val key = selected ?: return d
        val id = ItemKey.id(key)
        val def = ItemDb.item(id)
        d.add(ui.detailHeader(ui.item(key, 84f), ui.t("item.$id"), ui.t("category.${def.category.name.lowercase()}") + if (def.hasQuality) " • " + ui.t("quality.${ItemKey.quality(key)}") else "")).growX().padBottom(10f).row()
        val info = ui.lines()
        info.line(ui.info("bag", ui.f("ui.you_have", farm.count(key))))
        if (def.sellPrice > 0) info.line(ui.info("coin", ui.f("bag.sells_now", farm.priceOf(key))))
        if (def.energy > 0) info.line(ui.info("energy", ui.f("shop.edible", def.energy)))
        ItemDb.cropBySeed[id]?.let { info.line(ui.info("clock", ui.f("shop.grows", it.growDays))) }
        if (def.category == Category.FEED || id == "clover") {
            val eater = ItemDb.animals.values.firstOrNull { (ItemDb.feedAliases[it.feed] ?: listOf(it.feed)).contains(id) }
            if (eater != null) info.line(ui.info("animal_${eater.id}", ui.f("bag.feed_for", ui.t("animal.${eater.id}"), ui.t("trough.${ItemDb.troughByFeed.getValue(eater.feed).id}"))))
        }
        d.add(ui.scroll(info)).grow().padBottom(10f).row()
        val buttons = Table()
        if (def.energy > 0) buttons.add(ui.button(ui.t("bag.eat"), "green", "energy") {
            ctx.report(farm.eat(key), "cook")
            refreshKeep()
        }.apply { isDisabled = farm.state.energy >= Farm.MAX_ENERGY }).growX().height(66f).padRight(8f)
        if (def.category == Category.SEED || def.category == Category.SUPPLY) {
            val sel = farm.state.selected == key
            buttons.add(ui.button(ui.t(if (sel) "bag.selected" else "bag.select"), if (sel) "grey" else "blue", "seed") {
                farm.select(key)
                ctx.refreshHud()
                refreshKeep()
            }).growX().height(66f)
        }
        d.add(buttons).growX()
        return d
    }

    private fun animals(inner: Table) {
        val list = Table().apply { top() }
        if (farm.state.animals.isEmpty()) list.add(ui.label(ui.t("bag.no_animals"), ui.head))
        for (a in farm.state.animals) {
            val def = ItemDb.animal(a.kind)
            val card = Table().apply { background = ui.inset; pad(10f, 14f, 10f, 14f) }
            card.add(ui.icon("animal_${a.kind}", 60f)).size(60f).padRight(12f)
            val mid = Table().apply { left() }
            mid.add(ui.label(a.name, ui.head)).left().padRight(10f)
            mid.add(ui.label(ui.t("animal.${a.kind}"), ui.soft)).left().expandX().row()
            val hearts = Table()
            for (i in 0 until Farm.MAX_MOOD / 2) {
                val filled = a.mood >= (i + 1) * 2
                val half = !filled && a.mood >= i * 2 + 1
                hearts.add(ui.icon("heart", 26f).also { it.color.a = if (filled) 1f else if (half) 0.6f else 0.2f }).size(26f).padRight(2f)
            }
            mid.add(hearts).left().colspan(2)
            card.add(mid).growX()
            val status = Table()
            when {
                a.arriving -> status.add(ui.info("clock", ui.t("bag.arriving"), ui.small)).width(300f).row()
                else -> {
                    status.add(ui.info(if (a.fedLastNight) "check" else "close", ui.t(if (a.fedLastNight) "bag.fed" else "bag.hungry"), ui.small, if (a.fedLastNight) Ui.GOOD else Ui.BAD)).width(300f).row()
                    status.add(ui.info("hand", ui.t(if (a.pettedToday) "bag.petted" else "bag.not_petted"), ui.small)).width(300f).row()
                    status.add(ui.info(def.product, ui.f("bag.product_progress", a.productProgress, def.productEvery), ui.small)).width(300f).row()
                }
            }
            card.add(status)
            list.add(card).growX().padBottom(8f).row()
        }
        val tips = ui.detailPane()
        tips.add(ui.label(ui.t("bag.care_title"), ui.head)).left().padBottom(8f).row()
        val lines = ui.lines()
        for (k in listOf("bag.care_feed", "bag.care_pet", "bag.care_mood")) lines.line(ui.info("heart", ui.t(k), ui.small))
        for (t in ItemDb.troughs.values) lines.line(ui.info(if (t.feed == "clover") "clover" else t.feed, ui.f("bag.trough_line", ui.t("trough.${t.id}"), farm.trough(t.id), t.capacity), ui.small))
        tips.add(ui.scroll(lines)).grow()
        ld.build(inner, list, tips, 360f)
    }
}
