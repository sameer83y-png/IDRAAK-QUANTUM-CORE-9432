package com.idraak.quantum

import java.util.Locale

/**
 * Converts a bounded set of common multilingual command phrases into the
 * Urdu command vocabulary understood by Idraak's offline handlers.
 * App names, contact names and search terms are preserved.
 */
object MultilingualCommandNormalizer {

    /**
     * Detects explicit multilingual refusals before any phone or screen action is routed.
     * This deliberately recognizes only clear action-negation patterns.
     */
    fun isExplicitRefusal(input: String): Boolean {
        val value = input.trim()
            .lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")
        if (value.isBlank()) return false

        val patterns = listOf(
            Regex("""(?:^|\s)(?:don't|dont|do not|never)\s+(?:call|open|launch|send|search|click|tap|press|delete|remove|buy|purchase|pay|transfer|share|post|install|enable|disable|turn|switch)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:मत|नहीं|ना)\s+(?:कॉल|फोन|खोल|भेज|तलाश|दबाओ|क्लिक|हटाओ|मिटाओ|खरीद|भुगतान|शेयर|पोस्ट|इंस्टॉल|चालू|बंद)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:mat|nahi|nahin|na)\s+(?:call|khol|bhej|talaash|search|dabao|click|hatao|mitao|khareed|share|post|install|chalu|band)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:مت|نہ|ہرگز)\s+(?:کال|فون|کھول|بھیج|تلاش|دباؤ|کلک|ہٹاؤ|حذف|خرید|ادا|شیئر|پوسٹ|انسٹال|چالو|بند)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:لا)\s+(?:تتصل|تفتح|ترسل|تبحث|تضغط|تحذف|تشتري|تدفع|تحول|تنشر|تثبت|تشغل|تغلق)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:call|open|send|click|tap|press|delete|remove|buy|pay|share|post|install)\s+(?:[\p{L}\p{M}\p{N}_-]+\s+){0,4}(?:mat|nahi|nahin|don't|do not)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:کال|فون|کھولو|بھیجو|دباؤ|کلک|حذف|خرید|ادا|شیئر|پوسٹ|انسٹال)\s+(?:[\p{L}\p{M}\p{N}_-]+\s+){0,4}(?:مت|نہ|نہیں)(?=\s|$)"""),
            Regex("""(?:^|\s)(?:कॉल|फोन|खोलो|भेजो|दबाओ|क्लिक|हटाओ|खरीदो|भुगतान|शेयर|पोस्ट|इंस्टॉल)\s+(?:[\p{L}\p{M}\p{N}_-]+\s+){0,4}(?:मत|नहीं|ना)(?=\s|$)""")
        )
        return patterns.any { it.containsMatchIn(value) }
    }

