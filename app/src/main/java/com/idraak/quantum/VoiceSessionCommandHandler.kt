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
            "stop listening",
            "stop voice",
            "stop voice chat",
            "stop background voice",
            "turn off voice",
            "disable voice listening",
            "सुनना बंद करो",
            "सुनना बंद कर दो",
            "आवाज बंद करो",
            "आवाज़ बंद करो",
            "वॉइस बंद करो",
            "वॉइस बंद कर दो",
            "सुनना रोक दो",
            "سننا بند کرو",
            "سننا بند کر دو",
            "آواز بند کرو",
            "وائس بند کرو",
            "وائس بند کر دو"
        )

        return normalized in stopPhrases
    }
}
