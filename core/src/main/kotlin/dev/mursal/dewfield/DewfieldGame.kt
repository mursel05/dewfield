package dev.mursal.dewfield

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import dev.mursal.dewfield.app.Notice
import dev.mursal.dewfield.app.Notifier
import dev.mursal.dewfield.app.SaveStore
import dev.mursal.dewfield.app.Settings
import dev.mursal.dewfield.audio.Audio
import dev.mursal.dewfield.gfx.Assets
import dev.mursal.dewfield.i18n.Lang
import dev.mursal.dewfield.i18n.Strings
import dev.mursal.dewfield.model.Farm
import dev.mursal.dewfield.model.Reminders
import dev.mursal.dewfield.screen.GameScreen
import dev.mursal.dewfield.screen.TitleScreen
import dev.mursal.dewfield.ui.Ui
import dev.mursal.dewfield.world.Locations

/**
 * The application: owns shared services (assets, audio, strings, settings, the save slot)
 * and switches between the title and the game.
 *
 * [director] lets the desktop build drive the game for automated screenshots. [notifier] posts the
 * local reminders the game leaves behind when it goes to the background (Android only).
 */
class DewfieldGame(val director: Director? = null, val notifier: Notifier = Notifier.None) : Game() {
    lateinit var settings: Settings
        private set
    lateinit var assets: Assets
        private set
    lateinit var audio: Audio
        private set
    lateinit var saves: SaveStore
        private set
    lateinit var batch: SpriteBatch
        private set
    lateinit var ui: Ui
        private set
    var strings: Strings = Strings(Lang.EN, emptyMap())
        private set

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        settings = Settings()
        val scale = minOf(Gdx.graphics.width / Ui.WIDTH, Gdx.graphics.height / Ui.HEIGHT).coerceIn(1f, 3f)
        assets = Assets(scale)
        audio = Audio(settings)
        saves = SaveStore(Locations.FIELD, Locations.FIELD_V2)
        batch = SpriteBatch()
        reloadStrings()
        ui = Ui(assets, { strings }, audio)
        // The player is here now: reminders already shown or pending are out of date.
        notifier.cancelAll()
        director?.started(this)
        showTitle()
    }

    fun reloadStrings() {
        val en = load(Lang.EN)
        strings = Strings(settings.lang, if (settings.lang == Lang.EN) en else load(settings.lang), en)
    }

    private fun load(lang: Lang): Map<String, String> {
        val f = Gdx.files.internal("i18n/strings_${lang.code}.properties")
        return if (f.exists()) Strings.parse(f.reader("UTF-8")) else emptyMap()
    }

    fun showTitle() = swap(TitleScreen(this))

    fun newGame() {
        saves.delete()
        val farm = Farm.newGame(System.currentTimeMillis(), Locations.FIELD)
        swap(GameScreen(this, farm, fresh = true))
    }

    fun continueGame() {
        val state = saves.load()
        if (state == null) {
            newGame()
            return
        }
        swap(GameScreen(this, Farm(state, Locations.FIELD), fresh = false))
    }

    /** Starts playing a given farm (the desktop tour uses a fixed seed). */
    fun startGame(farm: Farm, fresh: Boolean) = swap(GameScreen(this, farm, fresh))

    private fun swap(next: com.badlogic.gdx.Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun render() {
        audio.update(Gdx.graphics.deltaTime.coerceAtMost(0.1f))
        super.render()
        director?.frame(this)
    }

    override fun pause() {
        super.pause()
        audio.pause()
        planReminders()
    }

    override fun resume() {
        super.resume()
        audio.resume()
        notifier.cancelAll()
    }

    /**
     * Leaves reminders behind for when the player is away, worded in the current language. The
     * screen saved the farm in [pause] just before; from the title, the saved farm is used.
     */
    private fun planReminders() {
        if (!notifier.available) return
        if (!settings.reminders || !notifier.allowed()) {
            notifier.cancelAll()
            return
        }
        val farm = (screen as? GameScreen)?.farm ?: saves.load()?.let { Farm(it, Locations.FIELD) } ?: return
        val notices = Reminders.plan(farm, System.currentTimeMillis()).map {
            Notice(it.kind.ordinal + 1, it.atMillis, strings.msg(it.title), strings.msg(it.text))
        }
        notifier.schedule(notices)
    }

    override fun dispose() {
        screen?.dispose()
        batch.dispose()
        audio.dispose()
        assets.dispose()
    }
}

/** Hooks for driving the game from outside (the desktop screenshot tour). */
interface Director {
    fun started(game: DewfieldGame) {}
    fun frame(game: DewfieldGame) {}
}
