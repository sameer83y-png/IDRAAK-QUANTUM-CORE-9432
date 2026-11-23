package com.idraak.quantum

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

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
            .lowercase()
            .replace(Regex("\\s+"), " ")

        if (command.isBlank()) return false

        val callWords = listOf(
            "call",
            "कॉल",
            "फोन",
            "फोने",
            "فون"
        )

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

        val hasCallWord = callWords.any {
            command.contains(it)
        }

        val hasActionWord = actionWords.any {
            command.contains(it)
        }

        val directEnglishCommand =
            command.startsWith("call ") ||
                command == "call someone" ||
                command == "make a call"

        if (!directEnglishCommand &&
            !(hasCallWord && hasActionWord)
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
