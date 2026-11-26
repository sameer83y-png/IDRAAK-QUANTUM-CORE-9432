package com.idraak.quantum

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/** Pure offline parser for one-shot reminders. */
object ReminderCommandParser {
    sealed class ParseResult {
        data object NotReminder : ParseResult()
        data object NeedsTime : ParseResult()
        data object NeedsTitle : ParseResult()
        data class Ready(val title: String, val triggerAtMillis: Long, val repeatIntervalMillis: Long = 0L) : ParseResult()
    }

    private val reminderMarkers = listOf(
        "remind me", "set reminder", "set a reminder", "reminder at", "reminder for", "ذكرني", "ذكّرني", "تذكير",
        "yaad dila", "yaad dilana", "yaad dila dena", "mujhe yaad",
        "یاد دلاؤ", "یاد دلانا", "یاد دہانی", "یاد دلا دینا", "یاد کرانا",
        "याद दिला", "याद दिलाना", "याद दिला देना", "रिमाइंडर"
    )

    fun isReminderCommand(command: String): Boolean {
        val normalized = normalizeDigits(command).lowercase(Locale.ROOT)
        return reminderMarkers.any { normalized.contains(it) }
    }

    fun parse(command: String, nowMillis: Long = System.currentTimeMillis()): ParseResult {
        val normalized = normalizeDigits(command).lowercase(Locale.ROOT)
            .replace('،', ' ')
            .replace(Regex("[!?؟،,؛;]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (!isReminderCommand(normalized)) return ParseResult.NotReminder

        val now = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowMillis),
            ZoneId.systemDefault()
        )
        val relative = relativeDuration(normalized)
        val clock = if (relative == null) clockTime(normalized, now) else null
        val trigger = when {
            relative != null -> now.plusSeconds(relative)
            clock != null -> clock
            else -> null
        } ?: return ParseResult.NeedsTime

        val title = extractTitle(normalized)
        if (title.isBlank()) return ParseResult.NeedsTitle
        val repeatInterval = when {
            listOf("every day", "daily", "rozana", "har roz", "روزانہ", "ہر روز", "روزانہ", "كل يوم", "يوميا", "يوميًا").any { normalized.contains(it) } -> 24L * 60L * 60L * 1000L
            listOf("every week", "weekly", "har haftay", "haftawar", "ہر ہفتے", "ہفتہ وار", "हर हफ्ते", "كل أسبوع", "أسبوعيا", "أسبوعيًا").any { normalized.contains(it) } -> 7L * 24L * 60L * 60L * 1000L
            else -> 0L
        }
        return ParseResult.Ready(
            title,
            trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            repeatInterval
        )
    }

