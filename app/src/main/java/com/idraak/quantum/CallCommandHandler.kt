package com.idraak.quantum

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Detects clear phone-call commands and opens
 * the existing safe CallControllerActivity.
 *
 * Contact selection and confirmation remain required.
 */
object CallCommandHandler {

    fun tryHandle(
        context: Context,
        message: String,
        launchActivity: Boolean = true
    ): Boolean {

        val command = message
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (command.isBlank()) return false

        // Phone power, lock, and silent-mode commands are not call requests.
        // Exclude them before the broad Hindi/Urdu "phone + action" matcher.
        val isPhoneControlCommand = listOf(
            "फोन बंद", "फोन लॉक", "फोन साइलेंट", "फोन चालू", "फोन म्यूट",
            "मोबाइल बंद", "मोबाइल लॉक", "मोबाइल चालू", "मोबाइल साइलेंट",
            "phone off", "phone on", "turn off phone", "switch off phone",
            "turn on phone", "switch on phone", "lock phone", "silent phone",
            "phone silent", "put phone on silent", "mobile off", "mobile on",
            "فون بند", "فون لاک", "فون چالو", "فون آن", "فون خاموش",
            "فون خاموش کرو", "فون خاموش کردو", "فون سائلنٹ",
            "موبائل بند", "موبائل لاک", "موبائل چالو", "موبائل آن",
            "موبائل خاموش", "موبائل سائلنٹ"
        ).any { command.startsWith(it) }

        if (isPhoneControlCommand) return false

        // Also reject phone-setting requests where the control word appears
        // after extra words, e.g. "फोन की आवाज बंद करो" or "فون کا والیوم کم کرو".
        val hasPhoneDeviceWord = listOf(
            "फोन", "मोबाइल", "phone", "mobile", "فون", "موبائل"
        ).any { word ->
            Regex("""(^|\s)""" + Regex.escape(word) + """(?=\s|$)""")
                .containsMatchIn(command)
        }
        val phoneControlTerms = listOf(
            "बंद", "लॉक", "साइलेंट", "म्यूट", "चालू",
            "आवाज़", "आवाज", "वॉल्यूम", "कम", "ज्यादा",
            "خاموش", "بند", "لاک", "سائلنٹ", "آواز",
            "والیوم", "کم", "زیادہ",
            "off", "on", "silent", "mute", "volume", "lock"
        )
        val hasPhoneControlTerm = phoneControlTerms.any { word ->
            Regex("""(^|\s)""" + Regex.escape(word) + """(?=\s|$)""")
                .containsMatchIn(command)
        }

        if (hasPhoneDeviceWord && hasPhoneControlTerm) return false

        // "फोन" alone is not enough: phrases such as
        // "फोन बंद करो" must never open the call flow.
        val hasCallWord = Regex(
            """(^|\s)(call|कॉल|کال|فون)(?=\s|$)"""
        ).containsMatchIn(command)

        val hasHindiPhoneCallPhrase = Regex(
            """(^|\s)फोन\s+(करो|कर दो|लगाओ|लगा दो|मिलाओ|मिला दो|करें|लगाइए)(?=\s|$)"""
        ).containsMatchIn(command)

        val actionWords = listOf(
            "karo",
            "kar do",
            "lagao",
            "laga do",
            "milao",
            "milaa do",
            "करो",
            "कर दो",
            "लगाओ",
            "लगा दो",
            "मिलाओ",
            "मिला दो",
            "करें",
            "लगाइए",
            "کرو",
            "ملاو"
        )

        val hasActionWord = actionWords.any { action ->
            val actionPattern =
                """(^|\s)""" + Regex.escape(action) + """(?=\s|$)"""
            Regex(actionPattern).containsMatchIn(command)
        }

        val directEnglishCommand = Regex(
            """^(please\s+)?(call|phone call)\b"""
        ).containsMatchIn(command) ||
            command == "make a call"

        if (
            !directEnglishCommand &&
            !(hasCallWord && hasActionWord) &&
            !hasHindiPhoneCallPhrase
        ) {
            return false
        }

        if (!launchActivity) return true

        return try {
            val intent = Intent(
                context,
                CallControllerActivity::class.java
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true

        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
