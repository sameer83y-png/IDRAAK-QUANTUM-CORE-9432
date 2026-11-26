
package com.idraak.quantum.engine

import java.util.Locale

/**
 * Idraak Offline Screen Command Handler
 * Converts Hindi and English commands into screen actions.
 */
object OfflineScreenCommandHandler {

    data class Result(
        val success: Boolean,
        val message: String,
        val intent: String
    )

    fun execute(userCommand: String): Result {
        val command = normalize(userCommand)

        if (command.isBlank()) {
            return Result(
                false,
                "براہِ کرم اسکرین کا حکم بتائیں۔",
                "EMPTY"
            )
        }

        // Read only the currently visible accessibility text; nothing is stored or transmitted.
        if (matches(
                command,
                "read screen", "read the screen", "what is on screen", "what's on the screen",
                "screen par kya hai", "screen par kya likha hai",
                "اسکرین پڑھو", "اسکرین پر کیا ہے", "اسکرین پر کیا لکھا ہے",
                "یہاں کیا لکھا ہے", "پڑھ کر سناؤ",
                "اسکرین پر موجود عبارت پڑھو", "موجودہ صفحہ پڑھ کر سناؤ",
                "یہ صفحہ پڑھو", "اسکرین کا متن بتاؤ",
                "اسکرین پر کیا نظر آ رہا ہے", "اسکرین پر جو لکھا ہے وہ پڑھو",
                "جو کچھ اسکرین پر ہے پڑھو",
                "اقرأ الشاشة", "ماذا على الشاشة", "ماذا يظهر على الشاشة",
                "اقرأ النص الظاهر", "اقرأ ما هو مكتوب هنا"
            )
        ) {
            if (!ScreenControlBridge.isServiceConnected()) {
                return Result(false, "اسکرین پڑھنے کے لیے ادراک کی رسائی کی اجازت فعال کریں۔", "SERVICE_DISABLED")
            }
            val visibleText = ScreenControlBridge.readVisibleScreenText()
            return if (visibleText.isNullOrBlank()) {
                Result(false, "اسکرین سے پڑھنے کے قابل متن دستیاب نہیں۔", "SCREEN_READ_EMPTY")
            } else {
                Result(true, "اسکرین پر یہ متن نظر آ رہا ہے: $visibleText", "SCREEN_READ")
            }
        }

        // Enter dictated text only in the currently focused editable field.
        // This never presses Send, Submit, Pay, Delete, or any other action button.
        val textToEnter = extractTextToEnter(userCommand)
        if (textToEnter != null) {
            return if (ScreenControlBridge.typeTextInFocusedField(textToEnter)) {
                Result(true, "متن موجودہ منتخب خانے میں درج کر دیا گیا ہے۔ بھیجنے یا جمع کرنے سے پہلے خود جائزہ لیں۔", "TYPE_TEXT")
            } else {
                Result(false, "متن درج نہیں ہوا۔ پہلے مطلوبہ ایپ میں متن لکھنے والا خانہ منتخب کریں۔", "TYPE_TEXT_FAILED")
            }
        }

        // Back navigation
        if (matches(
                command,
                "back", "go back", "press back", "navigate back", "previous screen",
                "पीछे जाओ", "पीछे आओ", "वापस जाओ", "वापस आओ", "बैक दबाओ",
                "पिछली स्क्रीन पर जाओ",
                "پیچھے جاؤ", "واپس جاؤ", "واپس آؤ", "پچھلی اسکرین پر جاؤ", "بیک دباؤ",
                "ارجع", "ارجع للخلف", "عد للخلف", "رجوع"
            )
        ) {
            return perform("BACK") {
                ScreenControlBridge.back()
            }
        }

        // Home navigation
        if (matches(
                command,
                "go home", "open home", "home screen", "go to home screen",
                "होम खोलो", "होम पर जाओ", "होम पर आओ", "घर जाओ", "मुख्य स्क्रीन खोलो",
                "ہوم کھولو", "ہوم پر جاؤ", "ہوم پر آؤ", "گھر جاؤ", "مرکزی اسکرین کھولو",
                "افتح الصفحة الرئيسية", "اذهب إلى الصفحة الرئيسية", "الشاشة الرئيسية"
            )
        ) {
            return perform("HOME") {
                ScreenControlBridge.home()
            }
        }

        // Swipe UP
        if (matches(
                command,
                "swipe up", "swipe upward",
                "ऊपर स्वाइप", "ऊपर स्वाइप करो", "ऊपर की तरफ स्वाइप करो",
                "स्वाइप ऊपर", "स्वाइप ऊपर करो",
                "اوپر سوائپ", "اوپر سوائپ کرو", "اوپر کی طرف سوائپ کرو",
                "مرر للأعلى", "اسحب للأعلى", "اسحب إلى الأعلى"
            )
        ) {
            return perform("SWIPE_UP") {
                ScreenControlBridge.swipeUp()
            }
        }

        // Swipe DOWN
        if (matches(
                command,
                "swipe down", "swipe downward",
                "नीचे स्वाइप", "नीचे स्वाइप करो", "नीचे की तरफ स्वाइप करो",
                "स्वाइप नीचे", "स्वाइप नीचे करो",
                "نیچے سوائپ", "نیچے سوائپ کرو", "نیچے کی طرف سوائپ کرو",
                "مرر للأسفل", "اسحب للأسفل", "اسحب إلى الأسفل"
            )
        ) {
            return perform("SWIPE_DOWN") {
                ScreenControlBridge.swipeDown()
            }
        }

        // Swipe LEFT
        if (matches(
                command,
                "swipe left", "swipe to left",
                "left swipe", "बाएं स्वाइप",
                "बाएं स्वाइप करो", "बाईं तरफ स्वाइप करो",
                "स्वाइप बाएं",
                "بائیں سوائپ", "بائیں طرف سوائپ کرو", "بائیں جانب سوائپ کرو",
                "مرر لليسار", "اسحب لليسار", "اسحب إلى اليسار"
            )
        ) {
            return perform("SWIPE_LEFT") {
                ScreenControlBridge.swipeLeft()
            }
        }

        // Swipe RIGHT
        if (matches(
                command,
                "swipe right", "swipe to right",
                "right swipe", "दाएं स्वाइप",
                "दाएं स्वाइप करो", "दाईं तरफ स्वाइप करो",
                "स्वाइप दाएं",
                "دائیں سوائپ", "دائیں طرف سوائپ کرو", "دائیں جانب سوائپ کرو",
                "مرر لليمين", "اسحب لليمين", "اسحب إلى اليمين"
            )
        ) {
            return perform("SWIPE_RIGHT") {
                ScreenControlBridge.swipeRight()
            }
        }

        // Scroll DOWN
        if (matches(
                command,
                "scroll down",
                "नीचे स्क्रॉल करो", "स्क्रॉल नीचे करो", "नीचे जाओ", "नीचे करो",
                "نیچے اسکرول کرو", "نیچے جاؤ", "نیچے کی طرف اسکرول کرو",
                "مرر لأسفل", "انزل لأسفل", "انزل إلى الأسفل"
            )
        ) {
            return perform("SCROLL_DOWN") {
                ScreenControlBridge.scrollForward()
            }
        }

        // Scroll UP
        if (matches(
                command,
                "scroll up",
                "ऊपर स्क्रॉल करो", "स्क्रॉल ऊपर करो", "ऊपर जाओ", "ऊपर करो",
                "اوپر اسکرول کرو", "اوپر جاؤ", "اوپر کی طرف اسکرول کرو",
                "مرر لأعلى", "اصعد لأعلى", "اصعد إلى الأعلى"
            )
        ) {
            return perform("SCROLL_UP") {
                ScreenControlBridge.scrollBackward()
            }
        }

        // Notifications
        if (matches(
                command,
                "open notifications",
                "show notifications", "notification panel",
                "नोटिफिकेशन खोलो", "नोटिफिकेशन दिखाओ",
                "نوٹیفکیشن کھولو", "نوٹیفکیشن دکھاؤ", "اطلاعات کھولو",
                "افتح الإشعارات", "اعرض الإشعارات", "لوحة الإشعارات"
            )
        ) {
            return perform("NOTIFICATIONS") {
                ScreenControlBridge.openNotifications()
            }
        }

        if (matches(command,
                "lock screen", "lock the screen",
                "स्क्रीन लॉक करो", "फोन लॉक करो", "मोबाइल लॉक करो",
                "اسکرین لاک کرو", "فون لاک کرو", "موبائل لاک کرو",
                "اقفل الشاشة", "قفل الشاشة", "اقفل الهاتف"
            )
        ) {
            return perform("LOCK_SCREEN") { ScreenControlBridge.lockScreen() }
        }

        if (matches(command,
                "open quick settings", "show quick settings", "quick settings panel",
                "क्विक सेटिंग खोलो", "क्विक सेटिंग्स खोलो",
                "فوری سیٹنگز کھولو", "کوئیک سیٹنگز دکھاؤ",
                "افتح الإعدادات السريعة", "اعرض الإعدادات السريعة"
            )
        ) {
            return perform("QUICK_SETTINGS") { ScreenControlBridge.openQuickSettings() }
        }

        if (matches(command,
                "open recent apps", "show recent apps", "recent apps",
                "हाल के ऐप खोलो", "हालिया ऐप दिखाओ",
                "حالیہ ایپس کھولو", "حالیہ ایپس دکھاؤ",
                "افتح التطبيقات الأخيرة", "اعرض التطبيقات الأخيرة"
            )
        ) {
            return perform("RECENT_APPS") { ScreenControlBridge.openRecentApps() }
        }

        if (matches(command,
                "open power menu", "show power menu", "power menu",
                "पावर मेन्यू खोलो", "पावर मेनू दिखाओ",
                "پاور مینو کھولو", "پاور مینو دکھاؤ",
                "افتح قائمة الطاقة", "قائمة الطاقة"
            )
        ) {
            return perform("POWER_MENU") { ScreenControlBridge.openPowerMenu() }
        }

        // Find requested text to click
        val targetText = extractClickTarget(command)

        if (targetText != null) {
            if (targetText.isBlank()) {
                return Result(
                    false,
                    "بتائیں کس بٹن پر کلک کرنا ہے۔",
                    "CLICK_EMPTY"
                )
            }

            // Sensitive actions are never clicked automatically.
            if (isSensitiveTarget(targetText)) {
                return Result(
                    false,
                    "یہ اہم کارروائی ہو سکتی ہے۔ پہلے آپ کی تصدیق ضروری ہے۔",
                    "CLICK_CONFIRMATION_REQUIRED"
                )
            }

            return perform("CLICK_TEXT") {
                ScreenControlBridge.tapText(targetText)
            }
        }

        return Result(
            false,
            "اسکرین کا حکم سمجھ نہیں آیا۔ براہِ کرم واضح بتائیں۔",
            "UNKNOWN"
        )
    }

