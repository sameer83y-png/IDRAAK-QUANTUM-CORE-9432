
package com.idraak.quantum.engine

import android.content.Context
import java.util.Locale

/**
 * IDRAAK QUANTUM CORE
 *
 * Handles explicit user corrections.
 *
 * Flow:
 * 1. User says that an answer was wrong.
 * 2. Idraak asks for the topic.
 * 3. Idraak asks for the correct information.
 * 4. The correction is saved as PENDING.
 * 5. User must verify it in Memory Dashboard.
 *
 * Corrections are never trusted automatically.
 */
object MemoryFeedbackHandler {

    private const val TIMEOUT_MS = 120_000L

    private enum class Stage {
        IDLE,
        WAITING_FOR_TOPIC,
        WAITING_FOR_CORRECTION
    }

    private var stage = Stage.IDLE
    private var topic = ""
    private var startedAt = 0L

    @Synchronized
    fun handle(
        context: Context,
        userMessage: String
    ): String? {

        val message = normalize(userMessage)

        if (message.isBlank()) {
            return null
        }

        val now = System.currentTimeMillis()

        if (stage != Stage.IDLE &&
            now - startedAt > TIMEOUT_MS
        ) {
            reset()
            return "Correction का समय समाप्त हो गया। कृपया दोबारा बताइए कि क्या गलत था।"
        }

        if (isCancel(message)) {
            if (stage != Stage.IDLE) {
                reset()
                return "ठीक है। Correction रद्द कर दिया गया है।"
            }
            return null
        }

        when (stage) {

            Stage.IDLE -> {
                if (!isCorrectionRequest(message)) {
                    return null
                }

                stage = Stage.WAITING_FOR_TOPIC
                startedAt = now

                return "माफ कीजिए। बताइए, किस जानकारी या विषय में गलती हुई थी?"
            }

            Stage.WAITING_FOR_TOPIC -> {
                if (message.length < 2) {
                    return "कृपया उस जानकारी का विषय थोड़ा स्पष्ट बताइए।"
                }

                if (message.length > 120) {
                    return "विषय थोड़ा छोटा करके बताइए।"
                }

                topic = userMessage.trim()
                stage = Stage.WAITING_FOR_CORRECTION
                startedAt = now

                return "समझ गया। अब बताइए कि \"$topic\" के बारे में सही जानकारी क्या है?"
            }

            Stage.WAITING_FOR_CORRECTION -> {
                val correction = userMessage.trim()

                if (correction.length < 2) {
                    return "कृपया सही जानकारी स्पष्ट रूप से बताइए।"
                }

                if (correction.length > 2000) {
                    return "जानकारी बहुत लंबी है। कृपया इसे 2000 अक्षरों के अंदर लिखें।"
                }

                val saved = try {
                    LocalMemoryStore.saveCorrection(
                        context = context.applicationContext,
                        topic = topic,
                        correctedInformation = correction
                    )
                } catch (_: Exception) {
                    false
                }

                reset()

                return if (saved) {
                    "धन्यवाद। आपकी Correction Local Memory में PENDING के रूप में सेव हो गई है। इसे Verified बनाने के लिए Memory Dashboard में जाकर खुद Verify करना होगा।"
                } else {
                    "Correction सेव नहीं हो सकी। जानकारी में कोई संवेदनशील विवरण हो सकता है या Storage में समस्या आई है।"
                }
            }
        }
    }

    @Synchronized
    fun cancel() {
        reset()
    }

    @Synchronized
    fun isWaiting(): Boolean {
        return stage != Stage.IDLE
    }

    private fun isCorrectionRequest(
        message: String
    ): Boolean {

        val phrases = listOf(
            "that is wrong",
            "that's wrong",
            "this is wrong",
            "you are wrong",
            "your answer is wrong",
            "wrong answer",
            "incorrect answer",
            "that was incorrect",
            "please correct that",
            "let me correct you",
            "that is not correct",

            "ye galat hai",
            "yeh galat hai",
            "aap galat ho",
            "tum galat ho",
            "jawab galat hai",
            "galat jawab",
            "sahi jawab nahi",
            "meri baat galat samjhe",

            "यह गलत है",
            "ये गलत है",
            "आप गलत हैं",
            "तुम गलत हो",
            "जवाब गलत है",
            "गलत जवाब",
            "सही जवाब नहीं",
            "आपने गलत समझा",

            "یہ غلط ہے",
            "آپ غلط ہیں",
            "جواب غلط ہے",
            "غلط جواب",
            "آپ نے غلط سمجھا"
        )

        return phrases.any { phrase ->
            val normalizedPhrase = normalize(phrase)

            message == normalizedPhrase ||
                message.startsWith("$normalizedPhrase ") ||
                message.endsWith(" $normalizedPhrase")
        }
    }

    private fun isCancel(
        message: String
    ): Boolean {

        val phrases = listOf(
            "cancel correction",
            "cancel",
            "stop correction",
            "never mind",
            "rehne do",
            "chhodo",
            "रद्द करो",
            "रहने दो",
            "छोड़ दो",
            "منسوخ کرو",
            "رہنے دو"
        )

        return phrases.any {
            message == normalize(it)
        }
    }

    private fun normalize(
        value: String
    ): String {
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

    private fun reset() {
        stage = Stage.IDLE
        topic = ""
        startedAt = 0L
    }
}
