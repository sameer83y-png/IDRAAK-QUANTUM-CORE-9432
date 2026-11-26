package com.idraak.quantum.engine

import android.content.Context
import android.content.Intent
import java.util.Locale

/**
 * Launches an already-installed Android app using only local package data.
 * This does not open websites or contact any server.
 */
object OfflinePhoneCommandHandler {

    data class Result(
        val handled: Boolean,
        val success: Boolean,
        val message: String,
        val appName: String = ""
    )

    private data class InstalledApp(val packageName: String, val label: String)

    private var pendingChoices: List<InstalledApp> = emptyList()
    private var pendingChoicesAt: Long = 0L
    private const val CHOICE_TIMEOUT_MS = 30_000L

    @Synchronized
    fun resolvePendingChoice(context: Context, userCommand: String): Result? {
        val waiting = pendingChoices
        if (waiting.isEmpty()) return null
        if (System.currentTimeMillis() - pendingChoicesAt > CHOICE_TIMEOUT_MS) {
            pendingChoices = emptyList()
            return null
        }
        val answer = normalize(userCommand)
        if (isChoiceCancellation(answer)) {
            pendingChoices = emptyList()
            return Result(true, false, "ٹھیک ہے، ایپ کا انتخاب منسوخ کر دیا گیا ہے۔")
        }

        val index = resolveChoiceIndex(answer, waiting.size)
        val selected = if (index != null) {
            listOf(waiting[index])
        } else {
            waiting.filter { app ->
                answer == normalize(app.label) ||
                    aliasesFor(answer).contains(normalize(app.label)) ||
                    normalize(app.label) == answer.removePrefix("open ").trim()
            }
        }
        if (selected.size == 1) {
            pendingChoices = emptyList()
            return launchSelected(context, selected.single())
        }

        // A different, complete command should not be trapped by an old
        // app-choice question. Clear the old choice and let the router handle it.
        if (isClearlyNewCommand(answer)) {
            pendingChoices = emptyList()
            return null
        }

        val names = waiting.take(5).mapIndexed { i, app -> "${i + 1}. ${app.label}" }
            .joinToString("، ")
        return Result(
            true,
            false,
            "براہِ کرم ایک ایپ منتخب کریں: $names۔ نام یا نمبر بتائیں، یا منسوخ کہیں۔",
            ""
        )
    }

    fun execute(context: Context, userCommand: String): Result? {
        val target = extractAppName(userCommand)
        if (target.isBlank()) {
            return Result(true, false, "کون سی ایپ کھولنی ہے؟ براہِ کرم اس کا نام واضح بتائیں۔")
        }

        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installedApps = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    if (activity.packageName == context.packageName) return@mapNotNull null
                    val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    if (label.isBlank()) return@mapNotNull null
                    InstalledApp(activity.packageName, label)
                }
                .distinctBy { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }

        if (installedApps.isEmpty()) {
            return Result(true, false, "فون میں دستیاب ایپس کی فہرست نہیں مل سکی۔ فون کی ایپ ترتیبات دیکھیں۔", target)
        }

        // If Idraak previously asked the user to choose between installed apps,
        // accept the next clear app name instead of treating it as a new command.
        val waiting = pendingChoices
        if (waiting.isNotEmpty()) {
            if (System.currentTimeMillis() - pendingChoicesAt > CHOICE_TIMEOUT_MS) {
                pendingChoices = emptyList()
            } else {
                val answer = normalize(userCommand)
                val selectedByAnswer = waiting.filter { app ->
                    answer == normalize(app.label) ||
                        aliasesFor(answer).contains(normalize(app.label)) ||
                        normalize(app.label) == answer.removePrefix("open ").trim()
                }
                if (selectedByAnswer.size == 1) {
                    pendingChoices = emptyList()
                    return launchSelected(context, selectedByAnswer.single())
                }
                if (selectedByAnswer.size > 1) {
                    return Result(true, false, "ایک ہی نام کی کئی ایپس ہیں۔ براہِ کرم مکمل نام بتائیں۔", target)
                }
            }
        }

        // Support explicit alternatives such as "open YouTube or WhatsApp".
        // Each candidate is resolved against the launchable apps on this phone.
        val candidateTargets = splitAppChoices(target)
        val resolved = candidateTargets.flatMap { candidate ->
            val aliases = aliasesFor(candidate)
            val exact = installedApps.filter { app ->
                val label = normalize(app.label)
                label == candidate || aliases.contains(label)
            }
            if (exact.isNotEmpty()) exact else {
                val tokens = candidate.split(" ").filter { it.isNotBlank() }
                installedApps.filter { app ->
                    val labelTokens = normalize(app.label).split(" ").filter { it.isNotBlank() }
                    tokens.isNotEmpty() && labelTokens.size >= tokens.size &&
                        labelTokens.windowed(tokens.size).any { it == tokens }
                }
            }
        }.distinctBy { it.packageName }

