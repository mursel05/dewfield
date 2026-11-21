package dev.mursal.dewfield.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.Gain
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.model.ItemKey
import dev.mursal.dewfield.model.Weather
import dev.mursal.dewfield.world.Locations
import dev.mursal.dewfield.world.Task
import dev.mursal.dewfield.world.World
import kotlin.math.roundToInt

/**
 * The always-on overlay: clock and weather, coins, energy, level, the seed bar, the water can,
 * the task queue, and buttons for orders, bag and menu. Laid out against the screen edges so it
 * works on any landscape aspect ratio.
 */
class Hud(private val ctx: GameUi, private val onOrders: () -> Unit, private val onBag: () -> Unit, private val onMenu: () -> Unit) : Group() {
    private val ui get() = ctx.ui
    private val farm get() = ctx.farm
    private val world get() = ctx.world

    private val topLeft = Table()
    private val topRight = Table()
    private val bottom = Table()
    private val queueChip = Table()
    private val marketChip = Table()

    private lateinit var dayLabel: Label
    private lateinit var timeLabel: Label
    private lateinit var weatherCell: Container<com.badlogic.gdx.scenes.scene2d.ui.Image>
    private lateinit var moneyLabel: Label
    private lateinit var levelLabel: Label
    private lateinit var energyLabel: Label
    private lateinit var queueLabel: Label
    private lateinit var ordersBadge: Table
    private lateinit var ordersBadgeLabel: Label
    private lateinit var marketLabel: Label

    private var shownMoney = 0f
    private var lastMinute = -1
    private var lastWeather: Weather? = null
    private var barSignature = ""
    private var refreshIn = 0f
    private var hidden = false

    init {
        touchable = Touchable.childrenOnly
        addActor(topLeft)
        addActor(topRight)
        addActor(bottom)
        addActor(queueChip)
        addActor(marketChip)
        build()
    }

    /** Rebuilds every widget (after a language change). */
    fun build() {
        topLeft.clear()
        topRight.clear()
        queueChip.clear()
        marketChip.clear()
        barSignature = ""
        shownMoney = farm.state.money.toFloat()
        lastMinute = -1
        lastWeather = null

        val clock = Table().apply { background = ui.pill }
        weatherCell = Container(ui.icon("sun", 40f)).size(40f)
        clock.add(weatherCell).padRight(8f)
        dayLabel = ui.label("", ui.head)
        timeLabel = ui.label("", ui.head)
        clock.add(dayLabel).padRight(12f)
        clock.add(timeLabel).width(78f)

        val money = Table().apply { background = ui.pill }
        money.add(ui.icon("coin", 38f)).size(38f).padRight(8f)
        moneyLabel = ui.label("", ui.head)
        money.add(moneyLabel).minWidth(80f)

        val energy = Table().apply { background = ui.pill }
        energy.add(ui.icon("energy", 38f)).size(38f).padRight(6f)
        energy.add(ui.bar("energy", 130f, 22f) { farm.state.energy / Farm.MAX_ENERGY.toFloat() }).size(130f, 22f).padRight(8f)
        energyLabel = ui.label("", ui.small)
        energy.add(energyLabel).width(36f)

        val level = Table().apply { background = ui.pill }
        level.add(ui.icon("xp", 38f)).size(38f).padRight(6f)
        levelLabel = ui.label("", ui.head)
        level.add(levelLabel).padRight(8f)
        level.add(ui.bar("xp", 100f, 18f) { farm.levelProgress()?.let { (a, b) -> a / b.toFloat() } ?: 1f }).size(100f, 18f)

        topLeft.left()
        topLeft.defaults().padRight(10f).height(60f)
        topLeft.add(clock)
        topLeft.add(money)
        topLeft.add(energy)
        topLeft.add(level)
        topLeft.pack()

        val orders = ui.roundButton("orders", 74f) { onOrders() }
        ordersBadgeLabel = ui.label("", ui.small.let { Label.LabelStyle(it.font, Color.WHITE) }, align = Align.center)
        ordersBadge = Table().apply {
            background = ui.badge
            add(ordersBadgeLabel).size(26f)
            touchable = Touchable.disabled
        }
        val ordersGroup = Group().apply {
            setSize(74f, 74f)
            addActor(orders)
            addActor(ordersBadge)
            ordersBadge.setBounds(50f, 50f, 28f, 28f)
        }
        topRight.add(ordersGroup).size(74f).padLeft(10f)
        topRight.add(ui.roundButton("bag", 74f) { onBag() }).size(74f).padLeft(10f)
        topRight.add(ui.roundButton("menu", 74f) { onMenu() }).size(74f).padLeft(10f)
        topRight.pack()

        queueChip.background = ui.pill
        queueChip.add(ui.icon("queue", 36f)).size(36f).padRight(6f)
        queueLabel = ui.label("", ui.head)
        queueChip.add(queueLabel).padRight(10f)
        queueChip.add(ui.roundButton("close", 52f) {
            world.clearTasks()
            update(0f)
        }).size(52f)
        queueChip.pack()

        marketChip.background = ui.pill
        marketChip.add(ui.icon("clock", 32f)).size(32f).padRight(6f)
        marketLabel = ui.label("", ui.text)
        marketChip.add(marketLabel)

        refresh()
        layoutTo()
    }

