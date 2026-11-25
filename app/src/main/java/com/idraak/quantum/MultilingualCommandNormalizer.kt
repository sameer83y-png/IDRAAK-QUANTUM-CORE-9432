package com.idraak.quantum

import java.util.Locale

/**
 * Converts a carefully bounded set of common multilingual command phrases
 * into the Urdu command vocabulary already understood by Idraak's offline
 * handlers. App names, contact names and search terms are preserved.
 *
 * Speech transcription can be multilingual; command coverage grows through
 * explicit phrase rules and verified user-taught aliases.
 */
object MultilingualCommandNormalizer {

    fun normalize(input: String): String {
        var value = input.trim().lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")
        if (value.isBlank()) return input

        // Preserve clear refusals: they must never become executable requests.
        val refusalPrefixes = listOf(
            "don't open ", "do not open ", "dont open ", "never open ",
            "मत खोलो ", "मत खोलना ", "मत खोलिए ",
            "mat kholo ", "mat kholna ",
            "ne pas ouvrir ", "nicht öffnen "
        )
        for (prefix in refusalPrefixes.sortedByDescending { it.length }) {
            if (value.startsWith(prefix)) {
                return "مت کھولو " + value.removePrefix(prefix).trim()
            }
        }

        val torchOff = listOf(
            "turn off the flashlight", "turn off flashlight", "switch off the flashlight",
            "switch off flashlight", "turn off torch", "switch off torch",
            "flashlight off", "torch off", "disable flashlight",
            "टॉर्च बंद करो", "टॉर्च बुझाओ", "फ्लैशलाइट बंद करो",
            "torch band karo", "torch bujha do", "flashlight band karo",
            "ٹارچ بند کرو", "ٹارچ بجھا دو"
        )
        if (torchOff.any { value == it || value.endsWith(" $it") }) {
            return "ٹارچ بند کرو"
        }

        val torchOn = listOf(
            "turn on the flashlight", "turn on flashlight", "switch on the flashlight",
            "switch on flashlight", "turn on torch", "switch on torch",
            "flashlight on", "torch on", "enable flashlight",
            "टॉर्च जलाओ", "टॉर्च चालू करो", "फ्लैशलाइट चालू करो",
            "torch chalu karo", "torch jalao", "flashlight chalu karo",
            "ٹارچ جلاؤ", "ٹارچ چالو کرو"
        )
        if (torchOn.any { value == it || value.endsWith(" $it") }) {
            return "ٹارچ جلاؤ"
        }

        val cameraOpen = listOf(
            "open camera", "open the camera", "launch camera", "start camera",
            "take me to camera", "कैमरा खोलो", "कैमरा खोल दो", "कैमरा चालू करो",
            "camera kholo", "camera khol do", "camera chalu karo",
            "کیمرہ کھولو", "کیمرہ کھول دو"
        )
        if (cameraOpen.any { value == it }) return "کیمرہ کھولو"

        val timeQuestions = listOf(
            "what time is it", "what's the time", "tell me the time",
            "current time", "time please", "¿qué hora es?", "wie spät ist es",
            "अभी कितने बजे हैं", "समय बताओ", "अभी टाइम बताओ",
            "abhi kitne baje hain", "samay batao",
            "وقت بتاؤ", "ابھی کیا وقت ہے"
        )
        if (timeQuestions.any { value == it }) return "وقت کیا ہے"

        val dateQuestions = listOf(
            "what is today's date", "what's today's date", "tell me today's date",
            "what date is it", "¿qué fecha es hoy?", "welches datum ist heute",
            "आज की तारीख बताओ", "आज तारीख क्या है", "aaj ki tareekh batao",
            "آج کی تاریخ بتاؤ", "آج کی تاریخ کیا ہے"
        )
        if (dateQuestions.any { value == it }) return "آج کی تاریخ کیا ہے"

        // App-opening phrases are normalized only when they are not web URLs.
        val openPrefixes = listOf(
            "could you please open ", "please open the ", "please open ",
            "could you open ", "open the app ", "open app ", "open the ",
            "launch the app ", "launch ", "start the app ", "start app ",
            "start ", "run the app ", "run ",
            "कृपया खोलो ", "ऐप खोलो ", "ऐप खोल दो ", "खोलो ", "खोल दो ",
            "जरा खोलो ", "जरा खोल दो ",
            "please kholo ", "app kholo ", "app khol do ", "khol do ",
            "kholna ", "zara kholo ", "zara khol do ", "chalu karo ",
            "افتح ", "افتح التطبيق ",
            "ouvrir ", "lance "
        )
        val prefix = openPrefixes.sortedByDescending { it.length }
            .firstOrNull { value.startsWith(it) }
        if (prefix != null) {
            val target = input.trim().drop(prefix.length).trim()
            if (target.isNotBlank() && !target.contains("://") &&
                !target.matches(Regex("(?i).*[a-z0-9-]+\\.[a-z]{2,}.*"))) {
                return "کھولو $target"
            }
        }

        return input.trim()
    }
}
