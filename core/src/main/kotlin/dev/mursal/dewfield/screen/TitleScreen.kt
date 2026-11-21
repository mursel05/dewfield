package dev.mursal.dewfield.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.ExtendViewport
import dev.mursal.dewfield.DewfieldGame
import dev.mursal.dewfield.i18n.Lang
import dev.mursal.dewfield.model.AnimalState
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.ItemDb
import dev.mursal.dewfield.render.CameraRig
import dev.mursal.dewfield.render.Lighting
import dev.mursal.dewfield.render.WorldRenderer
import dev.mursal.dewfield.world.Locations
import dev.mursal.dewfield.world.World
import kotlin.math.sin

/**
 * The title: a living demo farm drifts by behind the logo. Continue, New farm,
 * language and sound. A new farm over an existing save asks first.
 */
class TitleScreen(private val game: DewfieldGame) : ScreenAdapter() {
    private val ui get() = game.ui
    private val stage = Stage(ExtendViewport(1280f, 720f), game.batch)
    private val demo = demoFarm()
    private val world = World(demo).also { it.enter(Locations.FARM, "house_door") }
    private val renderer = WorldRenderer(game.assets, world)
    private val rig = CameraRig()
    private val lighting = Lighting()
    private val ambient = Color()
    private var t = 0f
    private val root = Table()
    private var confirm: Table? = null

    init {
        stage.addActor(root)
        root.setFillParent(true)
        build()
    }

    private fun build() {
        root.clear()
        confirm = null
        val logo = Image(game.assets.region("ui/logo")).apply { setScaling(Scaling.fit) }
        root.add(logo).width(720f).height(208f).padBottom(18f).row()
        val buttons = Table()
        if (game.saves.exists()) {
            buttons.add(ui.button(ui.t("title.continue"), "green", "play", 76f) { game.continueGame() }).width(420f).height(76f).padBottom(14f).row()
        }
        buttons.add(ui.button(ui.t("title.new"), if (game.saves.exists()) "cream" else "green", "seed", 76f) {
            if (game.saves.exists()) askNewFarm() else game.newGame()
        }).width(420f).height(76f).padBottom(14f).row()
        val small = Table()
        small.add(ui.button(if (game.settings.lang == Lang.EN) "English" else "Azərbaycan", "cream", "globe", 62f) {
            game.settings.lang = if (game.settings.lang == Lang.EN) Lang.AZ else Lang.EN
            game.reloadStrings()
            build()
        }).width(250f).height(62f).padRight(10f)
        small.add(ui.button("", "cream", if (game.settings.sound) "sound_on" else "sound_off", 62f) {
            game.settings.sound = !game.settings.sound
            game.settings.music = game.settings.sound
            build()
        }).width(160f).height(62f)
        buttons.add(small)
        root.add(buttons).row()
        val version = ui.label(ui.f("title.version", VERSION), ui.light)
        root.add(version).padTop(26f)
        root.color.a = 0f
        root.addAction(Actions.fadeIn(0.5f))
    }

    private fun askNewFarm() {
        confirm?.remove()
        val box = Table().apply {
            background = ui.panel
            pad(28f)
            add(ui.label(ui.t("title.confirm_new"), ui.head, wrap = true, align = Align.center)).width(560f).colspan(2).padBottom(20f).row()
            add(ui.button(ui.t("ui.cancel"), "cream") { remove(); confirm = null }).width(260f).height(68f).padRight(12f)
            add(ui.button(ui.t("title.confirm_yes"), "red") { game.newGame() }).width(260f).height(68f)
            pack()
            setPosition((stage.width - width) / 2f, (stage.height - height) / 2f)
            touchable = Touchable.enabled
            isTransform = true
            setOrigin(Align.center)
            setScale(0.9f)
            addAction(Actions.scaleTo(1f, 1f, 0.18f, Interpolation.swingOut))
        }
        confirm = box
        stage.addActor(box)
    }

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(stage, object : InputAdapter() {
            override fun keyDown(keycode: Int): Boolean {
                if (keycode != Input.Keys.BACK && keycode != Input.Keys.ESCAPE) return false
                if (confirm != null) {
                    confirm?.remove()
                    confirm = null
                } else Gdx.app.exit()
                return true
            }
        })
        resize(Gdx.graphics.width, Gdx.graphics.height)
        rig.setMap(world.location.width, world.location.height, 11f)
        rig.snap(12f, 15f)
        game.audio.music("day")
    }

    override fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        stage.viewport.update(width, height, true)
        rig.resize()
        lighting.resize(width, height)
        confirm?.let { it.setPosition((stage.width - it.width) / 2f, (stage.height - it.height) / 2f) }
    }

    override fun render(delta: Float) {
        val dt = delta.coerceAtMost(0.05f)
        t += dt
        world.update(dt, clockRunning = false)
        world.events.clear()
        // A slow drift over the farm.
        rig.manual = true
        rig.cam.position.set(20f + sin(t * 0.05f) * 9f, 14.5f + sin(t * 0.08f) * 1.5f, 0f)
        rig.resize()
        renderer.update(dt, rig.left, rig.bottom, rig.right, rig.top)

        Gdx.gl.glClearColor(0.31f, 0.54f, 0.23f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        game.batch.projectionMatrix = rig.cam.combined
        game.batch.begin()
        renderer.draw(game.batch, rig)
        game.batch.end()
        if (renderer.needsLighting()) lighting.render(game.batch, rig.cam, renderer.ambient(ambient)) { renderer.drawLights(it) }
        stage.act(dt)
        stage.draw()
    }

    override fun dispose() {
        stage.dispose()
        lighting.dispose()
    }

    companion object {
        const val VERSION = "0.2.0"

        /** A pretty, never-saved farm for the background. */
        fun demoFarm(): Farm {
            val f = Farm.newGame(2026, Locations.FIELD)
            val s = f.state
            s.upgrades[ItemDb.UPGRADE_FIELD] = 4
            s.minutes = 9 * 60.0
            val crops = listOf("wheat", "carrot", "tomato", "lettuce", "corn", "strawberry", "pumpkin", "radish")
            s.plots.forEachIndexed { i, p ->
                if ((p.x + p.y) % 7 == 0) return@forEachIndexed
                val crop = crops[(p.x / 2 + p.y / 2 + i / 24) % crops.size]
                val def = ItemDb.crop(crop)
                p.tilled = true
                p.watered = i % 3 != 0
                p.crop = crop
                p.growth = (def.growDays * ((i * 37) % 100) / 100f).toInt().coerceAtMost(def.growDays)
            }
            listOf("cow" to "Marta", "cow" to "Bella", "sheep" to "Bulud", "sheep" to "Pambiq", "chicken" to "Lala", "chicken" to "Zuzu").forEach { (k, n) ->
                s.animals += AnimalState(s.nextAnimalId++, k, n)
            }
            s.troughs.keys.forEach { s.troughs[it] = 20 }
            return f
        }
    }
}
