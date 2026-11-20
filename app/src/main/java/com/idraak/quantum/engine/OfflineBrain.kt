package com.idraak.quantum.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OfflineBrain {

    private const val PREFS_NAME = "idraak_offline_brain"
    private const val MEMORY_KEY = "experience_memory"
    private const val MAX_MEMORIES = 200

    data class Decision(
        val intent: String,
        val response: String,
        val requiresConfirmation: Boolean = false
    )

    /**
     * Main command understanding engine.
     * Action commands are checked before normal conversation.
     */
    fun understand(userMessage: String): Decision {
        val message = normalize(userMessage)

        if (message.isBlank()) {
            return Decision(
                intent = "EMPTY",
                response = "कृपया मुझे कोई कमांड दीजिए।"
            )
        }

        val wantsToSendMessage = containsAny(
            message,
            "send message",
            "send a message",
            "message bhejo",
            "msg bhejo",
            "मैसेज भेजो",
            "संदेश भेजो",
            "मैसेज भेजना"
        )

        val wantsFlashlight = containsAny(
            message,
            "flashlight",
            "torch",
            "turn on torch",
            "turn off torch",
            "टॉर्च",
            "फ्लैशलाइट",
            "टॉर्च जलाओ",
            "टॉर्च बंद"
        )

        val wantsToOpenApp = containsAny(
            message,
            "open",
            "launch",
            "खोलो",
            "खोल दो",
            "खोलना",
            "चालू करो",
            "ऐप खोल"
        )

        // Do not execute a command if the user has denied it.
        val hasActionRequest =
            wantsToSendMessage || wantsFlashlight || wantsToOpenApp

        if (hasActionRequest && containsAny(
                message,
                "don't",
                "do not",
                "dont",
                "never",
                "mat",
                "nahi",
                "nahin",
                "मत",
                "नहीं",
                "ना",
                "मत करना",
                "मत खोलो",
                "मत भेजो",
                "बंद मत करना"
            )
        ) {
            return Decision(
                intent = "CANCELLED",
                response = "ठीक है। आपने इस कमांड को मना किया है। " +
                    "मैं कोई कार्रवाई नहीं करूँगा।"
            )
        }

        // Multiple actions are not executed automatically.
        val actionCount = listOf(
            wantsToSendMessage,
            wantsFlashlight,
            wantsToOpenApp
        ).count { it }

        if (actionCount > 1) {
            return Decision(
                intent = "MULTIPLE_ACTIONS",
                response = "मैंने एक से अधिक कमांड पहचानी हैं। " +
                    "कृपया एक समय में एक कमांड दीजिए।"
            )
        }

        if (wantsToSendMessage) {
            return Decision(
                intent = "SEND_MESSAGE",
                response = "मैं मैसेज भेजने की कमांड समझ गया हूँ। " +
                    "मैसेज भेजने से पहले आपकी पुष्टि जरूरी होगी। " +
                    "मैसेज भेजने वाला कंट्रोल अभी कनेक्ट नहीं है।",
                requiresConfirmation = true
            )
        }

        if (wantsFlashlight) {
            return Decision(
                intent = "FLASHLIGHT",
                response = "मैंने टॉर्च कंट्रोल की कमांड समझ ली है। " +
                    "फोन का हार्डवेयर कंट्रोल अभी कनेक्ट नहीं हुआ है।"
            )
        }

        if (wantsToOpenApp) {
            return Decision(
                intent = "OPEN_APP",
                response = "मैंने ऐप खोलने की कमांड समझ ली है। " +
                    "ऐप खोलने वाला Phone Control Engine अभी कनेक्ट नहीं है।"
            )
        }

        // Date and time
        if (containsAny(
                message,
                "time",
                "what time",
                "current time",
                "समय",
                "कितने बजे",
                "वक्त",
                "अभी टाइम"
            )
        ) {
            val time = SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            ).format(Date())

            return Decision(
                intent = "TIME",
                response = "अभी समय $time है।"
            )
        }

        if (containsAny(
                message,
                "date",
                "today's date",
                "today date",
                "तारीख",
                "आज कौन सा दिन",
                "आज की डेट",
                "आज की तारीख"
            )
        ) {
            val date = SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.getDefault()
            ).format(Date())

            return Decision(
                intent = "DATE",
                response = "आज की तारीख $date है।"
            )
        }

        // Identity
        if (containsAny(
                message,
                "who are you",
                "what is your name",
                "your name",
                "tum kaun",
                "aap kaun",
                "तुम कौन",
                "आप कौन",
                "तुम्हारा नाम",
                "आपका नाम"
            )
        ) {
            return Decision(
                intent = "IDENTITY",
                response = "मैं Idraak हूँ। आपका Offline AI Assistant। " +
                    "मेरा Offline Brain अभी विकसित किया जा रहा है।"
            )
        }

        // Help
        if (containsAny(
                message,
                "help",
                "what can you do",
                "your features",
                "मदद",
                "क्या कर सकते हो",
                "क्या कर सकते हैं",
                "तुम्हारे फीचर"
            )
        ) {
            return Decision(
                intent = "HELP",
                response = "मैं अभी सामान्य बातचीत, समय और तारीख " +
                    "समझ सकता हूँ। ऐप खोलने, मैसेज भेजने और टॉर्च " +
                    "कंट्रोल के लिए Phone Control Engine जोड़ना बाकी है।"
            )
        }

        // Greeting is checked after action commands.
        if (containsAny(
                message,
                "hello",
                "hi",
                "hey",
                "नमस्ते",
                "सलाम",
                "आदाब",
                "राम राम"
            )
        ) {
            return Decision(
                intent = "GREETING",
                response = "नमस्ते! मैं Idraak हूँ। बताइए, क्या काम करना है?"
            )
        }

        return Decision(
            intent = "UNKNOWN",
            response = "मैंने आपकी बात प्राप्त कर ली है, " +
                "लेकिन अभी उसका सही अर्थ पहचान नहीं पाया। " +
                "इस कमांड को समझने के लिए मुझे और नियमों की जरूरत है।"
        )
    }

    /**
     * Existing MainActivity compatibility.
     * Only actionable commands are automatically recorded.
     */
    fun respond(
        context: Context,
        userMessage: String
    ): String {
        val decision = understand(userMessage)

        when (decision.intent) {
            "OPEN_APP",
            "SEND_MESSAGE",
            "FLASHLIGHT" -> {
                recordExperience(
                    context = context,
                    intent = decision.intent,
                    status = "UNDERSTOOD_NOT_EXECUTED"
                )
            }
        }

        return decision.response
    }

    /**
     * Record an actual outcome when another component reports it.
     * Never mark an action successful merely because it was understood.
     */
    fun recordExperience(
        context: Context,
        intent: String,
        status: String,
        steps: List<String> = emptyList()
    ) {
        if (intent.isBlank()) return

        saveLegacyExperience(
            context = context,
            intent = intent,
            status = status,
            steps = steps
        )

        saveToLocalMemory(
            context = context,
            intent = intent,
            status = status
        )
    }

    /**
     * Conservative memory status mapping.
     * Unverified or ambiguous outcomes remain PENDING.
     */
    private fun mapMemoryStatus(
        status: String
    ): LocalMemoryStore.Status {
        val normalized = status
            .trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')

        val tokens = normalized
            .split("_")
            .filter { it.isNotBlank() }

        val negativeOrUnverified =
            "NOT" in tokens ||
            "UNVERIFIED" in tokens ||
            "PENDING" in tokens ||
            "UNKNOWN" in tokens ||
            "UNDERSTOOD" in tokens ||
            "EXECUTED" in tokens

        if (negativeOrUnverified) {
            return LocalMemoryStore.Status.PENDING
        }

        return when {
            "REJECTED" in tokens ->
                LocalMemoryStore.Status.REJECTED

            "FAILED" in tokens || "FAILURE" in tokens ->
                LocalMemoryStore.Status.FAILED

            "VERIFIED" in tokens ->
                LocalMemoryStore.Status.VERIFIED

            "SUCCESS" in tokens ->
                LocalMemoryStore.Status.SUCCESS

            else ->
                LocalMemoryStore.Status.PENDING
        }
    }

    private fun saveToLocalMemory(
        context: Context,
        intent: String,
        status: String
    ) {
        try {
            val memoryStatus = mapMemoryStatus(status)

            LocalMemoryStore.saveExperience(
                context = context,
                commandType = intent,
                outcome = memoryStatus,
                lesson = "Recorded outcome: ${status.take(60)}"
            )
        } catch (_: Exception) {
            // Memory errors must not crash the assistant.
        }
    }

    fun getExperienceCount(context: Context): Int {
        return readMemories(context).length()
    }

    fun getRecentExperiences(
        context: Context,
        limit: Int = 20
    ): List<String> {
        val memories = readMemories(context)
        val result = mutableListOf<String>()

        val safeLimit = limit.coerceAtLeast(0)
        val start = (memories.length() - safeLimit).coerceAtLeast(0)

        for (index in start until memories.length()) {
            val item = memories.optJSONObject(index) ?: continue

            result.add(
                "${item.optString("intent")}: " +
                    item.optString("status")
            )
        }

        return result
    }

    fun getLocalMemoryCount(context: Context): Int {
        return try {
            LocalMemoryStore.getCount(context)
        } catch (_: Exception) {
            0
        }
    }

    private fun saveLegacyExperience(
        context: Context,
        intent: String,
        status: String,
        steps: List<String> = emptyList()
    ) {
        try {
            val memories = readMemories(context)

            val record = JSONObject().apply {
                put("intent", intent)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
                put("steps", JSONArray(steps))
            }

            memories.put(record)

            while (memories.length() > MAX_MEMORIES) {
                memories.remove(0)
            }

            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(MEMORY_KEY, memories.toString())
                .apply()

        } catch (_: Exception) {
            // Legacy memory failure must not crash the assistant.
        }
    }

    private fun readMemories(context: Context): JSONArray {
        return try {
            val saved = context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(MEMORY_KEY, null)

            if (saved.isNullOrBlank()) {
                JSONArray()
            } else {
                JSONArray(saved)
            }
        } catch (_: Exception) {
            JSONArray()
        }
    }

    /**
     * Normalize punctuation and spaces while preserving
     * Hindi/Urdu and other Unicode letters.
     */
    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Match complete words or phrases.
     * Prevents "hi" from matching inside "this".
     */
    private fun containsAny(
        message: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any { rawPhrase ->
            val phrase = normalize(rawPhrase)

            if (phrase.isBlank()) {
                false
            } else {
                val pattern = Regex(
                    "(?<![\\p{L}\\p{M}\\p{N}])" +
                        Regex.escape(phrase) +
                        "(?![\\p{L}\\p{M}\\p{N}])"
                )

                pattern.containsMatchIn(message)
            }
        }
    }
}
