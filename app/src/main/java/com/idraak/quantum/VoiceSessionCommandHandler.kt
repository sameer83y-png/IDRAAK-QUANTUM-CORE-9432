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
            .removePrefix("idraak ")
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
            "سننا روک دیں"
        )

        return normalized in stopPhrases
    }
}
