package com.idraak.quantum

import java.util.Locale

/**
 * Recognizes explicit requests to stop Idraak voice listening.
 * A bare "stop" is intentionally not treated as a stop-listening command.
 */
object VoiceSessionCommandHandler {

    fun isStopListeningCommand(command: String): Boolean {
        val normalized = command
            .trim()
            .lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}'\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("ادراک ")
            .removePrefix("idraak ")
            .removePrefix("इद्राक ")
            .trim()

        if (normalized.isBlank()) return false

        val stopPhrases = setOf(
            "سننا بند کرو",
            "سننا بند کر دو",
            "آواز سننا بند کرو",
            "آواز سننا بند کر دو",
            "آواز بند کرو",
            "آواز بند کر دو",
            "ادراک سننا بند کرو",
            "پس منظر میں سننا بند کرو",
            "پس منظر کی آواز بند کرو",
            "وائس بند کرو",
            "وائس بند کر دو",
            "سننا روک دو",
            "سننا روک دیں",
            "سننا بند کریں",
            "آواز سننا بند کریں",
            "آواز سننا روک دیں",
            "میری بات سننا بند کرو",
            "میری بات سننا بند کریں",
            "ادراک سننا بند کریں",
            "ادراک خاموش ہو جاؤ",
            "ادراک خاموش ہو جائیں",
            "خاموش ہو جاؤ",
            "خاموش ہو جائیں",
            "وائس سننا بند کرو",
            "وائس سننا بند کریں",
            "مائیک بند کرو",
            "مائیک بند کر دو",
            "مائیک بند کریں",
            "اب مت سنو",
            "اب سننا بند کرو",
            "بس اب سننا بند کرو",

            // English and Roman Urdu
            "stop listening",
            "stop hearing",
            "stop voice",
            "stop background listening",
            "stop listening now",
            "sunna band karo",
            "sunna band kar do",
            "awaz sunna band karo",
            "awaaz sunna band karo",
            "mic band karo",
            "mic band kar do",

            // Hindi
            "सुनना बंद करो",
            "सुनना बंद कर दो",
            "आवाज़ सुनना बंद करो",
            "आवाज सुनना बंद करो",
            "माइक बंद करो",
            "माइक बंद कर दो",
            "अब मत सुनो",

            // Arabic
            "توقف عن الاستماع",
            "أوقف الاستماع",
            "أوقف الاستماع الآن",
            "أوقف الميكروفون",
            "أوقف سماع الصوت"
        )

        return normalized in stopPhrases
    }
}
