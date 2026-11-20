
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

    fun understand(userMessage: String): Decision {
        val message = normalize(userMessage)

        if (message.isBlank()) {
            return Decision(
                intent = "EMPTY",
                response = "कृपया मुझे कोई कमांड दीजिए।"
            )
        }

        return when {
            containsAny(message, "hello", "hi", "hey", "नमस्ते", "सलाम") ->
                Decision(
                    intent = "GREETING",
                    response = "नमस्ते! मैं Idraak हूँ। बताइए, क्या काम करना है?"
                )

            containsAny(
                message, "who are you", "tum kaun",
                "तुम कौन", "आप कौन", "तुम्हारा नाम"
            ) -> Decision(
                intent = "IDENTITY",
                response = "मैं Idraak हूँ। मैं अपना Offline Brain तैयार कर रहा हूँ।"
            )

            containsAny(message, "time", "समय", "कितने बजे", "वक्त") -> {
                val time = SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(Date())

                Decision("TIME", "अभी समय $time है।")
            }

            containsAny(
                message, "date", "तारीख",
                "आज कौन सा दिन", "आज की डेट"
            ) -> {
                val date = SimpleDateFormat(
                    "dd MMMM yyyy",
                    Locale.getDefault()
                ).format(Date())

                Decision("DATE", "आज की तारीख $date है।")
            }

            containsAny(
                message, "open ", "खोलो", "खोल दो",
                "चालू करो", "launch "
            ) -> Decision(
                intent = "OPEN_APP",
                response = "मैंने ऐप खोलने की कमांड समझ ली है। " +
                    "फोन कंट्रोल इंजन अभी कनेक्ट नहीं हुआ है।"
            )

            containsAny(
                message, "send message", "मैसेज भेजो",
                "message bhejo", "संदेश भेजो"
            ) -> Decision(
                intent = "SEND_MESSAGE",
                response = "मैं मैसेज भेजने की कमांड समझ गया हूँ। " +
                    "मैसेज भेजने से पहले तुम्हारी पुष्टि जरूरी होगी।",
                requiresConfirmation = true
            )

            containsAny(
                message, "flashlight", "torch",
                "टॉर्च", "फ्लैशलाइट"
            ) -> Decision(
                intent = "FLASHLIGHT",
                response = "मैंने टॉर्च कंट्रोल की कमांड समझ ली है। " +
                    "हार्डवेयर कंट्रोल अभी कनेक्ट नहीं हुआ है।"
            )

            containsAny(message, "help", "मदद", "क्या कर सकते हो") ->
                Decision(
                    intent = "HELP",
                    response = "मेरा Offline Brain तैयार हो रहा है। " +
                        "अभी मैं सामान्य कमांड पहचान सकता हूँ। " +
                        "फोन कंट्रोल अगले चरणों में जोड़ा जाएगा।"
                )

            else -> Decision(
                intent = "UNKNOWN",
                response = "मैंने तुम्हारी बात प्राप्त कर ली है, " +
                    "लेकिन अभी उसका सही अर्थ पहचान नहीं पाया। " +
                    "मुझे इस कमांड को समझने के लिए और नियम सीखने होंगे।"
            )
        }
    }

    fun respond(
        context: Context,
        userMessage: String
    ): String {
        val decision = understand(userMessage)

        recordExperience(
            context = context,
            intent = decision.intent,
            status = "UNDERSTOOD_NOT_EXECUTED"
        )

        return decision.response
    }

    fun recordExperience(
        context: Context,
        intent: String,
        status: String,
        steps: List<String> = emptyList()
    ) {
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
     * Negative and unverified states are checked FIRST.
     * Only explicit positive states can become SUCCESS or VERIFIED.
     */
    private fun mapMemoryStatus(
        status: String
    ): LocalMemoryStore.Status {
        val normalized = status
            .trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')

        val tokens = normalized.split("_").filter { it.isNotBlank() }

        val isNegativeOrUnverified =
            "NOT" in tokens ||
            "UNVERIFIED" in tokens ||
            "PENDING" in tokens ||
            "UNKNOWN" in tokens ||
            "UNDERSTOOD" in tokens ||
            "EXECUTED" in tokens

        if (isNegativeOrUnverified) {
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
            if (intent.isBlank()) return

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

    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    private fun containsAny(
        message: String,
        vararg phrases: String
    ): Boolean {
        return phrases.any { phrase ->
            message.contains(phrase.lowercase(Locale.ROOT))
        }
    }
}
