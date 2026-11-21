package dev.mursal.dewfield.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ExtendViewport
import dev.mursal.dewfield.DewfieldGame
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.FarmEvent
import dev.mursal.dewfield.model.Gain
import dev.mursal.dewfield.model.Msg
import dev.mursal.dewfield.model.Weather
import dev.mursal.dewfield.render.CameraRig
import dev.mursal.dewfield.render.Lighting
import dev.mursal.dewfield.render.WorldRenderer
import dev.mursal.dewfield.ui.BagPanel
import dev.mursal.dewfield.ui.CookPanel
import dev.mursal.dewfield.ui.FeedShopPanel
import dev.mursal.dewfield.ui.Floaters
import dev.mursal.dewfield.ui.GameUi
import dev.mursal.dewfield.ui.Hud
import dev.mursal.dewfield.ui.LevelUpPanel
import dev.mursal.dewfield.ui.MachinePanel
import dev.mursal.dewfield.ui.Modal
import dev.mursal.dewfield.ui.ModalHost
import dev.mursal.dewfield.ui.MorningPanel
import dev.mursal.dewfield.ui.OrdersPanel
import dev.mursal.dewfield.ui.PausePanel
import dev.mursal.dewfield.ui.SeedShopPanel
import dev.mursal.dewfield.ui.SellPanel
import dev.mursal.dewfield.ui.SleepPanel
import dev.mursal.dewfield.ui.Toasts
import dev.mursal.dewfield.ui.TvPanel
import dev.mursal.dewfield.ui.Ui
import dev.mursal.dewfield.ui.WorkshopPanel
import dev.mursal.dewfield.world.Locations
import dev.mursal.dewfield.world.Panel
import dev.mursal.dewfield.world.World
import dev.mursal.dewfield.world.WorldEvent
import kotlin.math.floor

/**
 * The play screen: the world drawn with [WorldRenderer], touch input (tap to walk or act,
 * drag across plots to queue work, drag elsewhere to look around, pinch to zoom), the HUD and
 * panels, fades between areas, sleeping, and autosave.
 */
class GameScreen(private val game: DewfieldGame, override val farm: Farm, private val fresh: Boolean) : ScreenAdapter(), GameUi {
    override val ui: Ui get() = game.ui
    override val world = World(farm)
    private val rig = CameraRig()
    private val renderer: WorldRenderer
    private val lighting = Lighting()
    private val stage = Stage(ExtendViewport(1280f, 720f), game.batch)
    private val modals = ModalHost(game.ui)
    private val toasts = Toasts(game.ui)
    private val floaters = Floaters(game.ui)
    private val hud: Hud
    private val fade = Image(game.ui.white).apply { color = Color(0.06f, 0.05f, 0.08f, 1f); touchable = Touchable.disabled }
    private var banner: Label? = null
    private var busy = false
    private var autosave = 0f
    private val ambient = Color()
    private val tmp = Vector2()
    private val tmp2 = Vector2()
    private var started = false
    private var toastsOverPanel: Boolean? = null
    private var hintGap = 0f
    private var stepClock = 0f

    init {
        val spawn = if (fresh) "bed" else null
        world.enter(farm.state.location, spawn, useSaved = !fresh)
        renderer = WorldRenderer(game.assets, world)
        hud = Hud(this, { open(OrdersPanel(this)) }, { open(BagPanel(this)) }, { open(PausePanel(this, game.settings)) })
        // Floaters and toasts sit above panels so selling or cooking still shows what happened.
        stage.addActor(hud)
        stage.addActor(modals)
        stage.addActor(floaters)
        stage.addActor(toasts)
        stage.addActor(fade)
    }