    /**
     * Executes a previously blocked sensitive click.
     * Call this only after OfflineBrain has received
     * an explicit confirmation for the pending target.
     */
    fun executeConfirmedClick(targetText: String): Result {
        val target = normalize(targetText)

        if (target.isBlank()) {
            return Result(
                false,
                "کلک کرنے کے لیے بٹن کا نام دستیاب نہیں۔",
                "CLICK_EMPTY"
            )
        }

        return perform("CONFIRMED_CLICK") {
            ScreenControlBridge.tapText(target)
        }
    }

    private fun perform(
        intent: String,
        action: () -> Boolean
    ): Result {

        if (!ScreenControlBridge.isServiceConnected()) {
            return Result(
                false,
                "ادراک کا اسکرین کنٹرول ابھی فعال نہیں۔ فون کی رسائی کی ترتیبات میں اجازت دیں۔",
                "SERVICE_DISABLED"
            )
        }

        return try {
            val completed = action()

            if (completed) {
                Result(
                    true,
                    "حکم کامیابی سے مکمل ہو گیا۔",
                    intent
                )
            } else {
                Result(
                    false,
                    "عمل مکمل نہیں ہوا۔ اسکرین پر مطلوبہ اختیار دستیاب نہیں تھا۔",
                    "ACTION_FAILED"
                )
            }

        } catch (_: Exception) {
            Result(
                false,
                "اسکرین کنٹرول میں مسئلہ پیش آیا۔",
                "ACTION_ERROR"
            )
        }
    }

