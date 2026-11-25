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
                return "ठीक है। Memory save रद्द कर दिया गया है।"
            }
            return null
        }

        when (stage) {
            Stage.IDLE -> {
                val payload = extractRememberPayload(message) ?: return null

                if (payload.isBlank()) {
                    stage = Stage.WAITING_FOR_TOPIC
                    startedAt = now
                    return "ज़रूर। किस विषय को याद रखना है? विषय बताइए।"
                }

                if (payload.length > MAX_TOPIC_LENGTH) {
                    return "विषय थोड़ा छोटा करके बताइए।"
                }

                topic = payload
                stage = Stage.WAITING_FOR_CONTENT
                startedAt = now
                return "समझ गया। अब बताइए कि \"$topic\" के बारे में क्या याद रखना है?"
            }

            Stage.WAITING_FOR_TOPIC -> {
                val value = userMessage.trim()
                if (value.length < 2 || value.length > MAX_TOPIC_LENGTH) {
                    return "कृपया विषय 2 से 120 अक्षरों के बीच स्पष्ट बताइए।"
                }

                topic = value
                stage = Stage.WAITING_FOR_CONTENT
                startedAt = now
                return "ठीक है। अब \"$topic\" के बारे में सही जानकारी बताइए।"
            }

            Stage.WAITING_FOR_CONTENT -> {
                val content = userMessage.trim()
                if (content.length < 2 || content.length > MAX_CONTENT_LENGTH) {
                    return "जानकारी 2 से 1000 अक्षरों के बीच स्पष्ट बताइए।"
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
                    "आपकी जानकारी सुरक्षित Local Memory में PENDING के रूप में सेव हो गई है। इसे भरोसेमंद याददाश्त बनाने के लिए Memory Dashboard में जाकर Verify करें। Password, OTP या निजी credentials सेव न करें।"
                } else {
                    "यह जानकारी सेव नहीं हो सकी। इसमें संवेदनशील विवरण हो सकता है या Storage में समस्या आई है।"
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