    fun normalize(input: String): String {
        val original = input.trim()
        var value = original.lowercase(Locale.ROOT)
            .replace('’', '\'')
            .replace(Regex("\\s+"), " ")
        if (value.isBlank()) return input

        // Handle combined YouTube-open + search requests before generic app opening.
        // The resulting Urdu phrase is understood by WebNavigationCommandHandler.
        val youtubeQuery = extractYoutubeSearchQuery(original)
        if (!youtubeQuery.isNullOrBlank()) {
            return "یوٹیوب پر $youtubeQuery تلاش کرو"
        }

        // Preserve clear refusals: they must never become executable requests.
        val refusalPrefixes = listOf(
            "don't open ", "do not open ", "dont open ", "never open ",
            "मत खोलो ", "मत खोलना ", "मत खोलिए ",
            "mat kholo ", "mat kholna ",
            "ne pas ouvrir ", "nicht öffnen "
        )
        for (prefix in refusalPrefixes.sortedByDescending { it.length }) {
            if (value.startsWith(prefix)) {
                return "مت کھولو " + value.removePrefix(prefix).trim()
            }
        }

        val torchOff = listOf(
            "turn off the flashlight", "turn off flashlight", "switch off the flashlight",
            "switch off flashlight", "turn off torch", "switch off torch",
            "flashlight off", "torch off", "disable flashlight",
            "टॉर्च बंद करो", "टॉर्च बुझाओ", "फ्लैशलाइट बंद करो",
            "torch band karo", "torch bujha do", "flashlight band karo",
            "ٹارچ بند کرو", "ٹارچ بجھا دو"
        )
        if (torchOff.any { value == it || value.endsWith(" $it") }) {
            return "ٹارچ بند کرو"
        }

        val torchOn = listOf(
            "turn on the flashlight", "turn on flashlight", "switch on the flashlight",
            "switch on flashlight", "turn on torch", "switch on torch",
            "flashlight on", "torch on", "enable flashlight",
            "टॉर्च जलाओ", "टॉर्च चालू करो", "फ्लैशलाइट चालू करो",
            "torch chalu karo", "torch jalao", "flashlight chalu karo",
            "ٹارچ جلاؤ", "ٹارچ چالو کرو"
        )
        if (torchOn.any { value == it || value.endsWith(" $it") }) {
            return "ٹارچ جلاؤ"
        }

        val cameraOpen = listOf(
            "open camera", "open the camera", "launch camera", "start camera",
            "take me to camera", "कैमरा खोलो", "कैमरा खोल दो", "कैमरा चालू करो",
            "camera kholo", "camera khol do", "camera chalu karo",
            "کیمرہ کھولو", "کیمرہ کھول دو"
        )
        if (cameraOpen.any { value == it }) return "کیمرہ کھولو"

        val timeQuestions = listOf(
            "what time is it", "what's the time", "tell me the time",
            "current time", "time please", "¿qué hora es?", "wie spät ist es",
            "अभी कितने बजे हैं", "समय बताओ", "अभी टाइम बताओ",
            "abhi kitne baje hain", "samay batao",
            "وقت بتاؤ", "ابھی کیا وقت ہے"
        )
        if (timeQuestions.any { value == it }) return "وقت کیا ہے"

        val dateQuestions = listOf(
            "what is today's date", "what's today's date", "tell me today's date",
            "what date is it", "¿qué fecha es hoy?", "welches datum ist heute",
            "आज की तारीख बताओ", "आज तारीख क्या है", "aaj ki tareekh batao",
            "آج کی تاریخ بتاؤ", "آج کی تاریخ کیا ہے"
        )
        if (dateQuestions.any { value == it }) return "آج کی تاریخ کیا ہے"

        // App-opening phrases are normalized only when they are not web URLs.
        val openPrefixes = listOf(
            "could you please open ", "please open the ", "please open ",
            "could you open ", "open the app ", "open app ", "open the ",
            "launch the app ", "launch ", "start the app ", "start app ",
            "start ", "run the app ", "run ",
            "कृपया खोलो ", "ऐप खोलो ", "ऐप खोल दो ", "खोलो ", "खोल दो ",
            "जरा खोलो ", "जरा खोल दो ",
            "please kholo ", "app kholo ", "app khol do ", "khol do ",
            "kholna ", "zara kholo ", "zara khol do ", "chalu karo ",
            "افتح ", "افتح التطبيق ",
            "ouvrir ", "lance "
        )
        val prefix = openPrefixes.sortedByDescending { it.length }
            .firstOrNull { value.startsWith(it) }
        if (prefix != null) {
            val target = original.drop(prefix.length).trim()
            if (target.isNotBlank() && !target.contains("://") &&
                !target.matches(Regex("(?i).*[a-z0-9-]+\\.[a-z]{2,}.*"))) {
                return "کھولو $target"
            }
        }

        return original
    }

