package com.idraak.quantum.engine

import android.content.Context
import java.util.Locale

/**
 * IDRAAK QUANTUM CORE
 *
 * Reads only explicitly verified local memories.
 * Pending, failed and rejected memories are never trusted.
 */
object VerifiedMemoryResponder {

    private val ignoredWords = setOf(
        "what", "which", "where", "when", "who",
        "tell", "me", "about", "please", "remember",
        "do", "you", "know", "did", "i", "my",
        "the", "is", "are", "was", "were",
        "kya", "kaise", "kab", "kahan", "kaun",
        "batao", "bataye", "mujhe", "mera", "meri",
        "hai", "tha", "thi", "ke", "ki", "ka",
        "क्या", "बताओ", "मुझे", "मेरा", "मेरी",
        "याद", "है", "था", "थी", "के", "की", "का",
        "کیا", "بتائیں", "مجھے", "میرا", "میری",
        "ہے", "تھا", "تھی", "کے", "کی", "کا"
    )

    /**
     * Returns a verified memory answer when a clear match exists.
     * Returns null when no reliable match is found.
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
                .asReversed()
                .firstOrNull { memory ->

                    if (
                        memory.status !=
                        LocalMemoryStore.Status.VERIFIED.name
                    ) {
                        return@firstOrNull false
                    }

                    val title = normalize(memory.title)
                    val content = normalize(memory.content)

                    if (
                        title.isBlank() ||
                        content.isBlank()
                    ) {
                        return@firstOrNull false
                    }

                    val exactMatch =
                        question == title ||
                        question.contains(title) ||
                        title.contains(question)

                    if (exactMatch) {
                        return@firstOrNull true
                    }

                    if (questionWords.isEmpty()) {
                        return@firstOrNull false
                    }

                    val titleWords = meaningfulWords(title)

                    titleWords.isNotEmpty() &&
                        titleWords.all { word ->
                            questionWords.contains(word)
                        }
                }

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
