
package com.idraak.quantum.engine

import java.util.Locale

/**
 * Idraak Offline Screen Command Handler
 *
 * Converts simple Hindi and English commands into
 * Accessibility-based screen actions.
 *
 * This handler is not yet connected to OfflineBrain.
 */
object OfflineScreenCommandHandler {

    data class Result(
        val success: Boolean,
        val message: String,
        val intent: String
    )

    fun execute(userCommand: String): Result {

        val command = normalize(userCommand)

        if (command.isBlank()) {
            return Result(
                false,
                "कृपया स्क्रीन कमांड बताइए।",
                "EMPTY"
            )
        }

        // Back navigation
        if (matches(
                command,
                "back",
                "go back",
                "press back",
                "पीछे जाओ",
                "वापस जाओ",
                "बैक दबाओ"
            )
        ) {
            return perform("BACK") {
                ScreenControlBridge.back()
            }
        }

        // Home navigation
        if (matches(
                command,
                "go home",
                "open home",
                "home screen",
                "होम खोलो",
                "होम पर जाओ",
                "घर जाओ"
            )
        ) {
            return perform("HOME") {
                ScreenControlBridge.home()
            }
        }

        // Scroll forward
        if (matches(
                command,
                "scroll down",
                "swipe down",
                "नीचे स्क्रॉल करो",
                "नीचे जाओ",
                "नीचे करो"
            )
        ) {
            return perform("SCROLL_DOWN") {
                ScreenControlBridge.scrollForward()
            }
        }

        // Scroll backward
        if (matches(
                command,
                "scroll up",
                "swipe up",
                "ऊपर स्क्रॉल करो",
                "ऊपर जाओ",
                "ऊपर करो"
            )
        ) {
            return perform("SCROLL_UP") {
                ScreenControlBridge.scrollBackward()
            }
        }

        // Open notifications
        if (matches(
                command,
                "open notifications",
                "show notifications",
                "notification panel",
                "नोटिफिकेशन खोलो",
                "नोटिफिकेशन दिखाओ"
            )
        ) {
            return perform("NOTIFICATIONS") {
                ScreenControlBridge.openNotifications()
            }
        }

        // Find a requested screen text to click
        val targetText = extractClickTarget(command)

        if (targetText != null) {

            if (targetText.isBlank()) {
                return Result(
                    false,
                    "किस बटन पर क्लिक करना है, उसका नाम बताइए।",
                    "CLICK_EMPTY"
                )
            }

            // Do not trigger consequential actions without
            // a separate confirmation flow.
            if (isSensitiveTarget(targetText)) {
                return Result(
                    false,
                    "यह कार्रवाई महत्वपूर्ण हो सकती है। पहले अलग से पुष्टि जरूरी है।",
                    "CLICK_CONFIRMATION_REQUIRED"
                )
            }

            return perform("CLICK_TEXT") {
                ScreenControlBridge.tapText(targetText)
            }
        }

        return Result(
            false,
            "यह स्क्रीन कमांड समझ नहीं आई। कृपया स्पष्ट कमांड दीजिए।",
            "UNKNOWN"
        )
    }

    private fun perform(
        intent: String,
        action: () -> Boolean
    ): Result {

        if (!ScreenControlBridge.isServiceConnected()) {
            return Result(
                false,
                "Idraak Screen Control अभी चालू नहीं है। Android Accessibility Settings में इसे अनुमति दें।",
                "SERVICE_DISABLED"
            )
        }

        return try {
            val completed = action()

            if (completed) {
                Result(
                    true,
                    "कमांड सफलतापूर्वक पूरी हुई।",
                    intent
                )
            } else {
                Result(
                    false,
                    "कार्रवाई पूरी नहीं हुई। स्क्रीन पर जरूरी बटन या कंट्रोल नहीं मिला।",
                    "ACTION_FAILED"
                )
            }
        } catch (_: Exception) {
            Result(
                false,
                "स्क्रीन कंट्रोल में समस्या आई।",
                "ACTION_ERROR"
            )
        }
    }

    private fun extractClickTarget(command: String): String? {

        val prefixes = listOf(
            "click on ",
            "click ",
            "tap on ",
            "tap ",
            "press ",
            "क्लिक करो ",
            "दबाओ "
        )

        for (prefix in prefixes) {
            if (command.startsWith(prefix)) {
                return command.removePrefix(prefix).trim()
            }
        }

        val suffixes = listOf(
            " पर क्लिक करो",
            " को क्लिक करो",
            " दबाओ"
        )

        for (suffix in suffixes) {
            if (command.endsWith(suffix)) {
                return command.removeSuffix(suffix).trim()
            }
        }

        return null
    }

    private fun isSensitiveTarget(target: String): Boolean {

        val sensitiveWords = listOf(
            "send",
            "submit",
            "delete",
            "remove",
            "purchase",
            "buy",
            "pay",
            "confirm",
            "transfer",
            "भेजो",
            "भेजें",
            "डिलीट",
            "हटाओ",
            "खरीदो",
            "पेमेंट",
            "भुगतान",
            "पुष्टि",
            "ट्रांसफर"
        )

        val value = normalize(target)

        return sensitiveWords.any { word ->
            value.contains(normalize(word))
        }
    }

    private fun matches(
        command: String,
        vararg phrases: String
    ): Boolean {

        return phrases.any { phrase ->
            command == normalize(phrase)
        }
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^\\p{L}\\p{M}\\p{N}\\s]"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
