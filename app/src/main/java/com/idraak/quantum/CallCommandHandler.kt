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
        message: String
    ): Boolean {

        val command = message
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (command.isBlank()) return false

        // "फोन" alone is not enough: phrases such as
        // "फोन बंद करो" must never open the call flow.
        val hasCallWord = Regex(
            """(^|\\s)(call|कॉल|کال|فون)(?=\\s|$)"""
        ).containsMatchIn(command)

        val hasHindiPhoneCallPhrase = Regex(
            """(^|\\s)फोन\\s+(करो|कर दो|लगाओ|लगा दो|मिलाओ|मिला दो|करें|लगाइए)(?=\\s|$)"""
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
            Regex(
                """(^|\\s)${Regex.escape(action)}(?=\\s|$)"""
            ).containsMatchIn(command)
        }

        val directEnglishCommand = Regex(
            """^(please\\s+)?(call|phone call)\\b"""
        ).containsMatchIn(command) ||
            command == "make a call"

        if (
            !directEnglishCommand &&
            !(hasCallWord && hasActionWord) &&
            !hasHindiPhoneCallPhrase
        ) {
            return false
        }

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
