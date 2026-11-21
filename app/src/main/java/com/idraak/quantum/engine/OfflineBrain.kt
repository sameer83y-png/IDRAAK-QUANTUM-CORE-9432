
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
    private const val CONFIRMATION_TIMEOUT_MS = 30_000L

    private data class PendingClick(
        val target: String,
        val createdAt: Long
    )

    // Pending confirmation stays in memory only.
    // It is cleared after confirmation, cancellation or timeout.
    private var pendingClick: PendingClick? = null

    data class Decision(
        val intent: String,
        val response: String,
        val requiresConfirmation: Boolean = false
    )

    fun understand(userMessage: String): Decision {

        val message = normalize(userMessage)

        if (message.isBlank()) {
            return Decision("EMPTY", "कृपया मुझे कोई कमांड दीजिए।")
        }

        val isNegative = containsAny(
            message,
            "don't", "do not", "dont", "never",
            "mat", "nahi", "nahin",
            "मत", "नहीं", "ना", "मत करना",
            "मत खोलो", "मत भेजो"
        )

        val sendMessage = containsAny(
            message,
            "send message", "send a message",
            "message bhejo", "msg bhejo",
            "मैसेज भेजो", "संदेश भेजो"
        )

        val flashlight = containsAny(
            message,
            "flashlight", "torch",
            "टॉर्च", "फ्लैशलाइट",
            "टॉर्च जलाओ", "टॉर्च बंद"
        )

        val openApp = containsAny(
            message,
            "open", "launch",
            "खोलो", "खोल दो", "ऐप खोल",
            "चालू करो"
        )

        val screenControl = isScreenCommand(message)

        val actionCount = listOf(
            sendMessage, flashlight, openApp, screenControl
        ).count { it }

        if (isNegative && actionCount > 0) {
            return Decision(
                "CANCELLED",
                "ठीक है। आपकी अनुमति के बिना कोई कार्रवाई नहीं होगी।"
            )
        }

        if (actionCount > 1) {
            return Decision(
                "MULTIPLE_ACTIONS",
                "कृपया एक समय में एक कमांड दीजिए।"
            )
        }

        if (sendMessage) {
            return Decision(
                "SEND_MESSAGE",
                "मैसेज भेजने की कमांड समझ गया हूँ। भेजने से पहले आपकी पुष्टि जरूरी होगी।",
                true
            )
        }

        if (flashlight) {
            return Decision(
                "FLASHLIGHT",
                "टॉर्च कंट्रोल की कमांड समझ गया हूँ। Hardware Controller अभी जोड़ना बाकी है।"
            )
        }

        if (openApp) {
            return Decision(
                "OPEN_APP",
                "ऐप खोलने की कमांड समझ गया हूँ। Phone Control Engine अभी जोड़ना बाकी है।"
            )
        }

        if (screenControl) {
            return Decision(
                "SCREEN_CONTROL",
                "स्क्रीन कमांड पहचान ली गई है।",
                false
            )
        }

        if (containsAny(
                message,
                "time", "current time", "what time",
                "समय", "कितने बजे", "अभी टाइम", "वक्त"
            )
        ) {
            val time = SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            ).format(Date())

            return Decision("TIME", "अभी समय $time है।")
        }

        if (containsAny(
                message,
                "date", "today date", "today's date",
                "तारीख", "आज की तारीख", "आज की डेट"
            )
        ) {
            val date = SimpleDateFormat(
                "dd MMMM yyyy",
                Locale.getDefault()
            ).format(Date())

            return Decision("DATE", "आज की तारीख $date है।")
        }

        if (containsAny(
                message,
                "who are you", "your name",
                "what is your name",
                "tum kaun", "aap kaun",
                "तुम कौन", "आप कौन",
                "तुम्हारा नाम", "आपका नाम"
            )
        ) {
            return Decision(
                "IDENTITY",
                "मैं Idraak हूँ। आपका Offline AI Assistant। मेरा लक्ष्य बिना इंटरनेट के भी आपकी सहायता करना है।"
            )
        }

        if (containsAny(
                message,
                "hello", "hi", "hey",
                "नमस्ते", "सलाम", "आदाब", "राम राम"
            )
        ) {
            return Decision(
                "GREETING",
                "नमस्ते! मैं Idraak हूँ। बताइए, आज क्या काम करना है?"
            )
        }

        if (containsAny(
                message,
                "help", "what can you do",
                "your features", "मदद",
                "क्या कर सकते हो", "तुम्हारे फीचर"
            )
        ) {
            return Decision(
                "HELP",
                "मैं बातचीत, समय, तारीख और कुछ Offline Screen Commands पहचान सकता हूँ। Screen Control के लिए Accessibility अनुमति जरूरी है।"
            )
        }

        return Decision(
            "UNKNOWN",
            "मैंने आपकी बात समझने की कोशिश की, लेकिन अभी इसका सही उत्तर मेरे Offline Brain में उपलब्ध नहीं है। मैं अनुमान लगाकर गलत जानकारी नहीं दूँगा।"
        )
    }

    private fun isScreenCommand(message: String): Boolean {

        val exactCommands = listOf(
            "back",
            "go back",
            "press back",
            "पीछे जाओ",
            "वापस जाओ",
            "बैक दबाओ",

            "go home",
            "open home",
            "home screen",
            "होम खोलो",
            "होम पर जाओ",
            "घर जाओ",

            "scroll down",
            "नीचे स्क्रॉल करो",
            "नीचे जाओ",
            "नीचे करो",

            "scroll up",
            "ऊपर स्क्रॉल करो",
            "ऊपर जाओ",
            "ऊपर करो",

            "swipe up",
            "swipe upward",
            "ऊपर स्वाइप",
            "ऊपर स्वाइप करो",
            "स्वाइप ऊपर",
            "स्वाइप ऊपर करो",

            "swipe down",
            "swipe downward",
            "नीचे स्वाइप",
            "नीचे स्वाइप करो",
            "स्वाइप नीचे",
            "स्वाइप नीचे करो",

            "swipe left",
            "swipe to left",
            "left swipe",
            "बाएं स्वाइप",
            "बाएं स्वाइप करो",
            "बाईं तरफ स्वाइप करो",
            "स्वाइप बाएं",

            "swipe right",
            "swipe to right",
            "right swipe",
            "दाएं स्वाइप",
            "दाएं स्वाइप करो",
            "दाईं तरफ स्वाइप करो",
            "स्वाइप दाएं",

            "open notifications",
            "show notifications",
            "notification panel",
            "नोटिफिकेशन खोलो",
            "नोटिफिकेशन दिखाओ"
        )

        if (exactCommands.any { message == normalize(it) }) {
            return true
        }

        val clickPrefixes = listOf(
            "click ",
            "click on ",
            "tap ",
            "tap on ",
            "press ",
            "क्लिक करो ",
            "दबाओ "
        )

        if (clickPrefixes.any { message.startsWith(it) }) {
            return true
        }

        val clickSuffixes = listOf(
            " पर क्लिक करो",
            " को क्लिक करो",
            " दबाओ"
        )

        if (clickSuffixes.any { message.endsWith(it) }) {
            return true
        }

        return containsAny(
            message,
            "control screen",
            "screen control",
            "tap screen",
            "click screen",
            "स्क्रीन कंट्रोल",
            "स्क्रीन पर क्लिक",
            "स्क्रीन दबाओ"
        )
    }

    /**
     * MainActivity calls this method for recognized text.
     * The synchronized method protects pending confirmation
     * from overlapping voice or text commands.
     */
    @Synchronized
    fun respond(
        context: Context,
        userMessage: String
    ): String {

        val message = normalize(userMessage)

        // Handle an existing confirmation before any new command.
        val pending = pendingClick

        if (pending != null) {

            val expired =
                System.currentTimeMillis() - pending.createdAt >
                    CONFIRMATION_TIMEOUT_MS

            if (expired) {
                pendingClick = null
            } else if (isNegativeConfirmation(message)) {

                pendingClick = null

                recordExperience(
                    context = context,
                    intent = "SCREEN_CONFIRMED_CLICK",
                    status = "REJECTED",
                    steps = listOf(
                        pending.target,
                        "User rejected the click"
                    )
                )

                return "ठीक है। ${pending.target} पर क्लिक रद्द कर दिया गया है।"
            } else if (isPositiveConfirmation(message)) {

                // Clear first so the same confirmation cannot be reused.
                pendingClick = null

                val result =
                    OfflineScreenCommandHandler.executeConfirmedClick(
                        pending.target
                    )

                val status = if (result.success) {
                    "SUCCESS"
                } else {
                    "FAILED"
                }

                recordExperience(
                    context = context,
                    intent = "SCREEN_CONFIRMED_CLICK",
                    status = status,
                    steps = listOf(
                        pending.target,
                        result.message
                    )
                )

                return if (result.success) {
                    "आपकी पुष्टि मिल गई। ${pending.target} पर क्लिक कर दिया गया है।"
                } else {
                    "पुष्टि मिल गई, लेकिन क्लिक पूरा नहीं हुआ। ${result.message}"
                }
            } else {
                // Any unrelated command cancels the old pending action.
                pendingClick = null
            }
        }

        val decision = understand(userMessage)

        when (decision.intent) {

            "SCREEN_CONTROL" -> {

                val result =
                    OfflineScreenCommandHandler.execute(userMessage)

                if (result.intent == "CLICK_CONFIRMATION_REQUIRED") {

                    val target = extractClickTarget(userMessage)

                    if (target.isNullOrBlank()) {
                        recordExperience(
                            context = context,
                            intent = "SCREEN_CLICK",
                            status = "FAILED",
                            steps = listOf(userMessage, result.message)
                        )

                        return result.message
                    }

                    pendingClick = PendingClick(
                        target = target,
                        createdAt = System.currentTimeMillis()
                    )

                    recordExperience(
                        context = context,
                        intent = "SCREEN_CLICK",
                        status = "PENDING_CONFIRMATION",
                        steps = listOf(
                            userMessage,
                            "Waiting for explicit user confirmation"
                        )
                    )

                    return "यह महत्वपूर्ण कार्रवाई है: \"$target\"। क्या आप इसे क्लिक करने की अनुमति देते हैं? पुष्टि के लिए हाँ कहें, रद्द करने के लिए नहीं कहें।"
                }

                val outcome = when {
                    result.success -> "SUCCESS"
                    else -> "FAILED"
                }

                recordExperience(
                    context = context,
                    intent = "SCREEN_${result.intent}",
                    status = outcome,
                    steps = listOf(userMessage, result.message)
                )

                return result.message
            }

            "OPEN_APP",
            "SEND_MESSAGE",
            "FLASHLIGHT" -> {

                recordExperience(
                    context,
                    decision.intent,
                    "UNDERSTOOD_NOT_EXECUTED"
                )
            }
        }

        return decision.response
    }

    private fun isPositiveConfirmation(message: String): Boolean {
        val phrases = listOf(
            "yes",
            "yes please",
            "yeah",
            "yep",
            "confirm",
            "confirmed",
            "proceed",
            "do it",
            "go ahead",
            "haan",
            "han",
            "ji haan",
            "haan kar do",
            "कर दो",
            "कर दीजिए",
            "हाँ",
            "हां",
            "जी हाँ",
            "जी हां",
            "जी"
        )

        return phrases.any { message == normalize(it) }
    }

    private fun isNegativeConfirmation(message: String): Boolean {
        val phrases = listOf(
            "no",
            "no thanks",
            "nope",
            "cancel",
            "cancel it",
            "don't do it",
            "do not",
            "nahi",
            "nahin",
            "mat karo",
            "नहीं",
            "नही",
            "ना",
            "मत करो",
            "रद्द",
            "रद्द करो",
            "कैंसल"
        )

        return phrases.any { message == normalize(it) }
    }

    private fun extractClickTarget(command: String): String? {

        val value = normalize(command)

        val prefixes = listOf(
            "click on ",
            "click ",
            "tap on ",
            "tap ",
            "press ",
            "क्लिक करो ",
            "दबाओ "
        )

        for (prefix in prefixes) {
            if (value.startsWith(prefix)) {
                return value.removePrefix(prefix).trim()
            }
        }

        val suffixes = listOf(
            " पर क्लिक करो",
            " को क्लिक करो",
            " दबाओ"
        )

        for (suffix in suffixes) {
            if (value.endsWith(suffix)) {
                return value.removeSuffix(suffix).trim()
            }
        }

        return null
    }

    fun recordExperience(
        context: Context,
        intent: String,
        status: String,
        steps: List<String> = emptyList()
    ) {
        if (intent.isBlank()) return

        saveLegacyExperience(
            context,
            intent,
            status,
            steps
        )

        val normalizedStatus = status
            .trim()
            .uppercase(Locale.ROOT)

        val memoryStatus = when {
            normalizedStatus.contains("NOT_EXECUTED") ||
            normalizedStatus.contains("PENDING") ||
            normalizedStatus.contains("UNVERIFIED") ||
            normalizedStatus == "UNKNOWN" ||
            normalizedStatus == "UNDERSTOOD" ->
                LocalMemoryStore.Status.PENDING

            normalizedStatus == "VERIFIED" ->
                LocalMemoryStore.Status.VERIFIED

            normalizedStatus == "SUCCESS" ->
                LocalMemoryStore.Status.SUCCESS

            normalizedStatus == "FAILED" ->
                LocalMemoryStore.Status.FAILED

            normalizedStatus == "REJECTED" ->
                LocalMemoryStore.Status.REJECTED

            else ->
                LocalMemoryStore.Status.PENDING
        }

        try {
            LocalMemoryStore.saveExperience(
                context = context,
                commandType = intent,
                outcome = memoryStatus,
                lesson = "Recorded outcome: ${status.take(60)}"
            )
        } catch (_: Exception) {
            // Memory failure must not crash the app.
        }
    }

    private fun saveLegacyExperience(
        context: Context,
        intent: String,
        status: String,
        steps: List<String>
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
            // Keep the assistant running.
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