    /** Re-reads inventory-dependent widgets (seed bar, badges). */
    fun refresh() {
        val items = world.barItems()
        val signature = items.joinToString { "$it:${farm.count(it)}" } + "|" + farm.state.selected + "|" + world.location.id +
            "|" + farm.state.canWater + "/" + farm.canCapacity
        if (signature != barSignature) {
            barSignature = signature
            buildBottom(items)
        }
        val deliverable = farm.state.orders.count { farm.canDeliver(it) }
        ordersBadge.isVisible = deliverable > 0
        ordersBadgeLabel.setText(deliverable.toString())
    }

    private fun buildBottom(items: List<String>) {
        bottom.clear()
        if (world.location.id != Locations.FARM) {
            bottom.isVisible = false
            return
        }
        bottom.isVisible = true
        val can = Table().apply { background = ui.pill }
        can.add(ui.icon("can", 44f)).size(44f).padRight(6f)
        val canTable = Table()
        canTable.add(ui.label("${farm.state.canWater}/${farm.canCapacity}", ui.small)).row()
        canTable.add(ui.bar("water", 86f, 16f) { farm.state.canWater / farm.canCapacity.toFloat() }).size(86f, 16f)
        can.add(canTable)
        bottom.add(can).height(76f).padRight(12f)

        val bar = Table().apply { background = ui.pill }
        if (items.isEmpty()) {
            bar.add(ui.icon("seed", 40f)).size(40f).padRight(8f)
            bar.add(ui.label(ui.t("hud.no_seeds"), ui.text)).padRight(6f)
        }
        for (id in items) {
            val sel = farm.state.selected == id
            val slot = ui.card(sel) {
                farm.select(if (farm.state.selected == id) null else id)
                refresh()
            }
            slot.add(ui.item(id, 50f, farm.count(id))).size(50f)
            bar.add(slot).size(70f).pad(0f, 3f, 0f, 3f)
        }
        bottom.add(bar).height(84f)
        bottom.pack()
        layoutTo()
    }

    fun update(dt: Float) {
        val s = farm.state
        val minute = s.minutes.toInt()
        if (minute != lastMinute) {
            lastMinute = minute
            dayLabel.setText(ui.f("hud.day", s.day))
            timeLabel.setText(World.hhmm(minute - minute % 10))
            val open = farm.isMarketOpen
            marketLabel.setText(if (open) ui.f("hud.market_open", World.hhmm(Farm.MARKET_CLOSE)) else ui.f("hud.market_closed", World.hhmm(Farm.MARKET_OPEN)))
            marketLabel.color = if (open) Ui.GOOD else Ui.BAD
            marketChip.pack()
        }
        if (s.weather != lastWeather) {
            lastWeather = s.weather
            weatherCell.actor = ui.icon(when (s.weather) {
                Weather.SUNNY -> "sun"
                Weather.CLOUDY -> "cloud"
                Weather.RAIN -> "rain"
            }, 40f)
        }
        // Coins count up smoothly instead of jumping.
        val target = s.money.toFloat()
        shownMoney += (target - shownMoney) * (1f - kotlin.math.exp(-dt * 10f))
        if (kotlin.math.abs(target - shownMoney) < 0.5f) shownMoney = target
        moneyLabel.setText(shownMoney.roundToInt().toString())
        energyLabel.setText(s.energy.toString())
        energyLabel.color = if (s.energy < 15) Ui.BAD else Ui.INK
        levelLabel.setText(ui.f("hud.level", farm.level))

        val queued = world.queue.count { it is Task.Plot } + if (world.current is Task.Plot) 1 else 0
        queueChip.isVisible = queued > 1
        queueLabel.setText(queued.toString())
        marketChip.isVisible = world.location.id == Locations.MARKET
        // Label texts change width (day 9 -> 10, 999 -> 1000 coins); keep the pills fitted.
        if (topLeft.prefWidth != topLeft.width || topLeft.prefHeight != topLeft.height) layoutTo()
        refreshIn -= dt
        if (refreshIn <= 0f) {
            refreshIn = 0.2f
            refresh()
        }
    }

