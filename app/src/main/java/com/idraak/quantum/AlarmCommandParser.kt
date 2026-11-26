package com.idraak.quantum

import java.util.Locale

/** Parses explicit offline alarm requests without guessing AM/PM. */
object AlarmCommandParser {
    sealed class Result {
        data object NotAlarm : Result()
        data object NeedsTime : Result()
        data object NeedsMeridiem : Result()
        data class Ready(val hour: Int, val minute: Int, val label: String) : Result()
    }

    private val markers = listOf(
        "set alarm", "alarm for", "alarm at", "alarm laga", "alarm lagao",
        "alarm lagana", "wake me up", "set an alarm",
        "الارم لگا", "الارم لگاؤ", "الارم لگانا", "الارم مقرر",
        "الارم بند", "الارم دکھاؤ", "صبح کا الارم", "شام کا الارم",
        "الارم بجا", "الارم"
    )

    fun isAlarmCommand(command: String): Boolean {
        val value = command.lowercase(Locale.ROOT)
        return markers.any { value.contains(it) }
    }

    fun parse(command: String): Result {
        val text = command.lowercase(Locale.ROOT)
        if (!isAlarmCommand(text)) return Result.NotAlarm
        val timeMatch = Regex("""(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?""")
            .find(text) ?: return Result.NeedsTime
        var hour = timeMatch.groupValues[1].toIntOrNull() ?: return Result.NeedsTime
        val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
        if (minute !in 0..59 || hour !in 0..23) return Result.NeedsTime

        val meridiem = timeMatch.groupValues[3]
        val isMorning = listOf("morning", "subah", "صبح", "सुबह").any { text.contains(it) }
        val isEvening = listOf("evening", "night", "shaam", "raat", "شام", "رات", "शाम", "रात")
            .any { text.contains(it) }
        if (meridiem == "pm" || (meridiem.isEmpty() && isEvening && hour in 1..11)) {
            if (hour in 1..11) hour += 12
        } else if (meridiem == "am" || (meridiem.isEmpty() && isMorning && hour == 12)) {
            if (hour == 12) hour = 0
        } else if (hour in 1..11) {
            return Result.NeedsMeridiem
        }

        val label = text
            .replace(Regex("""(?:set an alarm|set alarm|alarm at|alarm for|alarm laga do|alarm lagao|alarm lagana|wake me up)"""), " ")
            .replace(Regex("""\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?"""), " ")
            .replace(Regex("""(?:morning|evening|night|subah|shaam|raat|صبح|شام|رات|सुबह|शाम|रात|بجے|बजे)"""), " ")
            .replace(Regex("""[^\\p{L}\\p{M}\\p{N} ]"""), " ")
            .replace(Regex("""\\s+"""), " ")
            .trim()
            .take(60)
        return Result.Ready(hour, minute, label)
    }
}
