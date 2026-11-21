package dev.mursal.dewfield.ui

import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import dev.mursal.dewfield.model.Category
import dev.mursal.dewfield.model.Housing
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.ItemKey
import dev.mursal.dewfield.model.Msg
import kotlin.math.roundToInt

/** A list-and-detail panel that keeps its scroll position across refreshes. */
abstract class ListModal(ctx: GameUi, title: String, icon: String?, money: Boolean = true) :
    Modal(ctx, title, icon, showMoney = money) {
    protected val ld = ListDetail(ctx.ui)

    protected fun refreshKeep() {
        ld.remember()
        rebuild()
        ld.restore(this)
    }

    override fun shown() {
        ld.scrollY = 0f
        rebuild()
    }
}

// ---------------------------------------------------------------------- Nargiz: seeds

class SeedShopPanel(ctx: GameUi) : ListModal(ctx, ctx.ui.t("shop.seeds.title"), "seed") {
    private var selected = ItemDb.shopSeeds.first()
    private var qty = 1

    override fun fill(body: Table) {
        val list = Table()
        for (id in ItemDb.shopSeeds) {
            val def = ItemDb.item(id)
            val crop = ItemDb.cropBySeed[id]
            val locked = !farm.isUnlocked(id)
            val sub = when {
                locked -> ui.f("ui.unlocks_at", def.unlockLevel)
                crop != null -> ui.f("shop.seed_sub", crop.growDays, ItemDb.item(crop.produce).sellPrice)
                else -> ui.t("shop.fert_sub")
            }
            list.add(ui.row(id == selected, id, ui.t("item.$id"), sub, ui.coins(def.buyPrice), locked) {
                selected = id
                qty = 1
                refreshKeep()
            }).growX().padBottom(8f).row()
        }
        ld.build(body, list, detail(), DETAIL_BUY)
    }

    private fun detail(): Table {
        val d = ui.detailPane()
        val def = ItemDb.item(selected)
        val crop = ItemDb.cropBySeed[selected]
        d.add(ui.detailHeader(ui.item(selected, 84f), ui.t("item.$selected"), ui.f("ui.you_have", farm.count(selected)))).growX().padBottom(10f).row()
        val info = ui.lines()
        if (crop != null) {
            val produce = ItemDb.item(crop.produce)
            info.line(ui.info("clock", ui.f("shop.grows", crop.growDays)))
            if (crop.regrowDays > 0) info.line(ui.info("plus", ui.f("shop.regrows", crop.regrowDays), color = Ui.GOOD))
            info.line(ui.info(crop.produce, ui.f("shop.yield", range(crop.yieldMin, crop.yieldMax), ui.t("item.${crop.produce}"))))
            info.line(ui.info("coin", ui.f("shop.sells_for", produce.sellPrice)))
            info.line(ui.info("water", ui.f("shop.withers", crop.witherDays), color = if (crop.witherDays <= 2) Ui.BAD else null))
            val avg = (crop.yieldMin + crop.yieldMax) / 2f
            val perDay = ((produce.sellPrice * avg - def.buyPrice) / crop.growDays).roundToInt()
            info.line(ui.info("star_gold", ui.f("shop.profit", perDay), color = Ui.GOLD))
        } else {
            info.line(ui.info("star_gold", ui.t("shop.fert_info")))
        }
        d.add(ui.scroll(info)).grow().padBottom(10f).row()
        if (!farm.isUnlocked(selected)) {
            d.add(ui.info("lock", ui.f("ui.unlocks_at", def.unlockLevel), ui.head, Ui.BAD)).growX()
            return d
        }
        val max = (farm.state.money / def.buyPrice).coerceIn(1, 99)
        qty = qty.coerceIn(1, max)
        d.add(ui.stepper(qty, max) { qty = it; refreshKeep() }).padBottom(10f).row()
        val total = def.buyPrice * qty
        d.add(ui.button(ui.f("shop.buy_for", total), "green", "coin") {
            if (ctx.report(farm.buy(selected, qty))) qty = 1
            refreshKeep()
        }.apply { isDisabled = farm.state.money < total }).growX().height(68f)
        return d
    }
}

// ---------------------------------------------------------------------- Orkhan: feed and animals

class FeedShopPanel(ctx: GameUi) : ListModal(ctx, ctx.ui.t("shop.feed.title"), "grain") {
    private var animals = false
    private var selected = ItemDb.shopFeed.first()
    private var qty = 5

