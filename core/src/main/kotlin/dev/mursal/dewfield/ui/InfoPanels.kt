package dev.mursal.dewfield.ui

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import dev.mursal.dewfield.app.Settings
import dev.mursal.dewfield.i18n.Lang
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.MorningReport
import dev.mursal.dewfield.model.Weather
import kotlin.math.abs
import kotlin.math.roundToInt

private fun weatherIcon(w: Weather) = when (w) {
    Weather.SUNNY -> "sun"
    Weather.CLOUDY -> "cloud"
    Weather.RAIN -> "rain"
}

// ---------------------------------------------------------------------- TV

class TvPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("tv.title"), "info", 980f, 600f) {
    override fun fill(body: Table) {
        val s = farm.state
        val left = ui.detailPane()
        left.add(ui.label(ui.t("tv.forecast"), ui.head)).left().padBottom(10f).row()
        left.add(Table().apply {
            add(ui.icon(weatherIcon(s.tomorrowWeather), 96f)).size(96f).padRight(14f)
            add(ui.label(ui.t("tv.weather.${s.tomorrowWeather.name.lowercase()}"), ui.text, wrap = true)).growX()
        }).growX().padBottom(18f).row()
        s.tomorrowHotItem?.let { hot ->
            left.add(ui.label(ui.t("tv.hot_tomorrow"), ui.head)).left().padBottom(8f).row()
            left.add(Table().apply {
                add(ui.icon(hot, 64f)).size(64f).padRight(12f)
                add(ui.label(ui.f("tv.hot_line", ui.t("item.$hot")), ui.text, wrap = true)).growX()
            }).growX().row()
        }
        left.add().expandY()

        val right = ui.detailPane()
        right.add(ui.label(ui.t("tv.market_news"), ui.head)).left().padBottom(10f).row()
        val movers = ItemDb.sellable.mapNotNull { id -> s.prices[id]?.let { id to (it.mult / it.prevMult - 1f) } }
            .filter { abs(it.second) > 0.02f }.sortedByDescending { abs(it.second) }.take(4)
        if (movers.isEmpty()) right.add(ui.label(ui.t("tv.market_calm"), ui.text, wrap = true)).growX().row()
        for ((id, d) in movers) {
            val pct = (d * 100f).roundToInt()
            right.add(Table().apply {
                add(ui.icon(id, 48f)).size(48f).padRight(10f)
                add(ui.label(ui.t("item.$id"), ui.text)).left().growX()
                add(ui.icon(if (d > 0) "arrow_up" else "arrow_down", 30f)).size(30f).padRight(4f)
                add(ui.label((if (pct > 0) "+" else "") + "$pct%", ui.head).also { it.color = if (d > 0) Ui.GOOD else Ui.BAD })
            }).growX().padBottom(6f).row()
        }
        right.add().expandY().row()
        right.add(ui.label(ui.t("tv.tip"), ui.head)).left().padBottom(6f).row()
        right.add(ui.label(ui.t("tip.${s.day % TIPS}"), ui.text, wrap = true)).growX()

        body.add(left).grow().padRight(12f)
        body.add(right).grow()
    }

    companion object {
        const val TIPS = 12
    }
}

// ---------------------------------------------------------------------- bed

class SleepPanel(ctx: GameUi) : Modal(ctx, ctx.ui.t("sleep.title"), "zzz", 820f, 520f) {
    override fun fill(body: Table) {
        val s = farm.state
        val dry = s.plots.count { it.crop != null && !it.dead && !it.watered && !farm.isRipe(it) }
        val ripe = s.plots.count { farm.isRipe(it) }
        val info = Table().apply { top() }
        info.add(ui.info(weatherIcon(s.tomorrowWeather), ui.f("sleep.tomorrow", ui.t("weather.${s.tomorrowWeather.name.lowercase()}")))).growX().padBottom(10f).row()
        if (dry > 0) info.add(ui.info("water", ui.f(if (s.tomorrowWeather == Weather.RAIN) "sleep.dry_rain" else "sleep.dry", dry), color = if (s.tomorrowWeather == Weather.RAIN) Ui.GOOD else Ui.BAD)).growX().padBottom(10f).row()
        if (ripe > 0) info.add(ui.info("hand", ui.f("sleep.ripe", ripe))).growX().padBottom(10f).row()
        for (t in ItemDb.troughs.values) {
            val need = farm.state.animals.filter { !it.arriving && ItemDb.animal(it.kind).feed == t.feed }.sumOf { ItemDb.animal(it.kind).feedPerDay }
            if (need > 0 && farm.trough(t.id) < need) {
                info.add(ui.info(if (t.feed == "clover") "clover" else t.feed, ui.f("sleep.trough_low", ui.t("trough.${t.id}"), farm.trough(t.id), need), color = Ui.BAD)).growX().padBottom(10f).row()
            }
        }
        if (dry == 0 && ripe == 0) info.add(ui.info("check", ui.t("sleep.all_good"), color = Ui.GOOD)).growX().padBottom(10f).row()
        body.add(info).grow().colspan(2).row()
        body.add(ui.button(ui.t("sleep.not_yet"), "cream") { ctx.close(this) }).growX().height(70f).padRight(10f)
        body.add(ui.button(ui.t("sleep.sleep"), "blue", "zzz") {
            ctx.close(this)
            ctx.sleep()
        }).growX().height(70f)
    }
}

// ---------------------------------------------------------------------- morning report

