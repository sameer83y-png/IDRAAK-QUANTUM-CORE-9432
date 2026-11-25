
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

    private var pendingClick: PendingClick? = null

    data class Decision(
        val intent: String,
        val response: String,
        val requiresConfirmation: Boolean = false
    )

    fun understand(userMessage: String): Decision {
        val message = normalize(userMessage)

        if (message.isBlank()) {
            return Decision("EMPTY", "براہِ کرم بتائیں، آپ کیا کرنا چاہتے ہیں؟")
        }

        val screenControl = isScreenCommand(message)
        val openApp = !screenControl && isOpenAppCommand(message)

        val isNegative = containsAny(
            message,
            "don't", "do not", "dont", "never",
            "mat", "mat karo", "nahi", "nahin",
            "not now",
            "मत", "नहीं", "नही", "ना",
            "मत करना", "मत खोलो", "मत भेजो",
            "نہیں", "مت کرو", "مت کھولو", "مت بھیجو"
        )

        val sendMessage = containsAny(
            message,
            "send message", "send a message",
            "message bhejo", "msg bhejo",
            "message send karo", "msg send karo",
            "मैसेज भेजो", "संदेश भेजो",
            "پیغام بھیجو", "میسج بھیجو",
            "میسج بھیج دو"
        )

        val flashlight = containsAny(
            message,
            "flashlight", "torch",
            "turn on torch", "turn off torch",
            "torch on", "torch off",
            "torch chalu karo", "torch band karo",
            "टॉर्च", "फ्लैशलाइट",
            "टॉर्च जलाओ", "टॉर्च बंद",
            "टॉर्च चालू करो", "टॉर्च बुझाओ",
            "ٹارچ جلاؤ", "ٹارچ بند کرو", "ٹارچ آن کرو", "ٹارچ آف کرو",
            "الفلاش", "فلاش", "مصباح", "المصباح", "الكشاف",
            "شغل الفلاش", "افتح الفلاش", "أطفئ الفلاش", "اطفئ الفلاش", "أغلق الفلاش"
        )

        val actionCount = listOf(
            sendMessage, flashlight, openApp, screenControl
        ).count { it }

        if (isNegative && actionCount > 0) {
            return Decision(
                "CANCELLED",
                "ٹھیک ہے، آپ کی اجازت کے بغیر کوئی کارروائی نہیں ہوگی۔"
            )
        }

        if (actionCount > 1) {
            return Decision(
                "MULTIPLE_ACTIONS",
                "براہِ کرم ایک وقت میں ایک کام بتائیے۔"
            )
        }

        if (sendMessage) {
            return Decision(
                "SEND_MESSAGE",
                "پیغام بھیجنے کی بات سمجھ گیا ہوں۔ بھیجنے سے پہلے آپ کی تصدیق ضروری ہوگی۔",
                true
            )
        }

        if (flashlight) {
            return Decision(
                "FLASHLIGHT",
                "ٹارچ کے کنٹرول کی بات سمجھ گیا ہوں۔"
            )
        }

        if (openApp) {
            return Decision(
                "OPEN_APP",
                "ایپ کھولنے کی بات سمجھ گیا ہوں۔"
            )
        }

        if (screenControl) {
            return Decision(
                "SCREEN_CONTROL",
                "اسکرین کا حکم سمجھ گیا ہوں۔"
            )
        }

        if (containsAny(
                message,
                "time", "current time", "what time",
                "समय", "कितने बजे", "अभी टाइम", "वक्त",
                "وقت کیا ہے", "ابھی کیا وقت ہے"
            )
        ) {
            val time = SimpleDateFormat(
                "hh:mm a",
                Locale("ur", "PK")
            ).format(Date())

            return Decision("TIME", "ابھی وقت $time ہے۔")
        }

        if (containsAny(
                message,
                "date", "today date", "today's date",
                "तारीख", "आज की तारीख", "आज की डेट",
                "آج کی تاریخ", "تاریخ کیا ہے"
            )
        ) {
            val date = SimpleDateFormat(
                "dd MMMM yyyy",
                Locale("ur", "PK")
            ).format(Date())

            return Decision("DATE", "آج کی تاریخ $date ہے۔")
        }

        if (containsAny(
                message,
                "who are you", "your name",
                "what is your name",
                "tum kaun", "aap kaun",
                "tum kon ho", "aap kon hain",
                "तुम कौन", "आप कौन",
                "तुम्हारा नाम", "आपका नाम",
                "آپ کون ہیں", "تم کون ہو"
            )
        ) {
            return Decision(
                "IDENTITY",
                "میں ادراک ہوں۔ آپ کا آف لائن معاون۔ میرا مقصد انٹرنیٹ کے بغیر بھی آپ کی مدد کرنا ہے۔"
            )
        }

        if (containsAny(
                message,
                "hello", "hi", "hey",
                "namaste", "salam", "adaab",
                "नमस्ते", "सलाम", "आदाब", "राम राम",
                "السلام علیکم", "سلام"
            )
        ) {
            return Decision(
                "GREETING",
                "السلام علیکم! میں ادراک ہوں۔ بتائیے، آج کیا کام کرنا ہے؟"
            )
        }

        if (containsAny(
                message,
                "help", "what can you do",
                "your features", "madad",
                "मदद", "क्या कर सकते हो", "तुम्हारे फीचर",
                "آپ کیا کر سکتے ہیں", "مدد کرو"
            )
        ) {
            return Decision(
                "HELP",
                "میں نصب شدہ ایپس کھول سکتا ہوں، کال اور پیغام کا محفوظ عمل شروع کر سکتا ہوں، کیمرہ، ٹارچ، آواز، روشنی، اسکرین، نوٹیفکیشن اور فوری ترتیبات کنٹرول کر سکتا ہوں۔ وقت اور تاریخ بتا سکتا ہوں اور آپ کے کہنے پر معلومات مقامی یادداشت میں محفوظ کر سکتا ہوں۔ کچھ کاموں کے لیے فون کی اجازت یا آپ کی تصدیق ضروری ہوگی۔ نئی ترکیب سکھانے کے لیے کہیں: جب میں کہوں مخصوص جملہ، تو یوٹیوب کھولو۔ یہ ترکیب تصدیق تک استعمال نہیں ہوگی۔"
            )
        }

        if (containsAny(message, "آپ کیسے ہیں", "کیا حال ہے", "کیسے ہو", "سب ٹھیک ہے")) {
            return Decision(
                "SMALL_TALK",
                "میں بالکل ٹھیک ہوں، شکریہ۔ آپ بتائیں، آپ کا دن کیسا گزر رہا ہے؟"
            )
        }

        if (containsAny(message, "شکریہ", "بہت شکریہ", "مہربانی", "آپ کا شکریہ")) {
            return Decision(
                "THANKS",
                "آپ کا خیر مقدم ہے۔ جب بھی ضرورت ہو، مجھے بتائیے۔"
            )
        }

        if (containsAny(message, "کیا کر رہے ہو", "کیا کر رہے ہیں", "کیا کر رہے ہو ادراک")) {
            return Decision(
                "SMALL_TALK",
                "میں آپ کی بات سن رہا ہوں اور آپ کی مدد کے لیے تیار ہوں۔ بتائیے، کیا بات کرنی ہے؟"
            )
        }

        if (containsAny(message, "ایک لطیفہ سناؤ", "کوئی لطیفہ سناؤ", "مجھے ہنساؤ")) {
            return Decision(
                "JOKE",
                "ایک چھوٹا سا لطیفہ: کمپیوٹر نے چائے کیوں نہیں پی؟ کیونکہ اس کا کپ پہلے ہی بھر چکا تھا!"
            )
        }

        if (containsAny(
                message,
                "چارجر ہٹا دو", "چارجر نکال دو", "چارجر الگ کر دو",
                "چارجر کھول کر رکھ دو", "چارجر کھول کے رکھ دو",
                "چارجر لگا دو", "چارجر جوڑ دو"
            )
        ) {
            return Decision(
                "PHYSICAL_DEVICE_ACTION",
                "میں فون سے چارجر جسمانی طور پر نہیں نکال سکتا اور نہ ہی لگا سکتا ہوں۔ یہ کام آپ کو خود کرنا ہوگا۔"
            )
        }

        return Decision(
            "UNKNOWN",
            "میں آپ کی بات سمجھنے کی کوشش کر رہا ہوں۔ ذرا واضح کریں کہ آپ کیا کرنا چاہتے ہیں؟ نیا جملہ سکھانے کے لیے کہیں: جب میں کہوں مخصوص جملہ، تو یوٹیوب کھولو۔"
        )
    }

    private fun isOpenAppCommand(message: String): Boolean {
        // Ignore a small set of conversational lead-ins before checking
        // the actual app-opening instruction. Keep this conservative so
        // ordinary conversation mentioning an app is not treated as a command.
        var command = message
        val conversationalPrefixes = listOf(
            "bhai ", "yaar ", "please ", "zara ",
            "भाई ", "यार ", "कृपया ", "जरा ",
            "بھائی ", "یار ", "ذرا "
        )
        for (prefix in conversationalPrefixes.sortedByDescending { it.length }) {
            if (command.startsWith(prefix)) {
                command = command.removePrefix(prefix).trim()
                break
            }
        }

        val prefixes = listOf(
            "please open the ", "could you open ",
            "could you please open ", "open up ",
            "open ", "open the ", "open app ",
            "please launch ", "launch ", "start ", "run ",
            "app kholo ", "app khol do ",
            "zara kholo ", "zara khol do ",
            "khol do ", "kholo ", "kholiye ",
            "chalu karo ",
            "ऐप खोलो ", "ऐप खोल दो ",
            "जरा खोलो ", "जरा खोल दो ",
            "खोलो ", "खोल दो ", "खोलिए ",
            "चालू करो ",
            "ایپ کھولو ", "ایپ کھول دو ",
            "ذرا کھولو ", "ذرا کھول دو ",
            "کھولو ", "کھول دو ", "کھولیں "
        )

        val suffixes = listOf(
            " open", " open karo", " open kar do",
            " please open", " please kholo",
            " kholo", " khol do", " kholiye",
            " chalao", " chala do", " start kar do",
            " chalu kar do", " chalao please", " on kar do", " on karo",
            " open karo", " open kar do", " launch karo", " start karo",
            " खोलो", " खोल दो", " खोल देना", " खोल दीजिए", " खोलिए",
            " चला दो", " चला देना", " चालू कर दो", " चालू कर देना", " चालू करो", " ऑन कर दो",
            " کھولو", " کھول دو", " کھول دینا", " کھول دیجیے", " کھولیں",
            " چلا دو", " چلا دینا", " آن کر دو", " شروع کرو"
        )

        return prefixes.any { command.startsWith(it) } ||
            suffixes.any { command.endsWith(it) }
    }

    private fun isScreenCommand(message: String): Boolean {
        val exactCommands = listOf(
            "back", "go back", "press back",
            "पीछे जाओ", "वापस जाओ", "बैक दबाओ",
            "واپس جاؤ", "پیچھے جاؤ",
            "ارجع", "ارجع للخلف", "عد للخلف", "رجوع",

            "go home", "open home", "home screen",
            "होम खोलो", "होम पर जाओ", "घर जाओ",
            "ہوم پر جاؤ", "گھر جاؤ",
            "افتح الصفحة الرئيسية", "اذهب إلى الصفحة الرئيسية", "الشاشة الرئيسية",

            "scroll down", "नीचे स्क्रॉल करो",
            "नीचे जाओ", "नीचे करो",
            "نیچے اسکرول کرو", "نیچے جاؤ",

            "scroll up", "ऊपर स्क्रॉल करो",
            "ऊपर जाओ", "ऊपर करो",
            "اوپر اسکرول کرو", "اوپر جاؤ",

            "swipe up", "swipe upward",
            "ऊपर स्वाइप", "ऊपर स्वाइप करो",
            "स्वाइप ऊपर", "स्वाइप ऊपर करो",
            "اوپر سوائپ کرو",

            "swipe down", "swipe downward",
            "नीचे स्वाइप", "नीचे स्वाइप करो",
            "स्वाइप नीचे", "स्वाइप नीचे करो",
            "نیچے سوائپ کرو",

            "swipe left", "swipe to left", "left swipe",
            "बाएं स्वाइप", "बाएं स्वाइप करो",
            "बाईं तरफ स्वाइप करो", "स्वाइप बाएं",
            "بائیں سوائپ کرو",

            "swipe right", "swipe to right", "right swipe",
            "दाएं स्वाइप", "दाएं स्वाइप करो",
            "दाईं तरफ स्वाइप करो", "स्वाइप दाएं",
            "دائیں سوائپ کرو",

            "open notifications", "show notifications",
            "notification panel",
            "नोटिफिकेशन खोलो", "नोटिफिकेशन दिखाओ",
            "نوٹیفکیشن کھولو", "نوٹیفکیشن دکھاؤ",
            "افتح الإشعارات", "اعرض الإشعارات",
            "lock screen", "lock the screen",
            "स्क्रीन लॉक करो", "फोन लॉक करो", "मोबाइल लॉक करो",
            "اسکرین لاک کرو", "فون لاک کرو",
            "اقفل الشاشة", "قفل الشاشة",
            "open quick settings", "show quick settings", "quick settings panel",
            "क्विक सेटिंग खोलो", "क्विक सेटिंग्स खोलो",
            "فوری سیٹنگز کھولو", "کوئیک سیٹنگز دکھاؤ",
            "افتح الإعدادات السريعة", "اعرض الإعدادات السريعة",
            "open recent apps", "show recent apps", "recent apps",
            "हाल के ऐप खोलो", "हालिया ऐप दिखाओ",
            "حالیہ ایپس کھولو", "حالیہ ایپس دکھاؤ",
            "open power menu", "show power menu", "power menu",
            "पावर मेन्यू खोलो", "पावर मेनू दिखाओ",
            "پاور مینو کھولو", "پاور مینو دکھاؤ"
        )

        if (exactCommands.any { message == normalize(it) }) {
            return true
        }

        val clickPrefixes = listOf(
            "click ", "click on ",
            "tap ", "tap on ",
            "press ",
            "क्लिक करो ", "दबाओ ",
            "کلک کرو ", "کلک کریں ", "دباؤ ", "دبائیں ",
            "انقر على ", "اضغط على ", "انقر ", "اضغط ", "المس "
        )

        if (clickPrefixes.any { message.startsWith(it) }) {
            return true
        }

        val clickSuffixes = listOf(
            " पर क्लिक करो",
            " को क्लिक करो",
            " दबाओ",
            " پر کلک کرو",
            " کو کلک کرو"
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
            "स्क्रीन दबाओ",
            "اسکرین کنٹرول"
        )
    }

    @Synchronized
    fun respond(
        context: Context,
        userMessage: String
    ): String {
        val message = normalize(userMessage)
        val pending = pendingClick

        if (pending != null) {
            val expired =
                System.currentTimeMillis() - pending.createdAt >
                    CONFIRMATION_TIMEOUT_MS

            if (expired) {
                pendingClick = null
                return "تصدیق کا وقت ختم ہو گیا ہے۔ براہِ کرم دوبارہ بتائیے۔"
            }

            if (isNegativeConfirmation(message)) {
                pendingClick = null

                recordExperience(
                    context,
                    "SCREEN_CONFIRMED_CLICK",
                    "REJECTED",
                    listOf(pending.target, "User cancelled")
                )

                return "ٹھیک ہے، ${pending.target} پر کلک منسوخ کر دیا گیا ہے۔"
            }

            if (isPositiveConfirmation(message)) {
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
                    context,
                    "SCREEN_CONFIRMED_CLICK",
                    status,
                    listOf(pending.target, result.message)
                )

                return if (result.success) {
                    "آپ کی تصدیق مل گئی۔ ${pending.target} پر کلک کر دیا گیا ہے۔"
                } else {
                    "تصدیق مل گئی، لیکن کلک مکمل نہیں ہو سکا۔ ${result.message}"
                }
            }

            pendingClick = null
        }

        LearnedCommandStore.handleTeaching(
            context.applicationContext,
            userMessage
        )?.let { teachingResponse ->
            return teachingResponse
        }

        // Explicit voice memory capture: new facts remain PENDING until verified.
        MemoryCommandHandler.handle(
            context.applicationContext,
            userMessage
        )?.let { memoryResponse ->
            return memoryResponse
        }

        // Handle explicit user corrections before normal command detection.
        // Corrections are saved as PENDING and require user verification.
        MemoryFeedbackHandler.handle(
            context.applicationContext,
            userMessage
        )?.let { correctionResponse ->
            return correctionResponse
        }

        val decision = understand(userMessage)

        // Use only explicitly verified local memory as a fallback.
        // Normal commands and known OfflineBrain responses keep priority.
        if (decision.intent == "UNKNOWN") {
            val memoryAnswer = try {
                VerifiedMemoryResponder.answer(
                    context.applicationContext,
                    userMessage
                )
            } catch (_: Exception) {
                null
            }

            if (!memoryAnswer.isNullOrBlank()) {
                return memoryAnswer
            }

            // Keep unclear requests as unverified learning examples.
            // They are never promoted to executable rules without correction.
            recordExperience(
                context = context,
                intent = "UNRECOGNIZED_UTTERANCE",
                status = "NEEDS_CLARIFICATION",
                steps = listOf(userMessage)
            )
        }

        when (decision.intent) {
            "OPEN_APP" -> {
                val result = OfflinePhoneCommandHandler.execute(
                    context.applicationContext,
                    userMessage
                )

                if (result == null) {
                    val response =
                        "حکم سمجھ لیا، لیکن ایپ کا نام واضح نہیں ہوا۔ براہِ کرم یوٹیوب، واٹس ایپ، کروم یا کسی دوسری نصب شدہ ایپ کا نام بتائیں۔"

                    recordExperience(
                        context,
                        "OPEN_APP",
                        "FAILED",
                        listOf(userMessage, response)
                    )

                    return response
                }

                val status = if (result.success) {
                    "SUCCESS"
                } else {
                    "FAILED"
                }

                recordExperience(
                    context,
                    "OPEN_APP",
                    status,
                    listOf(userMessage, result.appName, result.message)
                )

                return result.message
            }

            "SCREEN_CONTROL" -> {
                val canonicalCommand = canonicalScreenCommand(userMessage)
                val result =
                    OfflineScreenCommandHandler.execute(canonicalCommand)

                if (result.intent == "CLICK_CONFIRMATION_REQUIRED") {
                    val target = extractClickTarget(canonicalCommand)

                    if (target.isNullOrBlank()) {
                        recordExperience(
                            context,
                            "SCREEN_CLICK",
                            "FAILED",
                            listOf(userMessage, result.message)
                        )
                        return result.message
                    }

                    pendingClick = PendingClick(
                        target = target,
                        createdAt = System.currentTimeMillis()
                    )

                    recordExperience(
                        context,
                        "SCREEN_CLICK",
                        "PENDING_CONFIRMATION",
                        listOf(userMessage, "Waiting for confirmation")
                    )

                    return "یہ اہم کارروائی ہے: \"$target\"۔ کیا آپ کلک کی اجازت دیتے ہیں؟ اجازت کے لیے ہاں اور منسوخ کرنے کے لیے نہیں کہیں۔"
                }

                val outcome = if (result.success) {
                    "SUCCESS"
                } else {
                    "FAILED"
                }

                recordExperience(
                    context,
                    "SCREEN_${result.intent}",
                    outcome,
                    listOf(userMessage, result.message)
                )

                return result.message
            }

            "SEND_MESSAGE",
            "FLASHLIGHT" -> {
                recordExperience(
                    context,
                    decision.intent,
                    "UNDERSTOOD_NOT_EXECUTED",
                    listOf(userMessage)
                )
            }
        }

        return decision.response
    }

    private fun canonicalScreenCommand(command: String): String {
        val value = normalize(command)

        val aliases = mapOf(
            "واپس جاؤ" to "go back",
            "پیچھے جاؤ" to "go back",
            "بیک دباؤ" to "press back",
            "ہوم پر جاؤ" to "go home",
            "گھر جاؤ" to "go home",
            "نیچے اسکرول کرو" to "scroll down",
            "نیچے جاؤ" to "scroll down",
            "اوپر اسکرول کرو" to "scroll up",
            "اوپر جاؤ" to "scroll up",
            "اوپر سوائپ کرو" to "swipe up",
            "نیچے سوائپ کرو" to "swipe down",
            "بائیں سوائپ کرو" to "swipe left",
            "دائیں سوائپ کرو" to "swipe right",
            "نوٹیفکیشن کھولو" to "open notifications",
            "نوٹیفکیشن دکھاؤ" to "show notifications",
            "اسکرین لاک کرو" to "lock screen",
            "فون لاک کرو" to "lock screen",
            "فوری سیٹنگز کھولو" to "open quick settings",
            "کوئیک سیٹنگز دکھاؤ" to "show quick settings",
            "حالیہ ایپس کھولو" to "open recent apps",
            "حالیہ ایپس دکھاؤ" to "show recent apps",
            "پاور مینو کھولو" to "open power menu",
            "پاور مینو دکھاؤ" to "show power menu"
        )

        aliases[value]?.let { return it }

        val clickPrefixes = listOf(
            "کلک کرو ", "دباؤ ", "کلک کریں ", "دبائیں ",
            "انقر على ", "اضغط على ", "انقر ", "اضغط ", "المس "
        )

        for (prefix in clickPrefixes) {
            if (value.startsWith(prefix)) {
                val target = value.removePrefix(prefix).trim()
                return "click $target"
            }
        }

        val clickSuffixes = listOf(
            " پر کلک کرو",
            " کو کلک کرو"
        )

        for (suffix in clickSuffixes) {
            if (value.endsWith(suffix)) {
                val target = value.removeSuffix(suffix).trim()
                return "click $target"
            }
        }

        return command
    }

    private fun isPositiveConfirmation(message: String): Boolean {
        return listOf(
            "yes", "yes please", "yeah", "yep",
            "confirm", "confirmed", "proceed",
            "نعم", "أجل", "موافق", "نفذ",
            "do it", "go ahead",
            "haan", "han", "ji haan",
            "haan kar do",
            "kar do", "kar dijiye",
            "कर दो", "कर दीजिए",
            "हाँ", "हां", "जी हाँ", "जी हां", "जी",
            "हाँ कर दो", "हां कर दो", "जी कर दीजिए",
            "جی ہاں", "ہاں", "کر دو", "جی کر دیں", "ہاں کر دیں", "بالکل"
        ).any { message == normalize(it) }
    }

    private fun isNegativeConfirmation(message: String): Boolean {
        return listOf(
            "no", "no thanks", "nope",
            "cancel", "cancel it",
            "don't do it", "do not",
            "nahi", "nahin", "mat karo",
            "नहीं", "नही", "ना", "मत करो",
            "रद्द", "रद्द करो", "कैंसल",
            "نہیں", "مت کرو", "منسوخ کرو", "لا", "إلغاء", "ألغ", "تراجع"
        ).any { message == normalize(it) }
    }

    private fun extractClickTarget(command: String): String? {
        val value = normalize(command)

        val prefixes = listOf(
            "click on ", "click ",
            "tap on ", "tap ",
            "press ",
            "क्लिक करो ", "दबाओ ",
            "کلک کرو ", "کلک کریں ", "دباؤ ", "دبائیں ",
            "انقر على ", "اضغط على ", "انقر ", "اضغط ", "المس "
        )

        for (prefix in prefixes) {
            if (value.startsWith(prefix)) {
                return value.removePrefix(prefix).trim()
            }
        }

        val suffixes = listOf(
            " पर क्लिक करो",
            " को क्लिक करो",
            " दबाओ",
            " پر کلک کرو",
            " کو کلک کرو",
            " دباؤ",
            " دبائیں"
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

        saveLegacyExperience(context, intent, status)

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
        status: String
    ) {
        try {
            val memories = readMemories(context)

            val record = JSONObject().apply {
                put("intent", intent)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
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
                val records = JSONArray(saved)
                var removedPrivateDetails = false
                for (index in 0 until records.length()) {
                    val record = records.optJSONObject(index) ?: continue
                    if (record.has("steps")) {
                        record.remove("steps")
                        removedPrivateDetails = true
                    }
                }
                if (removedPrivateDetails) {
                    context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(MEMORY_KEY, records.toString())
                        .apply()
                }
                records
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