    override fun fill(body: Table) {
        val wrap = Table()
        val tabs = Table()
        tabs.add(ui.tab(ui.t("shop.tab_feed"), !animals) { animals = false; selected = ItemDb.shopFeed.first(); qty = 5; ld.scrollY = 0f; rebuild() }).height(58f).padRight(8f)
        tabs.add(ui.tab(ui.t("shop.tab_animals"), animals) { animals = true; selected = "chicken"; ld.scrollY = 0f; rebuild() }).height(58f)
        wrap.add(tabs).left().padBottom(10f).row()
        val inner = Table()
        val list = Table()
        if (!animals) {
            for (id in ItemDb.shopFeed) {
                val def = ItemDb.item(id)
                val locked = !farm.isUnlocked(id)
                val eater = ItemDb.animals.values.first { (ItemDb.feedAliases[it.feed] ?: listOf(it.feed)).contains(id) }
                val sub = if (locked) ui.f("ui.unlocks_at", def.unlockLevel) else ui.f("shop.feed_for", ui.t("animal.${eater.id}"))
                list.add(ui.row(id == selected, id, ui.t("item.$id"), sub, ui.coins(def.buyPrice), locked) {
                    selected = id
                    qty = 5
                    refreshKeep()
                }).growX().padBottom(8f).row()
            }
            ld.build(inner, list, feedDetail(), DETAIL_FEED)
        } else {
            for (def in ItemDb.animals.values) {
                val locked = !farm.isUnlocked(def.id)
                val sub = if (locked) ui.f("ui.unlocks_at", def.unlockLevel) else ui.f("shop.animal_sub", ui.t("item.${def.product}"))
                val iconRow = Table().apply { add(ui.coins(def.price)) }
                list.add(ui.row(def.id == selected, "animal_${def.id}", ui.t("animal.${def.id}"), sub, iconRow, locked) {
                    selected = def.id
                    refreshKeep()
                }).growX().padBottom(8f).row()
            }
            ld.build(inner, list, animalDetail(), DETAIL_FEED)
        }
        wrap.add(inner).grow()
        body.add(wrap).grow()
    }

    private fun feedDetail(): Table {
        val d = ui.detailPane()
        val def = ItemDb.item(selected)
        val eater = ItemDb.animals.values.first { (ItemDb.feedAliases[it.feed] ?: listOf(it.feed)).contains(selected) }
        val trough = ItemDb.troughByFeed.getValue(eater.feed)
        d.add(ui.detailHeader(ui.item(selected, 84f), ui.t("item.$selected"), ui.f("ui.you_have", farm.countAll(selected)))).growX().padBottom(10f).row()
        val info = ui.lines()
        info.line(ui.info("animal_${eater.id}", ui.f("shop.feed_eaten_by", ui.t("animal.${eater.id}"), eater.feedPerDay)))
        info.line(ui.info("basket", ui.f("shop.feed_trough", ui.t("trough.${trough.id}"), farm.trough(trough.id), trough.capacity)))
        if (selected == "clover_mix") info.line(ui.info("clover", ui.t("shop.clover_tip"), color = Ui.GOOD))
        if (selected == "hay") info.line(ui.info("wheat", ui.t("shop.hay_tip"), color = Ui.GOOD))
        if (selected == "grain") info.line(ui.info("corn", ui.t("shop.grain_tip"), color = Ui.GOOD))
        d.add(ui.scroll(info)).grow().padBottom(10f).row()
        if (!farm.isUnlocked(selected)) {
            d.add(ui.info("lock", ui.f("ui.unlocks_at", def.unlockLevel), ui.head, Ui.BAD)).growX()
            return d
        }
        val max = (farm.state.money / def.buyPrice).coerceIn(1, 99)
        qty = qty.coerceIn(1, max)
        d.add(ui.stepper(qty, max) { qty = it; refreshKeep() }).padBottom(10f).row()
        val total = def.buyPrice * qty
        d.add(ui.button(ui.f("shop.buy_for", total), "green", "coin") {
            ctx.report(farm.buy(selected, qty))
            refreshKeep()
        }.apply { isDisabled = farm.state.money < total }).growX().height(68f)
        return d
    }