    private fun relativeDuration(text: String): Long? {
        val patterns = listOf(
            Regex("""(?:in|after)\s*(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)"""),
            Regex("""(\d+)\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\s*(?:later|from now)"""),
            Regex("""(\d+)\s*(?:second|seconds|minute|minutes|min|mins|hour|hours|ghanta|ghante)\s*(?:baad|bad|mein|me)"""),
            Regex("""(\d+)\s*(منٹ|منٹوں|گھنٹہ|گھنٹے|سیکنڈ)\s*(?:بعد|میں)"""),
            Regex("""(\d+)\s*(मिनट|मिनिट|घंटा|घंटे|सेकंड)\s*(?:बाद|में)""")
            Regex("""(\d+)\s*(ثانية|ثواني|دقيقة|دقائق|ساعة|ساعات)\s*(?:بعد|خلال)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val amount = match.groupValues[1].toLongOrNull() ?: continue
            if (amount <= 0L) return null
            val unit = match.groupValues.getOrNull(2).orEmpty()
            val multiplier = when {
                unit.startsWith("sec") || unit == "سیکنڈ" || unit == "सेकंड" || unit.startsWith("ثانية") || unit.startsWith("ثوان") -> 1L
                unit.startsWith("hour") || unit.startsWith("hr") ||
                    unit.startsWith("ghanta") || unit.startsWith("ghante") ||
                    unit.startsWith("گھنٹ") || unit.startsWith("घंट") || unit == "گھنٹہ" || unit == "گھنٹے" || unit.startsWith("ساعة") || unit.startsWith("ساعات") -> 3600L
                else -> 60L
            }
            return (amount * multiplier).coerceAtMost(30L * 24L * 3600L)
        }
        return null
    }

    private fun clockTime(text: String, now: LocalDateTime): LocalDateTime? {
        val hasClockMarker = listOf(
            "at ", "around ", "tomorrow", "kal", "کل", "صبح", "شام",
            "بجے", "सुबह", "शाम", "बजे", "am", "pm", "الساعة", "غدا", "غدًا", "صباح", "مساء", "ليلا", "ليلاً"
        ).any { text.contains(it) }
        if (!hasClockMarker) return null
        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
            .find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val meridiem = match.groupValues[3]
        if (meridiem == "pm" && hour in 1..11) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (meridiem.isEmpty()) {
            if ((text.contains("شام") || text.contains("shaam") || text.contains("مساء")) && hour in 1..11) hour += 12
            if (text.contains("صباح") && hour == 12) hour = 0
            if ((text.contains("رات") || text.contains("night") || text.contains("مساء") || text.contains("ليلا") || text.contains("ليلاً")) && hour in 1..11) hour += 12
        }
        var target = now.with(LocalTime.of(hour, minute, 0))
        val tomorrow = listOf("tomorrow", "kal", "کل", "कल", "غدا", "غدًا").any { text.contains(it) }
        if (tomorrow || !target.isAfter(now)) target = target.plusDays(1)
        return target
    }

    private fun extractTitle(text: String): String {
        var value = text
        val removals = listOf(
            Regex("""(?:remind me|set a reminder|set reminder|reminder)"""),
            Regex("""(?:in|after)\s*\d+\s*(?:seconds?|secs?|minutes?|mins?|hours?|hrs?)"""),
            Regex("""\d+\s*(?:seconds?|secs?|minutes?|mins?|hours?|hrs?|minute|minutes|min|mins|hour|hours|ghanta|ghante)\s*(?:later|from now|baad|bad|mein|me)"""),
            Regex("""\d+\s*(?:منٹ|منٹوں|گھنٹہ|گھنٹے|سیکنڈ)\s*(?:بعد|میں)"""),
            Regex("""\d+\s*(?:मिनट|मिनिट|घंटा|घंटे|सेकंड)\s*(?:बाद|में)"""),
            Regex("""\d+\s*(?:ثانية|ثواني|دقيقة|دقائق|ساعة|ساعات)\s*(?:بعد|خلال)"""),
            Regex("""(?:at|around)\s*\d{1,2}(?::\d{2})?\s*(?:am|pm)?"""),
            Regex("""(?:tomorrow|kal|کل|صبح|شام|رات|بجے|सुबह|शाम|बजे|الساعة|غدا|غدًا|صباح|مساء|ليلا|ليلاً)"""),
            Regex("""(?:every day|daily|rozana|har roz|روزانہ|ہر روز|every week|weekly|har haftay|haftawar|ہر ہفتے|ہفتہ وار|हर हफ्ते|كل يوم|يوميا|يوميًا|كل أسبوع|أسبوعيا|أسبوعيًا)"""),
            Regex("""(?:please|kindly|mujhe|mujhay|yaad dila dena|yaad dilana|yaad dila|yaad dila do)"""),
            Regex("""(?:مجھے|براہ کرم|ذرا|یاد دلاؤ|یاد دلانا|یاد دلا دیں|یاد دلا دینا|یاد دہانی|ذكرني|ذكّرني|تذكير)"""),
            Regex("""(?:मुझे|कृपया|जरा|याद दिलाना|याद दिला देना|याद दिला दो|रिमाइंडर)"""),
            Regex("""^\s*to\s+""")
        )
        removals.forEach { value = it.replace(value, " ") }
        return value.replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ").trim().take(100)
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { char ->
            val digit = when (char) {
                in '٠'..'٩' -> char.code - '٠'.code
                in '۰'..'۹' -> char.code - '۰'.code
                in '०'..'९' -> char.code - '०'.code
                else -> -1
            }
            append(if (digit >= 0) ('0'.code + digit).toChar() else char)
        }
    }
}
