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

        val normalizedTarget = normalize(target)
        val aliases = aliasesFor(normalizedTarget)
        val exactMatches = installedApps.filter { app ->
            val label = normalize(app.label)
            label == normalizedTarget || aliases.contains(label)
        }
        // Fallback matching is token-based, not arbitrary substring matching.
        // This prevents a short target such as "book" from accidentally opening
        // an unrelated app whose label merely contains those letters (for example, Facebook).
        val targetTokens = normalizedTarget.split(" ").filter { it.isNotBlank() }
        val matches = if (exactMatches.isNotEmpty()) {
            exactMatches
        } else {
            installedApps.filter { app ->
                val labelTokens = normalize(app.label).split(" ").filter { it.isNotBlank() }
                targetTokens.isNotEmpty() &&
                    labelTokens.size >= targetTokens.size &&
                    labelTokens.windowed(targetTokens.size).any { it == targetTokens }
            }
        }

        if (matches.isEmpty()) {
            return Result(true, false, "فون میں \"$target\" نام کی ایپ نہیں ملی۔ اگر ایپ نصب ہے تو اس کا درست نام بتائیں۔", target)
        }
        if (matches.size > 1) {
            val names = matches.take(4).joinToString(", ") { it.label }
            return Result(true, false, "ایک سے زیادہ ملتی جلتی ایپس ملیں: $names۔ براہِ کرم مکمل نام بتائیں۔", target)
        }

        val selected = matches.single()
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(selected.packageName)
                ?: return Result(true, false, "${selected.label} مل گئی، لیکن فون نے اسے کھولنے کی اجازت نہیں دی۔", selected.label)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.applicationContext.startActivity(launchIntent)
            Result(true, true, "${selected.label} کھول رہا ہوں۔", selected.label)
        } catch (_: Exception) {
            Result(true, false, "${selected.label} نہیں کھل سکی۔ فون نے اسے کھولنے سے روک دیا۔", selected.label)
        }
    }

    private fun extractAppName(command: String): String {
        var value = normalize(command)
        val conversationalPrefixes = listOf(
            "bhai ", "yaar ", "please ", "zara ",
            "भाई ", "यार ", "जरा ", "कृपया ",
            "بھائی ", "یار ", "ذرا "
        )
        conversationalPrefixes.forEach { if (value.startsWith(it)) value = value.removePrefix(it).trim() }

        val prefixes = listOf(
            "could you please open ", "please open the ", "could you open ", "please launch ",
            "open the app ", "open app ", "open the ", "open up ", "launch the app ", "launch ",
            "start the app ", "start app ", "start ", "run the app ", "run ",
            "app open karo ", "app kholo ", "app khol do ", "zara kholo ", "zara khol do ",
            "khol do ", "kholo ", "kholiye ", "chalu karo ", "ऐप खोल दो ", "ऐप खोलो ",
            "जरा खोलो ", "जरा खोल दो ", "खोल दो ", "खोलो ", "खोलिए ",
            "चालू कर दो ", "चालू करो ", "ایپ کھول دو ", "ایپ کھولو ",
            "ذرا کھولو ", "ذرا کھول دو ", "کھول دو ", "کھولو ", "کھولیں ", "کھول کر رکھو ", "کھول کے رکھو ", "کھول کر دکھاؤ ", "کھول کے دکھاؤ "
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
            " کھول دینا", " کھول دیجیے", " کھولیں", " کھول دو", " کھولو", " کھول کر رکھو", " کھول کے رکھو", " کھول کر رکھ دو", " کھول کے رکھ دو", " کھول کر دکھاؤ", " کھول کے دکھاؤ", " چلا دو", " چلا دینا",
            " شروع کرو", " آن کر دو"
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