    private fun animalDetail(): Table {
        val d = ui.detailPane()
        val def = ItemDb.animal(selected)
        val housing = if (def.housing == Housing.COOP) "coop" else "barn"
        d.add(ui.detailHeader(ui.icon("animal_${def.id}", 84f), ui.t("animal.${def.id}"), ui.f("shop.owned", farm.state.animals.count { it.kind == def.id }))).growX().padBottom(10f).row()
        val info = ui.lines()
        val feedItem = if (def.feed == "clover") "clover" else def.feed
        info.line(ui.info(feedItem, ui.f("shop.eats", def.feedPerDay, ui.t("feed.${def.feed}"))))
        info.line(ui.info(def.product, if (def.productEvery == 1) ui.f("shop.gives_daily", ui.t("item.${def.product}")) else ui.f("shop.gives_every", ui.t("item.${def.product}"), def.productEvery)))
        info.line(ui.info("home", ui.f("shop.lives_in", ui.t("building.$housing"), farm.animalsIn(def.housing).size, farm.capacity(def.housing))))
        info.line(ui.info("heart", ui.t("shop.mood_tip")))
        d.add(ui.scroll(info)).grow().padBottom(10f).row()
        if (!farm.isUnlocked(def.id)) {
            d.add(ui.info("lock", ui.f("ui.unlocks_at", def.unlockLevel), ui.head, Ui.BAD)).growX()
            return d
        }
        d.add(ui.label(ui.t("shop.arrives_tomorrow"), ui.soft)).padBottom(8f).row()
        d.add(ui.button(ui.f("shop.buy_for", def.price), "green", "coin") {
            ctx.report(farm.buyAnimal(def.id))
            refreshKeep()
        }.apply { isDisabled = farm.state.money < def.price }).growX().height(68f)
        return d
    }
}

// ---------------------------------------------------------------------- Leyla: selling

class SellPanel(ctx: GameUi) : ListModal(ctx, ctx.ui.t("shop.sell.title"), "coin") {
    private var selected: String? = null
    private var qty = 1

    private fun sellable(): List<String> = farm.state.inventory.keys
        .filter { (farm.state.inventory[it] ?: 0) > 0 && ItemDb.items[ItemKey.id(it)]?.sellPrice ?: 0 > 0 }
        .sortedWith(compareBy({ ItemDb.item(ItemKey.id(it)).category.ordinal }, { ItemKey.id(it) }, { ItemKey.quality(it) }))

    override fun fill(body: Table) {
        val keys = sellable()
        if (selected !in keys) {
            selected = keys.firstOrNull()
            qty = 1
        }
        val wrap = Table()
        val inner = Table()
        if (keys.isEmpty()) {
            inner.add(ui.label(ui.t("shop.nothing_to_sell"), ui.head, wrap = true, align = Align.center)).growX().pad(20f)
            wrap.add(inner).grow()
            body.add(wrap).grow()
            return
        }
        val grid = Table()
        keys.forEachIndexed { i, key ->
            val card = ui.card(key == selected) {
                selected = key
                qty = 1
                refreshKeep()
            }
            val cell = Stack()
            cell.add(ui.item(key, 64f, farm.count(key)))
            // Today's hot item sells for more: mark it so it stands out in the grid.
            if (ItemKey.id(key) == farm.state.hotItem) cell.add(Table().apply {
                top().right()
                add(ui.icon("arrow_up", 28f)).size(28f).padTop(-14f).padRight(-14f)
            })
            card.add(cell).size(64f)
            grid.add(card).size(GRID_CELL).pad(4f)
            if (i % GRID_COLUMNS == GRID_COLUMNS - 1) grid.row()
        }
        ld.build(inner, grid, detail(), DETAIL_SELL)
        wrap.add(inner).grow()
        body.add(wrap).grow()
    }

    private fun detail(): Table {
        val d = ui.detailPane()
        val key = selected ?: return d
        val id = ItemKey.id(key)
        val q = ItemKey.quality(key)
        val have = farm.count(key)
        d.add(ui.detailHeader(ui.item(key, 84f), ui.t("item.$id"), ui.t("quality.$q") + " • " + ui.f("ui.you_have", have))).growX().padBottom(10f).row()
        val info = ui.lines()
        val price = farm.priceOf(key)
        val trend = farm.trend(id)
        val pct = farm.state.prices[id]?.let { ((it.mult / it.prevMult - 1f) * 100f).roundToInt() } ?: 0
        info.line(Table().apply {
            left()
            add(ui.icon("coin", 30f)).size(30f).padRight(8f)
            add(ui.label(ui.f("shop.price_each", price), ui.head)).padRight(10f)
            add(ui.icon(if (trend > 0) "arrow_up" else if (trend < 0) "arrow_down" else "arrow_flat", 30f)).size(30f).padRight(4f)
            if (trend != 0) add(ui.label((if (pct > 0) "+" else "") + "$pct%", ui.text).also { it.color = if (trend > 0) Ui.GOOD else Ui.BAD })
        })
        if (farm.state.hotItem == id) info.line(ui.info("star_gold", ui.t("shop.hot_bonus"), color = Ui.GOLD))
        if (ItemDb.item(id).hasQuality) info.line(ui.info("star_silver", ui.t("shop.quality_prices"), ui.small))
        info.line(ui.info("arrow_down", ui.t("shop.saturation"), ui.small))
        val energy = ItemDb.item(id).energy
        if (energy > 0) info.line(ui.info("energy", ui.f("shop.edible", energy), ui.small))
        d.add(ui.scroll(info)).grow().padBottom(10f).row()
        qty = qty.coerceIn(1, have.coerceAtLeast(1))
        d.add(ui.stepper(qty, have) { qty = it; refreshKeep() }).padBottom(10f).row()
        val buttons = Table()
        buttons.add(ui.button(ui.f("shop.sell_for", farm.quote(key, qty)), "green", "coin") {
            ctx.report(farm.sell(key, qty))
            qty = 1
            refreshKeep()
        }).growX().height(68f).padRight(8f)
        buttons.add(ui.button(ui.f("shop.sell_all", farm.quote(key, have)), "orange") {
            ctx.report(farm.sell(key, have))
            qty = 1
            refreshKeep()
        }).growX().height(68f)
        d.add(buttons).growX()
        return d
    }
}