class MorningPanel(ctx: GameUi, private val r: MorningReport) : Modal(ctx, ctx.ui.f("morning.title", r.day), weatherIcon(r.weather), 900f, 620f) {
    override fun fill(body: Table) {
        val list = Table().apply { top() }
        fun line(icon: String, text: String, color: com.badlogic.gdx.graphics.Color? = null) {
            list.add(ui.info(icon, text, color = color)).growX().padBottom(10f).row()
        }
        if (r.passedOut) line("energy", ui.t("morning.passed_out"), Ui.BAD)
        line(weatherIcon(r.weather), ui.t("morning.weather.${r.weather.name.lowercase()}"))
        if (r.cropsGrew > 0) line("seed", ui.f("morning.grew", r.cropsGrew))
        if (r.cropsRipe > 0) line("hand", ui.f("morning.ripe", r.cropsRipe), Ui.GOOD)
        if (r.cropsWithered > 0) line("close", ui.f("morning.withered", r.cropsWithered), Ui.BAD)
        for ((id, n) in r.products) line(id, ui.f("morning.product", n, ui.t("item.$id")), Ui.GOOD)
        if (r.hungry.isNotEmpty()) line("heart", ui.f("morning.hungry", r.hungry.joinToString(", ")), Ui.BAD)
        if (r.arrived.isNotEmpty()) line("home", ui.f("morning.arrived", r.arrived.joinToString(", ")), Ui.GOOD)
        for ((id, n) in r.machineGoods) line(id, ui.f("morning.machine", n, ui.t("item.$id")))
        if (r.ordersExpired > 0) line("orders", ui.f("morning.orders_expired", r.ordersExpired), Ui.BAD)
        if (r.newOrders > 0) line("orders", ui.f("morning.orders_new", r.newOrders))
        r.hotItem?.let { line("star_gold", ui.f("morning.hot", ui.t("item.$it")), Ui.GOLD) }
        body.add(ui.scroll(list)).grow().row()
        body.add(ui.button(ui.t("morning.go"), "green", "sun") { ctx.close(this) }).width(360f).height(70f).padTop(10f)
    }
}

// ---------------------------------------------------------------------- level up

class LevelUpPanel(ctx: GameUi, private val level: Int, private val unlocks: List<String>) :
    Modal(ctx, ctx.ui.f("level.title", level), "star_gold", 860f, 560f) {
    override fun fill(body: Table) {
        body.add(ui.label(ui.t("level.congrats"), ui.head, wrap = true, align = Align.center)).growX().padBottom(14f).row()
        val grid = Table()
        unlocks.forEachIndexed { i, key ->
            val (kind, id) = key.split('.', limit = 2)
            val icon = when (kind) {
                "animal" -> "animal_$id"
                "machine" -> "machine_$id"
                "upgrade" -> "upgrade_$id"
                else -> id
            }
            grid.add(Table().apply {
                background = ui.inset
                pad(10f)
                add(ui.icon(icon, 64f)).size(64f).row()
                add(ui.label(ui.t(key), ui.small, wrap = true, align = Align.center)).width(150f)
            }).pad(6f)
            if (i % 4 == 3) grid.row()
        }
        if (unlocks.isEmpty()) grid.add(ui.label(ui.t("level.nothing_new"), ui.text))
        body.add(grid).expand().row()
        body.add(ui.button(ui.t("ui.great"), "green", "check") { ctx.close(this) }).width(320f).height(70f)
    }
}

// ---------------------------------------------------------------------- pause menu

class PausePanel(ctx: GameUi, private val settings: Settings) : Modal(ctx, ctx.ui.t("pause.title"), "menu", 900f, 600f) {
    override fun fill(body: Table) {
        val left = Table().apply { top() }
        left.add(ui.button(ui.t("pause.resume"), "green", "play") { ctx.close(this) }).growX().height(70f).padBottom(12f).row()
        left.add(ui.button(ui.t(if (settings.sound) "pause.sound_on" else "pause.sound_off"), "cream", if (settings.sound) "sound_on" else "sound_off") {
            settings.sound = !settings.sound
            rebuild()
        }).growX().height(64f).padBottom(10f).row()
        left.add(ui.button(ui.t(if (settings.music) "pause.music_on" else "pause.music_off"), "cream", "music") {
            settings.music = !settings.music
            rebuild()
        }).growX().height(64f).padBottom(10f).row()
        left.add(ui.button(ui.t("pause.language"), "cream", "globe") {
            settings.lang = if (settings.lang == Lang.EN) Lang.AZ else Lang.EN
            ctx.relabel()
        }).growX().height(64f).padBottom(10f).row()
        left.add().expandY().row()
        left.add(ui.button(ui.t("pause.exit"), "red", "home") { ctx.exitToTitle() }).growX().height(66f)

        val st = farm.state.stats
        val right = ui.detailPane()
        right.add(ui.label(ui.t("pause.stats"), ui.head)).left().padBottom(10f).row()
        fun stat(icon: String, key: String, v: Any) {
            right.add(Table().apply {
                add(ui.icon(icon, 34f)).size(34f).padRight(10f)
                add(ui.label(ui.t(key), ui.text)).left().growX()
                add(ui.label(v.toString(), ui.head))
            }).growX().padBottom(8f).row()
        }
        stat("sun", "stats.day", farm.state.day)
        stat("xp", "stats.level", farm.level)
        stat("coin", "stats.earned", st.earned)
        stat("basket", "stats.harvested", st.harvested)
        stat("egg", "stats.products", st.productsCollected)
        stat("orders", "stats.orders", st.ordersDone)
        stat("heart", "stats.animals", farm.state.animals.size)
        right.add().expandY().row()
        right.add(ui.label(ui.t("pause.autosave"), ui.soft, wrap = true)).growX()

        body.add(left).width(360f).growY().padRight(14f)
        body.add(right).grow()
    }
}
