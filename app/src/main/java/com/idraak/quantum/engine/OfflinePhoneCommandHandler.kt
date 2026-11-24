package com.idraak.quantum.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import java.util.Locale

/**
 * Idraak Offline Phone Command Handler
 *
 * Supports common Hindi, Hinglish, English and Urdu app-opening commands.
 * Only opens allowlisted apps or system screens.
 * Does not call, send messages, purchase or delete anything.
 */
object OfflinePhoneCommandHandler {

    data class Result(
        val handled: Boolean,
        val success: Boolean,
        val message: String,
        val appName: String = ""
    )

    fun execute(
        context: Context,
        userCommand: String
    ): Result? {
        val target = extractTarget(userCommand) ?: return null
        val appContext = context.applicationContext

        return try {
            when (target) {
                "youtube" -> launchPackage(
                    appContext,
                    "com.google.android.youtube",
                    "YouTube"
                ) ?: openWeb(
                    appContext,
                    "https://www.youtube.com",
                    "YouTube"
                )

                "chrome" -> launchPackage(
                    appContext,
                    "com.android.chrome",
                    "Chrome"
                ) ?: openWeb(
                    appContext,
                    "https://www.google.com",
                    "Browser"
                )

                "whatsapp" -> launchPackage(
                    appContext,
                    "com.whatsapp",
                    "WhatsApp"
                ) ?: unavailable("WhatsApp")

                "chatgpt" -> launchPackage(
                    appContext,
                    "com.openai.chatgpt",
                    "ChatGPT"
                ) ?: openWeb(
                    appContext,
                    "https://chatgpt.com",
                    "ChatGPT"
                )

                "gemini" -> launchPackage(
                    appContext,
                    "com.google.android.apps.bard",
                    "Gemini"
                ) ?: openWeb(
                    appContext,
                    "https://gemini.google.com",
                    "Gemini"
                )

                "maps" -> launchPackage(
                    appContext,
                    "com.google.android.apps.maps",
                    "Google Maps"
                ) ?: openWeb(
                    appContext,
                    "https://maps.google.com",
                    "Google Maps"
                )

                "gmail" -> launchPackage(
                    appContext,
                    "com.google.android.gm",
                    "Gmail"
                ) ?: unavailable("Gmail")

                "camera" -> openSystemIntent(
                    appContext,
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE),
                    "Camera"
                )

                "settings" -> openSystemIntent(
                    appContext,
                    Intent(Settings.ACTION_SETTINGS),
                    "Settings"
                )

                "phone" -> openSystemIntent(
                    appContext,
                    Intent(Intent.ACTION_DIAL),
                    "Phone dialer"
                )

                "contacts" -> openSystemIntent(
                    appContext,
                    Intent(
                        Intent.ACTION_VIEW,
                        ContactsContract.Contacts.CONTENT_URI
                    ),
                    "Contacts"
                )

                "calculator" -> openCalculator(appContext)

                else -> Result(
                    handled = true,
                    success = false,
                    message = "यह ऐप मेरी Offline App List में नहीं है। ऐप का सही नाम बताइए।",
                    appName = target
                )
            }
        } catch (_: ActivityNotFoundException) {
            Result(
                handled = true,
                success = false,
                message = "यह ऐप या इसकी सुविधा आपके फोन में उपलब्ध नहीं है।",
                appName = target
            )
        } catch (error: Exception) {
            Result(
                handled = true,
                success = false,
                message = "ऐप खोलने में समस्या आई: ${
                    error.localizedMessage ?: "Unknown error"
                }",
                appName = target
            )
        }
    }

    private fun extractTarget(command: String): String? {
        var value = normalize(command)

        if (value.isBlank()) return null

        // Longest phrases first, so "open the app" is handled correctly.
        val prefixes = listOf(
            "could you please open ",
            "please open the ",
            "could you open ",
            "open up ",
            "please launch ",
            "please open ",
            "open the app ",
            "open the ",
            "open app ",
            "open ",
            "launch ",
            "start ",
            "run ",
            "app kholo ",
            "app khol do ",
            "zara kholo ",
            "zara khol do ",
            "khol do ",
            "kholo ",
            "kholiye ",
            "kholein ",
            "khole ",
            "chalu karo ",
            "चालू करो ",
            "ऐप खोल दो ",
            "ऐप खोलो ",
            "जरा खोल दो ",
            "जरा खोलो ",
            "खोल दो ",
            "खोलो ",
            "खोलिए ",
            "ایپ کھول دو ",
            "ایپ کھولو ",
            "ذرا کھول دو ",
            "ذرا کھولو ",
            "کھول دو ",
            "کھولو ",
            "کھولیں "
        )

        var commandMatched = false

        for (prefix in prefixes) {
            if (value.startsWith(prefix)) {
                value = value.removePrefix(prefix).trim()
                commandMatched = true
                break
            }
        }

        if (!commandMatched) {
            val suffixes = listOf(
                " please open",
                " please kholo",
                " open kar do",
                " open karo",
                " khol do",
                " kholo",
                " kholiye",
                " chala do",
                " chalao",
                " start kar do",
                " chalu kar do",
                " chalao please",
                " खोल दो",
                " खोलो",
                " खोलिए",
                " चला दो",
                " चालू करो",
                " کھول دو",
                " کھولو",
                " کھولیں",
                " چلا دو",
                " چلاؤ",
                " کھول دیں"
            )

            for (suffix in suffixes) {
                if (value.endsWith(suffix)) {
                    value = value.removeSuffix(suffix).trim()
                    commandMatched = true
                    break
                }
            }
        }

        if (!commandMatched) return null

        value = value
            .removePrefix("the ")
            .removePrefix("app ")
            .removeSuffix(" app")
            .removeSuffix(" application")
            .trim()

        return resolveAlias(value)
    }

    private fun resolveAlias(value: String): String? {
        return when (value) {
            "youtube",
            "you tube",
            "यूट्यूब",
            "यु ट्यूब",
            "یوٹیوب" -> "youtube"

            "chrome",
            "google chrome",
            "क्रोम",
            "کروم" -> "chrome"

            "whatsapp",
            "what's app",
            "whats app",
            "व्हाट्सएप",
            "व्हाट्स ऐप",
            "واٹس ایپ" -> "whatsapp"

            "chatgpt",
            "chat gpt",
            "चैट जीपीटी" -> "chatgpt"

            "gemini",
            "जेमिनी",
            "جیمنی" -> "gemini"

            "maps",
            "google maps",
            "मैप",
            "मैप्स",
            "गूगल मैप",
            "گوگل میپس" -> "maps"

            "gmail",
            "जीमेल",
            "جی میل" -> "gmail"

            "camera",
            "कैमरा",
            "کیمرہ" -> "camera"

            "settings",
            "setting",
            "सेटिंग",
            "सेटिंग्स",
            "سیٹنگز" -> "settings"

            "phone",
            "dialer",
            "फोन",
            "डायलर",
            "فون" -> "phone"

            "contacts",
            "contact",
            "कॉन्टैक्ट",
            "कॉन्टैक्ट्स",
            "کانٹیکٹس" -> "contacts"

            "calculator",
            "calc",
            "कैलकुलेटर",
            "کیلکولیٹر" -> "calculator"

            else -> null
        }
    }

    private fun launchPackage(
        context: Context,
        packageName: String,
        appName: String
    ): Result? {
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(packageName)
            ?: return null

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)

        return Result(
            handled = true,
            success = true,
            message = "$appName खोल दिया गया है।",
            appName = appName
        )
    }

    private fun openWeb(
        context: Context,
        url: String,
        appName: String
    ): Result {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)

        return Result(
            handled = true,
            success = true,
            message = "$appName ब्राउज़र में खोल दिया गया है।",
            appName = appName
        )
    }

    private fun openSystemIntent(
        context: Context,
        intent: Intent,
        appName: String
    ): Result {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        return Result(
            handled = true,
            success = true,
            message = "$appName खोल दिया गया है।",
            appName = appName
        )
    }

    private fun openCalculator(context: Context): Result {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_CALCULATOR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)

        return Result(
            handled = true,
            success = true,
            message = "Calculator खोल दिया गया है।",
            appName = "Calculator"
        )
    }

    private fun unavailable(appName: String): Result {
        return Result(
            handled = true,
            success = false,
            message = "$appName आपके फोन में उपलब्ध नहीं है।",
            appName = appName
        )
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .lowercase(Locale.ROOT)
            .replace(
                Regex("[^\\p{L}\\p{M}\\p{N}\\s']"),
                " "
            )
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
