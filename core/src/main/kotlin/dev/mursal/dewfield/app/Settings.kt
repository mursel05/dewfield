package dev.mursal.dewfield.app

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import dev.mursal.dewfield.i18n.Lang
import java.util.Locale

/** Player settings stored in platform preferences (SharedPreferences on Android). */
class Settings {
    private val prefs: Preferences = Gdx.app.getPreferences("dewfield.settings")

    var lang: Lang
        get() = Lang.of(prefs.getString(KEY_LANG, null) ?: defaultLang())
        set(v) = prefs.putString(KEY_LANG, v.code).flush()

    var sound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(v) = prefs.putBoolean(KEY_SOUND, v).flush()

    var music: Boolean
        get() = prefs.getBoolean(KEY_MUSIC, true)
        set(v) = prefs.putBoolean(KEY_MUSIC, v).flush()

    private fun defaultLang(): String = if (Locale.getDefault().language == "az") Lang.AZ.code else Lang.EN.code

    private companion object {
        const val KEY_LANG = "lang"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
    }
}