    // ------------------------------------------------------------------ lifecycle

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, keys, GestureDetector(20f, 0.4f, 0.55f, 0.15f, gestures))
        resize(Gdx.graphics.width, Gdx.graphics.height)
        enterCamera()
        fade.color.a = 1f
        fade.addAction(Actions.fadeOut(0.6f))
        if (!started) {
            started = true
            showBanner()
            hint("hint.welcome", now = true)
        }
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        stage.viewport.update(width, height, true)
        rig.resize()
        lighting.resize(width, height)
        hud.layoutTo()
        modals.layoutModal()
        fade.setSize(stage.width, stage.height)
        toasts.setSize(stage.width, stage.height)
        toasts.setPosition(0f, 0f)
        toastsOverPanel = null
        floaters.setSize(stage.width, stage.height)
    }

    override fun pause() = save()

    override fun hide() = save()

    override fun dispose() {
        stage.dispose()
        lighting.dispose()
    }

    private fun save() {
        if (busy) return
        world.storePosition()
        game.saves.save(farm.state)
    }

    // ------------------------------------------------------------------ frame

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(1f / 20f)
        val clockRunning = !modals.isOpen && !busy
        world.update(dt, clockRunning)
        drainWorldEvents()
        drainFarmEvents()
        rig.follow(world.player.x, world.player.y, dt, world.player.moving)
        footsteps(dt)
        renderer.update(dt, rig.left, rig.bottom, rig.right, rig.top)
        hud.update(dt)
        val panel = modals.isOpen
        hud.setHidden(panel)
        if (toastsOverPanel != panel) {
            // Above a panel the toasts use the strip the hidden HUD leaves free, one line wide,
            // and messages about the world make way for the panel's own.
            if (panel && toastsOverPanel == false) toasts.fadeAll()
            toastsOverPanel = panel
            toasts.padTop(if (panel) 8f else 96f)
            toasts.maxTextWidth = if (panel) stage.width - 120f else 720f
            toasts.invalidate()
        }
        music()
        hints()
        autosave += dt
        if (autosave > AUTOSAVE_SECONDS && !busy) {
            autosave = 0f
            save()
        }

        val bg = if (world.location.indoor) INDOOR_BG else OUTDOOR_BG
        Gdx.gl.glClearColor(bg.r, bg.g, bg.b, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val batch = game.batch
        batch.projectionMatrix = rig.cam.combined
        batch.begin()
        renderer.draw(batch, rig)
        batch.end()
        if (renderer.needsLighting()) lighting.render(batch, rig.cam, renderer.ambient(ambient)) { renderer.drawLights(it) }

        stage.act(dt)
        stage.draw()
    }

    private fun drainWorldEvents() {
        while (true) {
            when (val e = world.events.removeFirstOrNull() ?: break) {
                is WorldEvent.Toast -> toast(e.msg)
                is WorldEvent.Sound -> ui.audio.play(e.id)
                is WorldEvent.Fx -> renderer.effects.burst(e.kind, e.x, e.y)
                is WorldEvent.Float -> {
                    rig.toScreen(e.x, e.y, tmp)
                    stage.screenToStageCoordinates(tmp)
                    floaters.spawn(e.gain, tmp.x, tmp.y)
                    hud.refresh()
                }
                is WorldEvent.Open -> openPanel(e.panel, e.arg)
                is WorldEvent.Travel -> travel(e.to, e.spawn)
            }
        }
    }

    private fun drainFarmEvents() {
        while (true) {
            when (val e = farm.events.removeFirstOrNull() ?: break) {
                is FarmEvent.LevelUp -> {
                    ui.audio.play("levelup")
                    renderer.effects.burst("sparkle", world.player.x, world.player.y + 0.6f)
                    modals.show(LevelUpPanel(this, e.level, e.unlocks), queue = true)
                }
                FarmEvent.Exhausted -> passOut()
            }
        }
    }

    fun openPanel(panel: Panel, arg: String = "") {
        val m: Modal = when (panel) {
            Panel.SHOP_SEEDS -> SeedShopPanel(this)
            Panel.SHOP_FEED -> FeedShopPanel(this)
            Panel.SELL -> SellPanel(this)
            Panel.WORKSHOP -> WorkshopPanel(this)
            Panel.ORDERS -> OrdersPanel(this)
            Panel.MACHINE -> MachinePanel(this, arg)
            Panel.COOK -> CookPanel(this)
            Panel.TV -> TvPanel(this)
            Panel.SLEEP -> SleepPanel(this)
        }
        open(m)
    }

    // ------------------------------------------------------------------ travel, sleep

    /** Jumps straight to an area without the fade (used by the desktop screenshot tour). */
    fun warp(to: String, spawn: String) {
        world.enter(to, spawn)
        enterCamera()
        hud.refresh()
    }

    /** Closes every panel, including queued ones. */
    fun closeAll() = modals.clearAll()

    val panelOpen: Boolean get() = modals.isOpen

    private fun enterCamera() {
        val loc = world.location
        rig.setMap(loc.width, loc.height, if (loc.indoor) 9.6f else CameraRig.DEFAULT_VIEW)
        rig.snap(world.player.x, world.player.y + 0.4f)
        renderer.effects.clearAmbient()
    }

    private fun travel(to: String, spawn: String) {
        if (busy) return
        busy = true
        fade.clearActions()
        fade.addAction(Actions.sequence(
            Actions.fadeIn(0.25f),
            Actions.run {
                world.enter(to, spawn)
                enterCamera()
                hud.refresh()
                busy = false
                save()
                showBanner()
                when (to) {
                    Locations.FARM -> hint("hint.farm", now = true)
                    Locations.MARKET -> hint("hint.market", now = true)
                }
            },
            Actions.fadeOut(0.35f),
        ))
    }

    override fun sleep() = endDay(passedOut = false)

    private fun passOut() {
        if (busy) return
        modals.clearAll()
        world.clearTasks()
        endDay(passedOut = true)
    }

    private fun endDay(passedOut: Boolean) {
        if (busy) return
        busy = true
        world.clearTasks()
        ui.audio.play("sleep")
        fade.clearActions()
        fade.addAction(Actions.sequence(
            Actions.fadeIn(if (passedOut) 1.2f else 0.8f),
            Actions.delay(0.5f),
            Actions.run {
                val report = farm.endDay(passedOut)
                world.enter(Locations.HOUSE, "bed")
                world.refreshAnimals()
                enterCamera()
                hud.build()
                busy = false
                save()
                ui.audio.play("rooster")
                modals.show(MorningPanel(this, report), queue = true)
            },
            Actions.fadeOut(0.9f),
        ))
    }

    // ------------------------------------------------------------------ GameUi

    override fun toast(msg: Msg) = toasts.show(ui.m(msg))

    override fun gain(g: Gain) {
        floaters.spawn(g, stage.width / 2f, stage.height * 0.62f)
    }

    override fun open(modal: Modal) {
        if (busy) return
        world.clearTasks()
        ui.audio.play("open")
        modals.show(modal)
    }

    override fun close(modal: Modal) {
        ui.audio.play("close")
        modals.close(modal)
        hud.refresh()
    }

    override fun refreshHud() = hud.refresh()

    override fun relabel() {
        game.reloadStrings()
        modals.clearAll()
        hud.build()
        stage.root.findActor<Label>("banner")?.remove()
        open(PausePanel(this, game.settings))
    }

    override fun exitToTitle() {
        save()
        busy = true
        fade.clearActions()
        fade.addAction(Actions.sequence(Actions.fadeIn(0.35f), Actions.run { game.showTitle() }))
    }

    // ------------------------------------------------------------------ presentation

    private fun showBanner() {
        banner?.remove()
        val l = Label(ui.t("loc.${world.location.id}"), ui.outlineBig)
        l.name = "banner"
        l.setAlignment(Align.center)
        l.pack()
        l.setPosition((stage.width - l.width) / 2f, stage.height * 0.56f)
        l.color.a = 0f
        l.touchable = Touchable.disabled
        l.addAction(Actions.sequence(
            Actions.parallel(Actions.fadeIn(0.4f), Actions.moveBy(0f, 12f, 0.4f, Interpolation.pow2Out)),
            Actions.delay(1.2f),
            Actions.fadeOut(0.6f),
            Actions.removeActor(),
        ))
        stage.addActor(l)
        fade.toFront()
        banner = l
    }

    private fun music() {
        val night = farm.state.minutes >= 20 * 60
        ui.audio.music(if (night) "night" else "day")
        ui.audio.ambient(if (farm.state.weather == Weather.RAIN && !world.location.indoor) "rain" else null)
    }

    /** Soft footsteps in time with the walk cycle (two steps per cycle at 9 frames a second). */
    private fun footsteps(dt: Float) {
        if (!world.player.moving || busy) {
            stepClock = STEP_SECONDS * 0.6f
            return
        }
        stepClock += dt
        if (stepClock >= STEP_SECONDS) {
            stepClock -= STEP_SECONDS
            ui.audio.play("step", if (world.location.indoor) 0.35f else 0.45f, 0.12f)
        }
    }

    /**
     * One-time tips for new players, keyed in the save so they show once per farm. Tips that wait
     * for a situation are spaced out so the player never gets a wall of text; [now] skips the wait
     * for the tip that explains the place the player just arrived at.
     */
    private fun hint(key: String, now: Boolean = false) {
        if (key in farm.state.seenHints) return
        if (!now && (hintGap > 0f || modals.isOpen || busy)) return
        farm.state.seenHints.add(key)
        toasts.show(ui.t(key))
        hintGap = HINT_GAP_SECONDS
    }

    private fun hints() {
        if (!modals.isOpen) hintGap -= Gdx.graphics.deltaTime
        val s = farm.state
        if (world.location.id == Locations.FARM) {
            if (s.plots.any { it.crop != null && !it.watered } && "hint.water" !in s.seenHints) hint("hint.water")
            if (s.day >= 2) hint("hint.animals")
        }
        if (s.minutes >= 20 * 60) hint("hint.night")
        if (farm.level >= 2) hint("hint.orders")
    }

    // ------------------------------------------------------------------ input

    private val keys = object : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
            val m = modals.current
            when {
                busy -> Unit
                m != null -> if (m.canClose) close(m)
                else -> open(PausePanel(this@GameScreen, game.settings))
            }
            return true
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            rig.zoomTo(rig.view * (1f + amountY * 0.1f))
            return true
        }
    }

    private val gestures = object : GestureDetector.GestureAdapter() {
        private var painting = false
        private var lastX = Float.NaN
        private var lastY = Float.NaN
        private var startX = 0f
        private var startY = 0f
        private var zoomBase = -1f

        override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (pointer != 0 || busy || modals.isOpen) return false
            rig.toWorld(x, y, tmp2)
            startX = tmp2.x
            startY = tmp2.y
            painting = world.location.id == Locations.FARM && world.canPaint(startX, startY)
            lastX = Float.NaN
            return false
        }

        override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
            if (busy || modals.isOpen) return false
            rig.toWorld(x, y, tmp2)
            renderer.effects.tapRipple(tmp2.x, tmp2.y)
            world.tap(tmp2.x, tmp2.y)
            rig.manual = false
            return true
        }

        override fun longPress(x: Float, y: Float): Boolean {
            if (busy || modals.isOpen) return false
            rig.toWorld(x, y, tmp2)
            world.inspect(tmp2.x, tmp2.y)
            return true
        }

        override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
            if (busy || modals.isOpen || zoomBase > 0f) return false
            if (!painting) {
                rig.pan(deltaX, deltaY)
                return true
            }
            rig.toWorld(x, y, tmp2)
            if (lastX.isNaN()) {
                world.paint(startX, startY)
                lastX = startX
                lastY = startY
            }
            // Walk the segment so fast swipes do not skip plots.
            val dx = tmp2.x - lastX
            val dy = tmp2.y - lastY
            val steps = (kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy)) / 0.25f).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val px = lastX + dx * i / steps
                val py = lastY + dy * i / steps
                if (floor(px) != floor(lastX + dx * (i - 1) / steps) || floor(py) != floor(lastY + dy * (i - 1) / steps) || i == steps) world.paint(px, py)
            }
            lastX = tmp2.x
            lastY = tmp2.y
            return true
        }

        override fun zoom(initialDistance: Float, distance: Float): Boolean {
            if (modals.isOpen) return false
            if (zoomBase < 0f) zoomBase = rig.view
            painting = false
            rig.zoomTo(zoomBase * initialDistance / distance.coerceAtLeast(1f))
            return true
        }

        override fun pinchStop() {
            zoomBase = -1f
        }
    }

    companion object {
        private const val AUTOSAVE_SECONDS = 45f
        private const val HINT_GAP_SECONDS = 7f
        private const val STEP_SECONDS = 2f / 9f
        private val OUTDOOR_BG = Color.valueOf("4f8a3a")
        private val INDOOR_BG = Color.valueOf("2b2118")
    }
}