    /** Fades the HUD out while a panel is open: panels show the coins themselves. */
    fun setHidden(hide: Boolean) {
        if (hide == hidden) return
        hidden = hide
        clearActions()
        addAction(if (hide) Actions.fadeOut(0.12f) else Actions.fadeIn(0.18f))
        touchable = if (hide) Touchable.disabled else Touchable.childrenOnly
    }

    fun layoutTo() {
        val s = stage ?: return
        setSize(s.width, s.height)
        refreshIn = 0f
        val m = 16f
        topLeft.pack()
        topLeft.setPosition(m, s.height - m - topLeft.height)
        topRight.pack()
        topRight.setPosition(s.width - m - topRight.width, s.height - m - topRight.height)
        bottom.pack()
        bottom.setPosition((s.width - bottom.width) / 2f, m)
        queueChip.pack()
        queueChip.setPosition(m, m + 4f)
        marketChip.pack()
        marketChip.setPosition(m, s.height - m - topLeft.height - 10f - marketChip.height)
    }
}

/** Short messages that stack at the top of the screen and fade away. */
class Toasts(private val ui: Ui) : Table() {
    private var last = ""
    private var lastTime = 0L

    /** Widest a toast may get before it wraps. */
    var maxTextWidth = 720f

    init {
        touchable = Touchable.disabled
        top()
    }

    fun show(text: String) {
        val now = System.currentTimeMillis()
        if (text == last && now - lastTime < 1500L) return
        last = text
        lastTime = now
        while (children.size >= 3) children.first().remove()
        val label = ui.label(text, ui.text, wrap = true, align = Align.center)
        // A wrapping label reports no preferred width, so measure the text itself.
        val natural = GlyphLayout(ui.text.font, text).width
        val pill = Table().apply {
            background = ui.pill
            add(label).width(minOf(maxTextWidth, natural + 8f)).pad(4f, 10f, 4f, 10f)
        }
        val holder = Container(pill)
        holder.isTransform = true
        holder.color.a = 0f
        holder.addAction(Actions.sequence(
            Actions.fadeIn(0.15f),
            Actions.delay(2.4f + text.length * 0.02f),
            Actions.fadeOut(0.4f),
            Actions.removeActor(),
        ))
        add(holder).padBottom(6f).row()
    }

    fun clearAll() = clearChildren()

    /** Fades out whatever is showing (the world's messages when a panel opens). */
    fun fadeAll() {
        for (c in children) {
            c.clearActions()
            c.addAction(Actions.sequence(Actions.fadeOut(0.12f), Actions.removeActor()))
        }
    }
}

/** Floating "+3 [egg]" texts that rise from a point on screen. */
class Floaters(private val ui: Ui) : Group() {
    init {
        touchable = Touchable.disabled
    }

    fun spawn(g: Gain, stageX: Float, stageY: Float) {
        val (icon, text) = describe(g)
        val t = Table()
        if (text.isNotEmpty()) t.add(ui.label(text, ui.outline)).padRight(4f)
        if (icon != null) t.add(ui.item(icon, 40f)).size(40f)
        t.pack()
        t.setPosition(stageX - t.width / 2f, stageY)
        t.color.a = 0f
        t.addAction(Actions.sequence(
            Actions.parallel(Actions.fadeIn(0.12f), Actions.moveBy(0f, 70f, 1.3f, Interpolation.pow2Out)),
            Actions.fadeOut(0.35f),
            Actions.removeActor(),
        ))
        addActor(t)
    }

    private fun describe(g: Gain): Pair<String?, String> = when (g.key) {
        "coins" -> "coin" to "+${g.amount}"
        "energy" -> "energy" to "+${g.amount}"
        "water" -> "water" to "${g.amount}"
        "heart" -> "heart" to ""
        else -> if (ItemDb.items.containsKey(ItemKey.id(g.key))) g.key to "+${g.amount}" else null to "+${g.amount}"
    }
}