// ---------------------------------------------------------------------- Tural: upgrades and machines

class WorkshopPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("shop.workshop.title"), "hoe", prefH = Modal.FIT, showMoney = true) {
    override fun fill(body: Table) {
        val grid = Table()
        for (def in ItemDb.upgrades.values) {
            val lv = farm.upgradeLevel(def.id)
            val now = def.levels[lv - 1].value
            val next = def.levels.getOrNull(lv)
            val card = Table().apply { background = ui.inset; pad(12f) }
            card.add(ui.icon("upgrade_${def.id}", 64f)).size(64f).padRight(10f)
            val mid = Table().apply { left() }
            mid.add(ui.label(ui.t("upgrade.${def.id}"), ui.head)).left().row()
            val line = if (next != null) ui.f("upgrade.${def.id}.step", now, next.value) else ui.f("upgrade.${def.id}.max", now)
            mid.add(ui.label(line, ui.small, wrap = true)).growX().left()
            card.add(mid).growX()
            card.add(action(next?.price, next?.unlockLevel, owned = next == null) { ctx.report(farm.buyUpgrade(def.id), "levelup") }).width(190f).height(64f)
            grid.add(card).growX().minHeight(110f).padBottom(10f).row()
        }
        for (def in ItemDb.machines.values.filter { it.price > 0 }) {
            val card = Table().apply { background = ui.inset; pad(12f) }
            card.add(ui.icon("machine_${def.id}", 64f)).size(64f).padRight(10f)
            val mid = Table().apply { left() }
            mid.add(ui.label(ui.t("machine.${def.id}"), ui.head)).left().row()
            mid.add(ui.label(ui.t("machine.${def.id}.about"), ui.small, wrap = true)).growX().left()
            card.add(mid).growX()
            card.add(action(def.price, def.unlockLevel, farm.owns(def.id)) { ctx.report(farm.buyMachine(def.id), "levelup") }).width(190f).height(64f)
            grid.add(card).growX().minHeight(110f).padBottom(10f).row()
        }
        body.add(ui.scroll(grid)).grow()
    }

    private fun action(price: Int?, level: Int?, owned: Boolean, buy: () -> Unit): Table {
        val t = Table()
        when {
            owned -> t.add(ui.label(ui.t("ui.owned"), ui.head).also { it.color = Ui.GOOD })
            level != null && farm.level < level -> t.add(Table().apply {
                add(ui.icon("lock", 32f)).size(32f).padRight(6f)
                add(ui.label(ui.f("ui.level_short", level), ui.head).also { it.color = Ui.BAD })
            })
            price != null -> t.add(ui.button(price.toString(), "green", "coin") {
                buy()
                rebuild()
            }.apply { isDisabled = farm.state.money < price }).grow()
        }
        return t
    }
}

/** Heights of the detail panes under the lists: tall enough for the info lines and the buttons. */
internal const val DETAIL_BUY = 500f
internal const val DETAIL_FEED = 470f
internal const val DETAIL_SELL = 520f
internal const val DETAIL_BAG = 430f

/** Item grids (sell, bag): six cells across the portrait panel. */
internal const val GRID_COLUMNS = 6
internal const val GRID_CELL = 94f

/** Category order used by the bag. */
internal val CATEGORY_ORDER = listOf(Category.SEED, Category.SUPPLY, Category.FEED, Category.CROP, Category.ANIMAL_PRODUCT, Category.ARTISAN, Category.MEAL)

internal fun Msg.textOf(ui: Ui) = ui.m(this)
