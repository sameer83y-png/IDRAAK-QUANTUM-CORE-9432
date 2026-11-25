package com.idraak.quantum.engine

import android.content.Context
import java.util.Locale

/**
 * Explicit, voice-first memory capture.
 *
 * Idraak never silently stores ordinary conversation. The user must say
 * a clear remember command, then provide a topic and its information.
 * New records are encrypted by LocalMemoryStore and remain PENDING until
 * the user verifies them in Memory Dashboard.
 */
object MemoryCommandHandler {

    private const val TIMEOUT_MS = 120_000L
    private const val MAX_TOPIC_LENGTH = 120
    private const val MAX_CONTENT_LENGTH = 1000

    private enum class Stage {
        IDLE,
        WAITING_FOR_TOPIC,
        WAITING_FOR_CONTENT
    }

    private var stage = Stage.IDLE
    private var topic = ""
    private var startedAt = 0L

    @Synchronized
    fun handle(context: Context, userMessage: String): String? {
        val message = normalize(userMessage)
        if (message.isBlank()) return null

        val now = System.currentTimeMillis()

        if (stage != Stage.IDLE && now - startedAt > TIMEOUT_MS) {
            reset()
            return "Memory save का समय समाप्त हो गया। जब चाहें, फिर से बोलें: याद रखना।"
        }

        if (isCancel(message)) {
            if (stage != Stage.IDLE) {
                reset()
                return "ٹھیک ہے، یادداشت محفوظ کرنے کا عمل منسوخ کر دیا گیا ہے۔"
            }
            return null
        }

        when (stage) {
            Stage.IDLE -> {
                val payload = extractRememberPayload(message) ?: return null

                if (payload.isBlank()) {
                    stage = Stage.WAITING_FOR_TOPIC
                    startedAt = now
                    return "ضرور، کس موضوع کو یاد رکھنا ہے؟ موضوع بتائیں۔"
                }

                if (payload.length > MAX_TOPIC_LENGTH) {
                    return "موضوع ذرا مختصر بتائیں۔"
                }

                topic = payload
                stage = Stage.WAITING_FOR_CONTENT
                startedAt = now
                return "سمجھ گیا۔ اب بتائیں کہ \"$topic\" کے بارے میں کیا یاد رکھنا ہے؟"
            }

            Stage.WAITING_FOR_TOPIC -> {
                val value = userMessage.trim()
                if (value.length < 2 || value.length > MAX_TOPIC_LENGTH) {
                    return "موضوع دو سے ایک سو بیس حروف کے درمیان واضح بتائیں۔"
                }

                topic = value
                stage = Stage.WAITING_FOR_CONTENT
                startedAt = now
                return "ٹھیک ہے، اب \"$topic\" کے بارے میں درست معلومات بتائیں۔"
            }

            Stage.WAITING_FOR_CONTENT -> {
                val content = userMessage.trim()
                if (content.length < 2 || content.length > MAX_CONTENT_LENGTH) {
                    return "معلومات دو سے ایک ہزار حروف کے درمیان واضح بتائیں۔"
                }

                val category = if (isPreferenceTopic(topic)) {
                    LocalMemoryStore.Category.USER_PREFERENCE
                } else {
                    LocalMemoryStore.Category.GENERAL_FACT
                }

                val saved = try {
                    LocalMemoryStore.save(
                        context = context.applicationContext,
                        category = category,
                        title = topic,
                        content = content,
                        status = LocalMemoryStore.Status.PENDING
                    )
                } catch (_: Exception) {
                    false
                }

                reset()

                return if (saved) {
                    "آپ کی معلومات محفوظ مقامی یادداشت میں زیرِ تصدیق رکھ دی گئی ہیں۔ انہیں قابلِ اعتماد بنانے کے لیے یادداشت کے صفحے میں تصدیق کریں۔ پاس ورڈ یا خفیہ کوڈ محفوظ نہ کریں۔"
                } else {
                    "یہ معلومات محفوظ نہیں ہو سکیں۔ ممکن ہے ان میں نجی تفصیل ہو یا فون کی محفوظ جگہ میں مسئلہ آیا ہو۔"
                }
            }
        }
    }

    @Synchronized
    fun cancel() {
        reset()
    }

    @Synchronized
    fun isWaiting(): Boolean = stage != Stage.IDLE

    private fun extractRememberPayload(message: String): String? {
        val markers = listOf(
            "please remember this",
            "please remember that",
            "remember this",
            "remember that",
            "remember my",
            "remember about",
            "save this to memory",
            "save this in memory",
            "save to memory",
            "save in memory",
            "yaad rakh lo",
            "yaad rakhna",
            "yaad rakh lena",
            "याद रखना",
            "याद रखो",
            "याद रख लेना",
            "इसे याद रखना",
            "मेरी बात याद रखना",
            "یاد رکھنا",
            "یاد رکھو",
            "یاد رکھ لینا",
            "یہ یاد رکھنا",
            "میری بات یاد رکھنا"
        ).sortedByDescending { it.length }

        for (marker in markers) {
            if (message == marker) return ""
            if (message.startsWith("$marker ")) {
                return message.removePrefix(marker).trim()
            }
        }

        return null
    }

    private fun isPreferenceTopic(value: String): Boolean {
        val topic = normalize(value)
        return listOf(
            "preference", "preferred", "favorite", "favourite",
            "my", "pasand", "पसंद", "मेरी पसंद", "मेरा पसंदीदा",
            "پسند", "میری پسند"
        ).any { topic.contains(it) }
    }

    private fun isCancel(message: String): Boolean {
        return listOf(
            "cancel", "cancel memory", "stop memory",
            "रद्द करो", "रहने दो", "छोड़ दो",
            "منسوخ کرو", "رہنے دو", "چھوڑ دو"
        ).any { message == normalize(it) }
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun reset() {
        stage = Stage.IDLE
        topic = ""
        startedAt = 0L
    }
}
