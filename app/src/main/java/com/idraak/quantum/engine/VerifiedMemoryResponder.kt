
package com.idraak.quantum.engine

import android.content.Context
import java.util.Locale

/**
 * IDRAAK QUANTUM CORE
 *
 * Safe verified-memory response engine.
 *
 * Rules:
 * - Only explicitly VERIFIED memories are considered.
 * - Only trusted categories are supplied by LocalMemoryStore.
 * - Weak or ambiguous matches are rejected.
 * - More specific matches are preferred.
 * - Newer verified memories win when relevance is equal.
 * - No guess is presented as remembered information.
 */
object VerifiedMemoryResponder {

    private val ignoredWords = setOf(
        // English
        "what", "which", "where", "when", "who",
        "whom", "whose", "tell", "me", "about",
        "please", "remember", "do", "does", "did",
        "you", "your", "i", "my", "mine",
        "the", "is", "are", "was", "were",
        "am", "a", "an", "of", "to", "for",
        "and", "in", "on", "at", "it",

        // Hindi / Hinglish
        "kya", "kaise", "kab", "kahan", "kaun",
        "batao", "bataye", "bataiye", "mujhe",
        "mera", "meri", "mere", "tumhara",
        "aapka", "hai", "hain", "tha", "thi",
        "the", "ke", "ki", "ka", "ko",
        "mein", "me", "aur", "ya",

        // Hindi script
        "क्या", "बताओ", "बताइए", "मुझे",
        "मेरा", "मेरी", "मेरे", "तुम्हारा",
        "आपका", "है", "हैं", "था", "थी",
        "थे", "के", "की", "का", "को",
        "में", "और", "या", "याद",

        // Urdu
        "کیا", "بتائیں", "بتاؤ", "مجھے",
        "میرا", "میری", "میرے", "آپ",
        "آپکا", "ہے", "ہیں", "تھا", "تھی",
        "کے", "کی", "کا", "کو", "میں",
        "اور", "یا"
    )

    /**
     * Returns an answer only when a reliable verified match exists.
     * Returns null when no sufficiently clear match is found.
     */
    fun answer(
        context: Context,
        userQuestion: String
    ): String? {

        val question = normalize(userQuestion)

        if (question.isBlank()) {
            return null
        }

        return try {
            val memories = LocalMemoryStore.getTrustedKnowledge(
                context = context.applicationContext,
                limit = 100
            )

            if (memories.isEmpty()) {
                return null
            }

            val questionWords = meaningfulWords(question)

            val matchingMemory = memories
                .filter { memory ->

                    if (
                        memory.status !=
                        LocalMemoryStore.Status.VERIFIED.name
                    ) {
                        return@filter false
                    }

                    val title = normalize(memory.title)
                    val content = memory.content.trim()

                    if (title.isBlank() || content.isBlank()) {
                        return@filter false
                    }

                    if (question == title) {
                        return@filter true
                    }

                    val titleWords = meaningfulWords(title)

                    if (titleWords.size < 2) {
                        return@filter false
                    }

                    if (questionWords.isEmpty()) {
                        return@filter false
                    }

                    titleWords.all { word ->
                        word in questionWords
                    }
                }
                .sortedWith(
                    compareByDescending<LocalMemoryStore.MemoryRecord> {
                        meaningfulWords(it.title).size
                    }.thenByDescending {
                        question.contains(normalize(it.title))
                    }.thenByDescending {
                        it.updatedAt
                    }.thenByDescending {
                        it.createdAt
                    }
                )
                .firstOrNull()

            matchingMemory?.let { memory ->
                "मेरी Verified Memory के अनुसार: ${memory.content}"
            }

        } catch (_: Exception) {
            null
        }
    }

    private fun meaningfulWords(value: String): Set<String> {
        return normalize(value)
            .split(Regex("\\s+"))
            .filter { word ->
                word.length > 1 &&
                    word !in ignoredWords
            }
            .toSet()
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