    private fun extractYoutubeSearchQuery(input: String): String? {
        // Speech engines may append sentence punctuation; ignore it for intent matching.
        val phrase = input.trim().trimEnd('.', '؟', '?', '!', '۔', '،', ',').trim()
        val patterns = listOf(
            Regex("""^open\s+(?:the\s+)?youtube\s+(?:and|then)\s+(?:search|find)\s+(?:for\s+)?(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+(.+?)\s+on\s+youtube$""", RegexOption.IGNORE_CASE),
            Regex("""^find\s+(.+?)\s+on\s+youtube$""", RegexOption.IGNORE_CASE),
            Regex("""^youtube\s+khol(?:o|na)\s+aur\s+(.+?)\s+(?:search|dhoondho|dhundo|talaash)\s+(?:karo)?$""", RegexOption.IGNORE_CASE),
            Regex("""^youtube\s+(?:par|pe)\s+(.+?)\s+(?:search|dhoondho|dhundo)\s+(?:karo|kar\s+do)$""", RegexOption.IGNORE_CASE),
            Regex("""^यूट्यूब\s+खोलो\s+और\s+(.+?)\s+(?:खोजो|ढूंढो|सर्च\s+करो)$"""),
            Regex("""^यूट्यूब\s+खोलकर\s+(.+?)\s+(?:खोजो|खोजिए|ढूंढो|ढूंढिए|सर्च\s+करो)$"""),
            Regex("""^यूट्यूब\s+में\s+(.+?)\s+(?:खोजो|ढूंढो|सर्च\s+करो)$"""),
            Regex("""^یوٹیوب\s+کھولو\s+اور\s+(.+?)\s+(?:تلاش\s+کرو|تلاش\s+کریں|ڈھونڈو|ڈھونڈیں|سرچ\s+کرو|سرچ\s+کریں)$"""),
            Regex("""^یوٹیوب[،,]?\s+کھول\s+دو\s+اور[،,]?\s+(.+?)\s+(?:تلاش\s+کرو|تلاش\s+کریں|ڈھونڈو|ڈھونڈیں|سرچ\s+کرو|سرچ\s+کریں|سرچ\s+لگاؤ|دکھاؤ)$"""),
            Regex("""^یوٹیوب[،,]?\s+کھولیں\s+(?:اور|پھر)[،,]?\s+(.+?)\s+(?:تلاش\s+کرو|تلاش\s+کریں|ڈھونڈو|ڈھونڈیں|سرچ\s+کرو|سرچ\s+کریں|سرچ\s+لگائیں|دکھائیں)$"""),
            Regex("""^یوٹیوب\s+کھول\s+کے\s+(.+?)\s+(?:تلاش\s+کرو|تلاش\s+کریں|ڈھونڈو|ڈھونڈیں|سرچ\s+کرو|سرچ\s+کریں|دکھاؤ)$"""),
            Regex("""^یوٹیوب\s+کھول\s+کر\s+(.+?)\s+(?:تلاش\s+کرو|تلاش\s+کریں|ڈھونڈو|ڈھونڈیں|سرچ\s+کرو|سرچ\s+کریں|دکھاؤ|دکھائیں)$"""),
            Regex("""^یوٹیوب\s+(?:پر|میں|پہ)\s+(.+?)\s+(?:تلاش\s+کرو|تلاش\s+کریں|ڈھونڈو|ڈھونڈیں|سرچ\s+کرو|سرچ\s+کریں|دکھاؤ|دکھائیں|ویڈیو\s+چلاؤ)$"""),
            Regex("""^افتح\s+(?:اليوتيوب|يوتيوب)\s+(?:و|ثم)\s+(?:ابحث\s+عن|دور\s+على)\s+(.+)$"""),
            Regex("""^(?:ابحث\s+عن|دور\s+على)\s+(.+?)\s+في\s+(?:اليوتيوب|يوتيوب)$""")
        )

        for (pattern in patterns) {
            val match = pattern.matchEntire(phrase) ?: continue
            val query = match.groupValues.getOrNull(1)
                ?.trim()
                ?.trimEnd('.', '؟', '?', '!', '۔')
                ?.trim()
            if (query.isNotNullOrBlank()) return query
        }
        return null
    }

    private fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()
}
