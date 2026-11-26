package com.idraak.quantum

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject

data class IdraakReminder(val id: Int, val title: String, val triggerAtMillis: Long, val repeatIntervalMillis: Long = 0L)

/** Local AlarmManager + SharedPreferences reminder store. */
object ReminderScheduler {
    private const val PREFS = "idraak_local_reminders"
    private const val KEY_ITEMS = "items"
    private const val CHANNEL_ID = "idraak_reminders"

    fun schedule(context: Context, title: String, triggerAtMillis: Long, repeatIntervalMillis: Long = 0L): IdraakReminder {
        val app = context.applicationContext
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt().coerceAtLeast(1)
        val reminder = IdraakReminder(id, title.take(100), triggerAtMillis, repeatIntervalMillis.coerceAtLeast(0L))
        val items = readAll(app).toMutableList()
        items.removeAll { it.id == id }
        items.add(reminder)
        saveAll(app, items)
        scheduleAlarm(app, reminder)
        return reminder
    }

    fun list(context: Context): List<IdraakReminder> =
        readAll(context.applicationContext).sortedBy { it.triggerAtMillis }

    fun cancelAll(context: Context): Int {
        val app = context.applicationContext
        val items = readAll(app)
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        items.forEach { manager.cancel(pendingIntent(app, it.id)) }
        saveAll(app, emptyList())
        return items.size
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val pending = readAll(app).mapNotNull { reminder ->
            if (reminder.triggerAtMillis > now) {
                reminder
            } else if (reminder.repeatIntervalMillis > 0L) {
                var nextTime = reminder.triggerAtMillis
                while (nextTime <= now && nextTime > 0L) {
                    nextTime += reminder.repeatIntervalMillis
                }
                if (nextTime > 0L) reminder.copy(triggerAtMillis = nextTime) else null
            } else {
                null
            }
        }
        pending.forEach { scheduleAlarm(app, it) }
        saveAll(app, pending)
    }

    fun cancelMatching(context: Context, query: String): Int {
        val app = context.applicationContext
        val normalized = query.lowercase().trim()
        if (normalized.isBlank()) return 0
        val items = readAll(app)
        val removed = items.filter { it.title.lowercase().contains(normalized) }
        val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        removed.forEach { manager.cancel(pendingIntent(app, it.id)) }
        saveAll(app, items.filterNot { it.id in removed.map(IdraakReminder::id) })
        return removed.size
    }

    internal fun deliver(context: Context, id: Int): IdraakReminder? {
        val app = context.applicationContext
        val items = readAll(app)
        val found = items.firstOrNull { it.id == id } ?: return null
        if (found.repeatIntervalMillis > 0L) {
            var nextTime = found.triggerAtMillis + found.repeatIntervalMillis
            val now = System.currentTimeMillis()
            while (nextTime <= now && nextTime > 0L) nextTime += found.repeatIntervalMillis
            val updated = found.copy(triggerAtMillis = nextTime)
            saveAll(app, items.map { if (it.id == id) updated else it })
            scheduleAlarm(app, updated)
        } else {
            saveAll(app, items.filterNot { it.id == id })
        }
        return found
    }

    private fun scheduleAlarm(context: Context, reminder: IdraakReminder) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val operation = pendingIntent(context, reminder.id)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
                }
            } else {
                manager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
            }
        } catch (_: Exception) {
            manager.set(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
        }
    }

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .setAction("com.idraak.quantum.REMINDER_$id")
            .putExtra(ReminderAlarmReceiver.EXTRA_ID, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, id, intent, flags)
    }

    private fun readAll(context: Context): List<IdraakReminder> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optInt("id", 0)
                    val title = item.optString("title", "")
                    val time = item.optLong("triggerAt", 0L)
                    val repeat = item.optLong("repeatInterval", 0L)
                    if (id > 0 && title.isNotBlank() && time > 0L) add(IdraakReminder(id, title, time, repeat))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAll(context: Context, items: List<IdraakReminder>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("title", item.title).put("triggerAt", item.triggerAtMillis).put("repeatInterval", item.repeatIntervalMillis))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    internal fun showNotification(context: Context, reminder: IdraakReminder) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Idraak Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(
            context, reminder.id, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("ادراک کی یاد دہانی")
            .setContentText(reminder.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.title))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(reminder.id, notification)
        } catch (_: SecurityException) {
            // Android notification access is controlled by the user.
        }
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        if (id <= 0) return
        val reminder = ReminderScheduler.deliver(context, id) ?: return
        ReminderScheduler.showNotification(context, reminder)
    }
    companion object { const val EXTRA_ID = "reminder_id" }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ReminderScheduler.rescheduleAll(context)
            ProactiveAssistantScheduler.rescheduleIfEnabled(context)
        }
    }
}
