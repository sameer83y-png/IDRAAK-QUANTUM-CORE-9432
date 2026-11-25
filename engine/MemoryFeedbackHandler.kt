
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
            return "درستگی کا وقت ختم ہو گیا۔ دوبارہ بتائیں کہ کیا غلط تھا۔"
        }

        if (isCancel(message)) {
            if (stage != Stage.IDLE) {
                reset()
                return "ٹھیک ہے، درستگی کا عمل منسوخ کر دیا گیا ہے۔"
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

                return "معذرت، بتائیں کس معلومات یا موضوع میں غلطی ہوئی تھی؟"
            }

            Stage.WAITING_FOR_TOPIC -> {
                if (message.length < 2) {
                    return "اس معلومات کا موضوع ذرا واضح بتائیں۔"
                }

                if (message.length > 120) {
                    return "موضوع ذرا مختصر بتائیں۔"
                }

                topic = userMessage.trim()
                stage = Stage.WAITING_FOR_CORRECTION
                startedAt = now

                return "سمجھ گیا۔ اب بتائیں کہ \"$topic\" کے بارے میں درست معلومات کیا ہیں؟"
            }

            Stage.WAITING_FOR_CORRECTION -> {
                val correction = userMessage.trim()

                if (correction.length < 2) {
                    return "درست معلومات واضح طور پر بتائیں۔"
                }

                if (correction.length > 2000) {
                    return "معلومات بہت طویل ہیں۔ انہیں دو ہزار حروف کے اندر رکھیں۔"
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
                    "شکریہ۔ آپ کی درستگی مقامی یادداشت میں زیرِ تصدیق محفوظ ہو گئی ہے۔ اسے قابلِ اعتماد بنانے کے لیے یادداشت کے صفحے میں خود تصدیق کریں۔"
                } else {
                    "درستگی محفوظ نہیں ہو سکی۔ معلومات میں نجی تفصیل ہو سکتی ہے یا محفوظ جگہ میں مسئلہ آیا ہے۔"
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
