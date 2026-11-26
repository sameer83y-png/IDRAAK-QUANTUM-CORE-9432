package com.idraak.quantum

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** Voice-facing reminder commands; all scheduling and storage stay on-device. */
object ReminderCommandHandler {
    data class Result(val response: String, val intent: String)

    fun tryHandle(context: Context, command: String): Result? {
        val normalized = command.lowercase()
        val listRequested = listOf(
            "list reminders", "show reminders", "my reminders",
            "یاد دہانیاں دکھاؤ", "یاد دہانیوں کی فہرست", "میرے ریمائنڈر",
            "reminder dikhao", "reminders dikhao"
        ).any { normalized.contains(it) }
        if (listRequested) {
            val reminders = ReminderScheduler.list(context)
            val response = if (reminders.isEmpty()) "اس وقت کوئی یاد دہانی محفوظ نہیں ہے۔"
            else reminders.joinToString("\\n") {
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(it.triggerAtMillis))
                "$time — ${it.title}"
            }
            return Result(response, "REMINDER_LIST")
        }

        val cancelRequested = listOf(
            "cancel all reminders", "delete all reminders", "clear reminders",
            "تمام یاد دہانیاں منسوخ", "ساری یاد دہانیاں ختم", "تمام ریمائنڈر ختم",
            "saare reminders cancel", "sab reminders hata"
        ).any { normalized.contains(it) }
        if (cancelRequested) {
            val count = ReminderScheduler.cancelAll(context)
            return Result("آپ کی $count یاد دہانیاں منسوخ کر دی گئی ہیں۔", "REMINDER_CANCEL")
        }

        if (!ReminderCommandParser.isReminderCommand(command)) return null
        return when (val parsed = ReminderCommandParser.parse(command)) {
            ReminderCommandParser.ParseResult.NotReminder -> null
            ReminderCommandParser.ParseResult.NeedsTime ->
                Result("کس وقت یاد دلاؤں؟ مثال: دس منٹ بعد یا شام سات بجے۔", "REMINDER_NEEDS_TIME")
            ReminderCommandParser.ParseResult.NeedsTitle ->
                Result("کس کام کی یاد دہانی بنانی ہے؟", "REMINDER_NEEDS_TITLE")
            is ReminderCommandParser.ParseResult.Ready -> {
                val reminder = ReminderScheduler.schedule(context, parsed.title, parsed.triggerAtMillis)
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(reminder.triggerAtMillis))
                Result("یاد دہانی محفوظ کر دی ہے: ${reminder.title}۔ وقت: $time۔", "REMINDER_SET")
            }
        }
    }
}
