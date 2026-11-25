package com.idraak.quantum.engine

import android.content.Context
import java.util.Locale

/**
 * User-approved Urdu command aliases.
 *
 * A new alias is stored as PENDING and is never executed until the user
 * verifies it in the local Memory Dashboard.
 */
object LearnedCommandStore {

    private const val ALIAS_PREFIX = "command_alias:"

    fun handleTeaching(context: Context, message: String): String? {
        val value = normalize(message)
        val prefixes = listOf("جب میں کہوں ", "جب بھی میں کہوں ", "اگر میں کہوں ")
        val prefix = prefixes.firstOrNull { value.startsWith(it) } ?: return null
        val remainder = value.removePrefix(prefix).trim()
        val separator = " تو "
        val splitAt = remainder.indexOf(separator)
        if (splitAt <= 0) {
            return "براہِ کرم اس طرح بتائیں: جب میں کہوں مخصوص جملہ، تو یوٹیوب کھولو۔"
        }

        val alias = remainder.substring(0, splitAt).trim()
        val action = remainder.substring(splitAt + separator.length).trim()
        if (alias.length !in 2..80 || action.isBlank() || action.length > 120) {
            return "جملہ یا مطلوبہ کام واضح نہیں۔ دوبارہ آسان الفاظ میں بتائیں۔"
        }

        val decision = OfflineBrain.understand(action)
        val allowed = decision.intent in setOf(
            "OPEN_APP", "FLASHLIGHT", "SCREEN_CONTROL", "TIME", "DATE"
        )
        if (!allowed) {
            return "یہ کام ابھی محفوظ مقامی حکم کے طور پر شامل نہیں کیا جا سکتا۔ پہلے کوئی واضح اور دستیاب اردو حکم بتائیں۔"
        }

        val saved = try {
            LocalMemoryStore.proposeLearning(
                context = context.applicationContext,
                topic = ALIAS_PREFIX + alias,
                lesson = action
            )
        } catch (_: Exception) {
            false
        }

        return if (saved) {
            "یہ نئی ترکیب مقامی یادداشت میں زیرِ تصدیق محفوظ ہو گئی ہے۔ یادداشت کے صفحے میں تصدیق کے بعد ہی ادراک اسے استعمال کرے گا۔"
        } else {
            "نئی ترکیب محفوظ نہیں ہو سکی۔ دوبارہ کوشش کریں۔"
        }
    }

    fun resolve(context: Context, spokenCommand: String): String? {
        val phrase = normalize(spokenCommand)
        if (phrase.isBlank()) return null

        return try {
            LocalMemoryStore.getVerifiedLearning(
                context.applicationContext,
                limit = 100
            ).firstOrNull { record ->
                record.title.startsWith(ALIAS_PREFIX) &&
                    normalize(record.title.removePrefix(ALIAS_PREFIX)) == phrase
            }?.content
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
