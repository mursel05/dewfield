package dev.mursal.dewfield.android

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.badlogic.gdx.Gdx
import dev.mursal.dewfield.R
import dev.mursal.dewfield.app.Notice
import dev.mursal.dewfield.app.Notifier
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local reminders with AlarmManager. Nothing leaves the phone: the game plans a few notices when it
 * goes to the background ([schedule]), they are stored so a reboot can set them again, and
 * [ReminderReceiver] posts each one when its time comes. Opening the game cancels them all.
 *
 * Alarms are inexact (`setAndAllowWhileIdle`), so no exact-alarm permission is needed; a reminder
 * a few minutes late is fine. On Android 13 and newer the game asks for POST_NOTIFICATIONS the
 * first time the player turns reminders on.
 */
class AndroidNotifier(private val activity: Activity) : Notifier {
    private val context: Context get() = activity.applicationContext
    private var pending: ((Boolean) -> Unit)? = null

    override val available = true

    override fun allowed(): Boolean = Reminders.allowed(context)

    override fun requestPermission(done: (Boolean) -> Unit) {
        if (allowed()) {
            Gdx.app.postRunnable { done(true) }
            return
        }
        activity.runOnUiThread {
            val prefs = Reminders.prefs(context)
            val needsRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            // Asked before and the system no longer shows its dialog: the player said no for good,
            // so the only way left is the app's notification settings.
            val deniedForGood = needsRuntime && prefs.getBoolean(KEY_ASKED, false) &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            if (needsRuntime && !deniedForGood) {
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
                pending = done
                activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
            } else {
                openSettings()
                Gdx.app.postRunnable { done(false) }
            }
        }
    }

    /** Called by the launcher with the answer to the permission dialog. */
    fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode != REQUEST_CODE) return
        val done = pending ?: return
        pending = null
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && allowed()
        Gdx.app.postRunnable { done(granted) }
    }

    override fun schedule(notices: List<Notice>) {
        cancelAlarms()
        Reminders.store(context, notices)
        for (n in notices) Reminders.setAlarm(context, n)
    }

    override fun cancelAll() {
        cancelAlarms()
        Reminders.store(context, emptyList())
        context.getSystemService(NotificationManager::class.java)?.cancelAll()
    }

    private fun cancelAlarms() {
        val ids = Reminders.load(context).map { it.id }.toSet() + (1..Reminders.MAX_ID)
        for (id in ids) Reminders.cancelAlarm(context, id)
    }

    private fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
        runCatching { activity.startActivity(intent) }
    }

    private companion object {
        const val REQUEST_CODE = 4107
        const val KEY_ASKED = "permission_asked"
    }
}

/** Storage, alarms and the notification channel, shared by [AndroidNotifier] and [ReminderReceiver]. */
internal object Reminders {
    const val CHANNEL = "reminders"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    /** Notice ids are small (one per kind of reminder); alarms up to this id are always cleared. */
    const val MAX_ID = 8

    private const val PREFS = "dewfield.reminders"
    private const val KEY_NOTICES = "notices"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(CHANNEL)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    fun store(context: Context, notices: List<Notice>) {
        val arr = JSONArray()
        for (n in notices) {
            arr.put(JSONObject().put("id", n.id).put("at", n.atMillis).put("title", n.title).put("text", n.text))
        }
        prefs(context).edit().putString(KEY_NOTICES, arr.toString()).apply()
    }

    fun load(context: Context): List<Notice> {
        val raw = prefs(context).getString(KEY_NOTICES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Notice(o.getInt("id"), o.getLong("at"), o.getString("title"), o.getString("text"))
            }
        }.getOrDefault(emptyList())
    }

    /** Forgets a notice once it was shown, so a reboot doesn't bring it back. */
    fun remove(context: Context, id: Int) = store(context, load(context).filter { it.id != id })

    fun setAlarm(context: Context, n: Notice) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.atMillis, alarmIntent(context, n.id, n))
    }

    fun cancelAlarm(context: Context, id: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(context, id, null))
    }

    private fun alarmIntent(context: Context, id: Int, n: Notice?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND)
        if (n != null) intent.putExtra(EXTRA_ID, n.id).putExtra(EXTRA_TITLE, n.title).putExtra(EXTRA_TEXT, n.text)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.reminder_channel_about)
        nm.createNotificationChannel(channel)
    }
}
