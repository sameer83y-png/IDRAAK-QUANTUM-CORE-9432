package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/** Hands alarm creation and dismissal to the device Clock app using Android's standard intents. */
object AlarmCommandHandler {
    data class Result(
        val response: String,
        val intent: String,
        val requiresForegroundUi: Boolean = false
    )

    fun tryHandle(context: Context, command: String, allowForegroundUi: Boolean): Result? {
        val text = command.lowercase()
        val show = listOf("show alarms", "list alarms", "الارم دکھاؤ", "الارم کی فہرست").any { text.contains(it) }
        val cancel = listOf(
            "cancel alarm", "delete alarm", "stop alarm", "turn off alarm",
            "الارم بند کرو", "الارم ختم کرو", "الارم منسوخ", "الارم بند"
        ).any { text.contains(it) }
        if (show || cancel) {
            if (!allowForegroundUi) return Result(
                "گھڑی کی ایپ کھولنے کی تیاری ہے۔",
                if (show) "ALARM_LIST" else "ALARM_DISMISS",
                requiresForegroundUi = true
            )
            return launchClock(
                context,
                if (show) Intent(AlarmClock.ACTION_SHOW_ALARMS)
                else Intent(AlarmClock.ACTION_DISMISS_ALARM).putExtra(AlarmClock.EXTRA_SKIP_UI, false),
                if (show) "ALARM_LIST" else "ALARM_DISMISS",
                if (show) "گھڑی میں موجود الارم دکھائے جا رہے ہیں۔" else "گھڑی کی ایپ میں الارم بند کرنے کا اختیار کھولا گیا ہے۔"
            )
        }

        return when (val parsed = AlarmCommandParser.parse(command)) {
            AlarmCommandParser.Result.NotAlarm -> null
            AlarmCommandParser.Result.NeedsTime -> Result("کس وقت الارم لگاؤں؟", "ALARM_NEEDS_TIME")
            AlarmCommandParser.Result.NeedsMeridiem -> Result("یہ وقت صبح کا ہے یا شام کا؟", "ALARM_NEEDS_MERIDIEM")
            is AlarmCommandParser.Result.Ready -> {
                if (!allowForegroundUi) {
                    Result("الارم لگانے کے لیے گھڑی کی ایپ کھولنے کی تیاری ہے۔", "ALARM_SET", true)
                } else {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, parsed.hour)
                        .putExtra(AlarmClock.EXTRA_MINUTES, parsed.minute)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    if (parsed.label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, parsed.label)
                    launchClock(context, intent, "ALARM_SET", "گھڑی کی ایپ میں الارم کی تصدیق مکمل کریں۔")
                }
            }
        }
    }

    private fun launchClock(context: Context, intent: Intent, action: String, message: String): Result {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(intent)
            Result(message, action)
        } catch (_: Exception) {
            Result("اس فون میں الارم کی گھڑی ایپ دستیاب نہیں ملی۔", "ALARM_UNAVAILABLE")
        }
    }
}