    private fun extractTextToEnter(rawCommand: String): String? {
        val raw = rawCommand.trim()
        val prefixes = listOf(
            "enter text ", "type text ", "type ", "write ",
            "لکھو ", "لکھ دیں ", "لکھ دو ", "متن لکھو ", "یہ لکھو ",
            "یہ عبارت لکھو ", "ٹائپ کرو ", "ٹائپ کر دو ", "متن درج کرو ",
            "اس خانے میں لکھو ", "اس خانے میں یہ لکھو ", "لکھ کر درج کرو ",
            "اكتب النص ", "اكتب "
        )
        val prefix = prefixes.firstOrNull { raw.startsWith(it, ignoreCase = true) }
            ?: return null
        val text = raw.drop(prefix.length).trim()
        return text.takeIf { it.isNotBlank() && it.length <= 2000 }
    }

    private fun extractClickTarget(
        command: String
    ): String? {

        val prefixes = listOf(
            "click on ", "click ", "tap on ", "tap ", "press ",
            "क्लिक करो ", "दबाओ ",
            "کلک کرو ", "کلک کریں ", "دباؤ ", "دبائیں ",
            "انقر على ", "اضغط على ", "انقر ", "اضغط ", "المس "
        )

        for (prefix in prefixes) {
            if (command.startsWith(prefix)) {
                return command.removePrefix(prefix).trim()
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
            if (command.endsWith(suffix)) {
                return command.removeSuffix(suffix).trim()
            }
        }

        return null
    }

    private fun isSensitiveTarget(
        target: String
    ): Boolean {

        val sensitiveWords = listOf(
            "send", "submit", "delete", "remove",
            "purchase", "buy", "pay", "confirm",
            "allow", "grant", "permission", "enable", "accept",
            "agree", "authorize", "install", "subscribe", "unsubscribe",
            "log out", "logout", "sign out", "signout", "delete account", "close account",
            "reset", "factory reset", "erase all", "revoke", "disconnect", "remove account",
            "deactivate", "disable account", "cancel subscription",
            "transfer", "send money", "place order",
            "share", "post", "publish",
            "إرسال", "ارسل", "أرسل", "احذف", "حذف",
            "شراء", "اشتر", "ادفع", "دفع", "أكد", "تأكيد",
            "تحويل", "حوّل", "شارك", "انشر", "نشر",
            "भेजो", "भेजें", "भेज दो", "डिलीट", "हटाओ", "हटाएं", "हटा दो", "मिटाओ", "मिटा दो",
            "खरीदो", "खरीदें", "पेमेंट", "भुगतान", "भुगतान करें",
            "पुष्टि", "ट्रांसफर", "शेयर", "शेयर करो", "पोस्ट", "प्रकाशित", "साझा", "साझा करें",
            "लॉग आउट", "साइन आउट", "खाता हटाएं", "खाता मिटाएं", "खाता बंद करो",
            "फैक्टरी रीसेट", "रीसेट करो", "सब मिटाओ", "एक्सेस हटाओ",
            "بھیجو", "بھیجیں", "بھیج دو",
            "اجازت دیں", "اجازت دو", "اجازت قبول کرو",
            "فعال کریں", "فعال کرو", "منظور کرو", "قبول کرو",
            "متفق ہوں", "رضامند ہوں", "انسٹال کریں", "انسٹال کرو",
            "لاگ آؤٹ", "سائن آؤٹ", "اکاؤنٹ حذف", "اکاؤنٹ بند",
            "فیکٹری ری سیٹ", "ری سیٹ", "سب کچھ مٹا", "رسائی ختم", "منقطع",
            "سبسکرائب کریں", "سبسکرائب کرو", "رکنیت لیں", "رکنیت ختم کریں",
            "حذف", "حذف کریں", "مٹاؤ", "مٹا دو", "مٹا دیں",
            "خریدیں", "خرید لو", "ادائیگی", "ادا کرو", "ادا کریں",
            "تصدیق", "منتقل کرو", "رقم بھیجو", "پیسے بھیجو",
            "شیئر", "پوسٹ", "شائع", "بانٹ",
            "امسح", "امسحها", "احذف", "احذفها", "اشتر", "اشترِ", "ادفع", "ادفع الآن",
            "أرسل", "ارسل", "انشر", "شارك", "مشاركة", "حوّل", "حول",
            "السماح", "اسمح", "إذن", "تمكين", "قبول",
            "أوافق", "موافق", "تثبيت", "اشتراك", "إلغاء الاشتراك",
            "تسجيل الخروج", "تسجيل خروج", "حذف الحساب", "إغلاق الحساب",
            "إعادة ضبط", "ضبط المصنع", "مسح الكل", "إلغاء الوصول", "قطع الاتصال", "إزالة الحساب", "تعطيل الحساب"
        )

        val value = normalize(target)

        return sensitiveWords.any { word ->
            value.contains(normalize(word))
        }
    }

    private fun matches(
        command: String,
        vararg phrases: String
    ): Boolean {

        return phrases.any { phrase ->
            command == normalize(phrase)
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
}