        if (resolved.isEmpty()) {
            return Result(true, false, "فون میں \"$target\" نام کی ایپ نہیں ملی۔ اگر ایپ نصب ہے تو اس کا درست نام بتائیں۔", target)
        }
        if (resolved.size > 1) {
            pendingChoices = resolved
            pendingChoicesAt = System.currentTimeMillis()
            val names = resolved.take(5).joinToString(" یا ") { it.label }
            return Result(
                true,
                false,
                "ایک سے زیادہ ایپس سمجھ آ رہی ہیں: $names۔ آپ کون سی ایپ کھولنا چاہتے ہیں؟ صرف اس کا نام بتائیں۔",
                target
            )
        }

        return launchSelected(context, resolved.single())
    }

    private fun launchSelected(context: Context, selected: InstalledApp): Result {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(selected.packageName)
                ?: return Result(true, false, "${selected.label} مل گئی، لیکن فون نے اسے کھولنے کی اجازت نہیں دی۔", selected.label)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(launchIntent)
            Result(true, true, "${selected.label} کھول رہا ہوں۔", selected.label)
        } catch (_: Exception) {
            Result(true, false, "${selected.label} نہیں کھل سکی۔ فون نے اسے کھولنے سے روک دیا۔", selected.label)
        }
    }

    internal fun resolveChoiceIndex(answer: String, count: Int): Int? {
        if (count <= 0) return null
        val value = normalize(answer)
        val number = Regex("""^(?:number\s*)?([1-5])$""").matchEntire(value)
            ?.groupValues?.get(1)?.toIntOrNull()
        if (number != null && number <= count) return number - 1

        val first = setOf("first", "one", "pehla", "pehli", "پہلا", "پہلی", "اول", "पहला", "पहली", "एक")
        val second = setOf("second", "two", "doosra", "doosri", "دوسرا", "دوسری", "دوم", "दूसरा", "दूसरी", "दो")
        val third = setOf("third", "three", "teesra", "teesri", "تیسرا", "تیسری", "سوم", "तीसरा", "तीसरी", "तीन")
        val fourth = setOf("fourth", "four", "chautha", "چوتھا", "चौथा")
        val fifth = setOf("fifth", "five", "paanchwa", "پانچواں", "पाँचवाँ")
        return when (value) {
            in first -> 0.takeIf { count >= 1 }
            in second -> 1.takeIf { count >= 2 }
            in third -> 2.takeIf { count >= 3 }
            in fourth -> 3.takeIf { count >= 4 }
            in fifth -> 4.takeIf { count >= 5 }
            else -> null
        }
    }

    private fun isChoiceCancellation(value: String): Boolean =
        value in setOf("cancel", "cancel it", "no", "stop", "منسوخ", "منسوخ کرو", "رہنے دو", "چھوڑ دو", "रद्द", "रद्द करो", "नहीं", "मत")

    internal fun isClearlyNewCommand(value: String): Boolean {
        val command = normalize(value)
        val prefixes = listOf(
            // English
            "open ", "launch ", "start ", "run ", "search ", "find ",
            "call ", "send ", "set ", "remind ", "play ", "turn on ",
            "turn off ", "take a photo", "take a picture",
            // Roman Urdu / Hindi
            "kholo ", "khol do ", "kholiye ", "zara kholo ", "zara khol do ",
            "app kholo ", "app khol do ", "youtube par ", "whatsapp par ",
            "khojo ", "talash karo ", "call karo ", "message bhejo ",
            "yaad dilana ", "alarm lagao ", "torch chalu ",
            // Hindi
            "खोलो ", "खोल दो ", "खोलिए ", "जरा खोलो ", "ऐप खोलो ",
            "यूट्यूब पर ", "खोजो ", "ढूँढो ", "कॉल ", "मैसेज भेजो ",
            "याद दिलाओ ", "अलार्म लगाओ ", "टॉर्च जलाओ ",
            // Urdu
            "کھولو ", "کھول دو ", "کھولیں ", "ذرا کھولو ", "ایپ کھولو ",
            "یوٹیوب پر ", "تلاش کرو ", "ڈھونڈو ", "کال کرو ",
            "پیغام بھیجو ", "یاد دلاؤ ", "یاد دلانا ", "الارم لگاؤ ",
            "ٹارچ جلاؤ ", "ٹارچ آن کرو ",
            // Arabic
            "افتح ", "شغل ", "أغلق ", "اغلق ", "ابحث ", "اتصل ",
            "أرسل ", "ارسل ", "ذكّرني ", "اضبط المنبه ", "شغّل "
        )
        return prefixes.any { command.startsWith(it) }
    }

    internal fun splitAppChoices(target: String): List<String> =
        target.split(
            Regex("\\s+(?:ya phir|یا پھر|या फिर|or|ya|या|یا)\\s+"),
            limit = 5
        ).map { normalize(it) }.filter { it.isNotBlank() }

    internal fun extractAppName(command: String): String {
        var value = normalize(command)
        val conversationalPrefixes = listOf(
            "bhai ", "yaar ", "please ", "kindly ", "zara ",
            "idraak ", "इद्राक ", "ادراک ",
            "भाई ", "यार ", "जरा ", "कृपया ",
            "بھائی ", "یار ", "ذرا ", "براہ کرم "
        ).sortedByDescending { it.length }
        // Remove stacked assistant-name and polite lead-ins, e.g.
        // "Idraak, please open YouTube" or "ادراک، ذرا یوٹیوب کھولو".
        while (true) {
            val prefix = conversationalPrefixes.firstOrNull { value.startsWith(it) } ?: break
            value = value.removePrefix(prefix).trim()
        }

        val prefixes = listOf(
            "could you please open ", "please open the ", "could you open ", "please launch ",
            "open the app ", "open app ", "open the ", "open up ", "open ", "launch the app ", "launch ",
            "start the app ", "start app ", "start ", "run the app ", "run ",
            "app open karo ", "app kholo ", "app khol do ", "zara kholo ", "zara khol do ",
            "khol do ", "kholo ", "kholiye ", "chalu karo ", "ऐप खोल दो ", "ऐप खोलो ",
            "जरा खोलो ", "जरा खोल दो ", "खोल दो ", "खोलो ", "खोलिए ",
            "चालू कर दो ", "चालू करो ", "ایپ کھول دو ", "ایپ کھولو ",
            "ذرا کھولو ", "ذرا کھول دو ", "کھول دو ", "کھولو ", "کھولیں ", "کھول دیں ", "کھول دیجیے ", "کھول کر رکھو ", "کھول کے رکھو ", "کھول کر دکھاؤ ", "کھول کے دکھاؤ ",
            "چالو کر دو ", "چالو کرو ", "چلا دو ", "چلاؤ "
        )
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (value.startsWith(prefix)) {
                value = value.removePrefix(prefix).trim()
                break
            }
        }

        val suffixes = listOf(
            " please open", " open please", " open kar do", " open karo", " open",
            " launch karo", " start kar do", " start karo", " on kar do", " on karo",
            " chalu kar do", " chalu karo", " kholo", " khol do", " kholiye", " chalao", " chala do",
            " ऐप खोल दो", " ऐप खोलो", " खोल देना", " खोल दीजिए", " खोलिए", " खोल दो", " खोलो",
            " चला देना", " चालू कर देना", " चालू कर दो", " चालू करो", " ऑन कर दो",
            " کھول دینا", " کھول دیجیے", " کھول دیں", " کھولیں", " کھول دو", " کھولو", " کھول کر رکھو", " کھول کے رکھو", " کھول کر رکھ دو", " کھول کے رکھ دو", " کھول کر دکھاؤ", " کھول کے دکھاؤ", " چلا دو", " چلا دینا",
            " چلا کر دکھاؤ", " چلا کے دکھاؤ", " چلا کر رکھو", " چلا کے رکھو",
            " چالو کر کے رکھو", " چالو کرکے رکھو", " چالو کر دو", " چالو کرو",
            " شروع کر دو", " شروع کرو", " شروع کریں", " آن کر دو", " آن کرو"
        )
        for (suffix in suffixes.sortedByDescending { it.length }) {
            if (value.endsWith(suffix)) {
                value = value.removeSuffix(suffix).trim()
                break
            }
        }
        value = value.removeSuffix(" app").removePrefix("the ").trim()
        return value
    }

    private fun aliasesFor(target: String): Set<String> {
        return mapOf(
            // YouTube
            "यूट्यूब" to setOf("youtube"),
            "یوٹیوب" to setOf("youtube"),

            // WhatsApp
            "व्हाट्सएप" to setOf("whatsapp"),
            "व्हाट्सऐप" to setOf("whatsapp"),
            "واٹس ایپ" to setOf("whatsapp"),

            // Chrome
            "क्रोम" to setOf("chrome"),
            "کروم" to setOf("chrome"),

            // Instagram and Facebook
            "इंस्टाग्राम" to setOf("instagram"),
            "انسٹاگرام" to setOf("instagram"),
            "فیس بک" to setOf("facebook"),
            "फेसबुक" to setOf("facebook"),

            // Gmail and Maps
            "जीमेल" to setOf("gmail"),
            "جی میل" to setOf("gmail"),
            "गूगल मैप" to setOf("maps", "google maps"),
            "गूगल मैप्स" to setOf("maps", "google maps"),
            "گوگل میپس" to setOf("maps", "google maps"),

            // Camera and Settings
            "कैमरा" to setOf("camera"),
            "کیمرہ" to setOf("camera"),
            "सेटिंग" to setOf("settings"),
            "सेटिंग्स" to setOf("settings"),
            "سیٹنگز" to setOf("settings")
        )[target].orEmpty()
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }
}
