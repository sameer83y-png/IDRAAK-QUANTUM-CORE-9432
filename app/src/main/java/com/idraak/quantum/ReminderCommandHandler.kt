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

        val cancelOne = listOf(
            "cancel reminder", "remove reminder", "delete reminder",
            "اس یاد دہانی کو منسوخ", "یاد دہانی منسوخ", "reminder cancel"
        ).any { normalized.contains(it) }
        if (cancelOne) {
            val query = normalized
                .replace("cancel reminder", "")
                .replace("remove reminder", "")
                .replace("delete reminder", "")
                .replace("reminder cancel", "")
                .replace("اس یاد دہانی کو منسوخ", "")
                .replace("یاد دہانی منسوخ", "")
                .replace("کرو", "")
                .trim()
            if (query.isBlank()) return Result("کس یاد دہانی کو منسوخ کرنا ہے؟", "REMINDER_NEEDS_TITLE")
            val count = ReminderScheduler.cancelMatching(context, query)
            return Result(if (count > 0) "منتخب یاد دہانی منسوخ کر دی گئی ہے۔" else "اس نام کی یاد دہانی نہیں ملی۔", "REMINDER_CANCEL_ONE")
        }

        if (!ReminderCommandParser.isReminderCommand(command)) return null
        return when (val parsed = ReminderCommandParser.parse(command)) {
            ReminderCommandParser.ParseResult.NotReminder -> null
            ReminderCommandParser.ParseResult.NeedsTime ->
                Result("کس وقت یاد دلاؤں؟ مثال: دس منٹ بعد یا شام سات بجے۔", "REMINDER_NEEDS_TIME")
            ReminderCommandParser.ParseResult.NeedsTitle ->
                Result("کس کام کی یاد دہانی بنانی ہے؟", "REMINDER_NEEDS_TITLE")
            is ReminderCommandParser.ParseResult.Ready -> {
                val reminder = ReminderScheduler.schedule(context, parsed.title, parsed.triggerAtMillis, parsed.repeatIntervalMillis)
                val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(reminder.triggerAtMillis))
                Result(if (reminder.repeatIntervalMillis > 0L) "بار بار آنے والی یاد دہانی محفوظ کر دی ہے: ${reminder.title}۔ اگلا وقت: $time۔" else "یاد دہانی محفوظ کر دی ہے: ${reminder.title}۔ وقت: $time۔", "REMINDER_SET")
            }
        }
    }
}
