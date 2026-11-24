package dev.mursal.dewfield.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dev.mursal.dewfield.R

/**
 * Posts a reminder when its alarm goes off, and sets the stored ones again after a reboot or an
 * app update (both clear alarms). Not exported: only our own alarms and the system reach it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> remind(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val now = System.currentTimeMillis()
                val future = Reminders.load(context).filter { it.atMillis > now }
                Reminders.store(context, future)
                future.forEach { Reminders.setAlarm(context, it) }
            }
        }
    }

    private fun remind(context: Context, intent: Intent) {
        val id = intent.getIntExtra(Reminders.EXTRA_ID, 0)
        val title = intent.getStringExtra(Reminders.EXTRA_TITLE)
        val text = intent.getStringExtra(Reminders.EXTRA_TEXT)
        Reminders.remove(context, id)
        if (id == 0 || title == null || text == null || !Reminders.allowed(context)) return
        Reminders.ensureChannel(context)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val open = launch?.let { PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, Reminders.CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_dewfield)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(id, notification) }
    }

    companion object {
        const val ACTION_REMIND = "dev.mursal.dewfield.REMIND"

        /** The leaf green of the game's buttons, for the small icon's tint. */
        private const val ACCENT = 0xFF5FAE4E.toInt()
    }
}
