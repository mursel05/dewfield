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

    /** The player asked for reminders (and the system allowed them when it was asked). */
    var reminders: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS, false)
        set(v) = prefs.putBoolean(KEY_REMINDERS, v).flush()

    /** The reminder offer was answered once, yes or no, so it isn't shown again. */
    var remindersAsked: Boolean
        get() = prefs.getBoolean(KEY_REMINDERS_ASKED, false)
        set(v) = prefs.putBoolean(KEY_REMINDERS_ASKED, v).flush()

    private fun defaultLang(): String = if (Locale.getDefault().language == "az") Lang.AZ.code else Lang.EN.code

    private companion object {
        const val KEY_LANG = "lang"
        const val KEY_SOUND = "sound"
        const val KEY_MUSIC = "music"
        const val KEY_REMINDERS = "reminders"
        const val KEY_REMINDERS_ASKED = "reminders_asked"
    }
}
