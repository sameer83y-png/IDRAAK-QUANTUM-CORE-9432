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
import java.util.Calendar

/** Opt-in daily proactive check-in; never starts the microphone from background. */
object ProactiveAssistantScheduler {
    private const val PREFS = "idraak_proactive_preferences"
    private const val ENABLED = "enabled"
    private const val REQUEST_CODE = 74021
    private const val CHANNEL_ID = "idraak_proactive"

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED, enabled).apply()
        if (enabled) scheduleNext(app) else cancel(app)
    }

    fun rescheduleIfEnabled(context: Context) {
        val app = context.applicationContext
        if (isEnabled(app)) scheduleNext(app)
    }

    internal fun shouldNotifyAt(hourOfDay: Int, enabled: Boolean): Boolean =
        enabled && hourOfDay in 8..21

    private fun scheduleNext(context: Context) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context)
        manager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    private fun cancel(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ProactiveAssistantReceiver::class.java)
            .setAction("com.idraak.quantum.PROACTIVE_CHECK_IN")
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    internal fun showCheckIn(context: Context) {
        if (!isEnabled(context)) return
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!shouldNotifyAt(hour, true)) return

        val suggestion = try {
            SmartSuggestionEngine.build(
                context = context.applicationContext,
                voiceActive = false,
                lastUserMessage = ""
            ).firstOrNull()
        } catch (_: Exception) {
            null
        }
        val body = suggestion?.title ?: "کیا آج کے کاموں میں میری مدد چاہیے؟"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Idraak Proactive Assistant",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("ادراک کی طرف سے")
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REQUEST_CODE, notification)
        } catch (_: SecurityException) {
            // Notification permission remains under the user's control.
        }
    }
}

class ProactiveAssistantReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.idraak.quantum.PROACTIVE_CHECK_IN") {
            ProactiveAssistantScheduler.showCheckIn(context)
        }
    }
}
