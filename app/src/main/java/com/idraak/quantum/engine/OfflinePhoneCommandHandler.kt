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
 * Recognizes common Hindi and English app-opening commands.
 * Does not make calls, send messages, purchase, or delete anything.
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
                )

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
                )

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
                message = "यह ऐप आपके फोन में उपलब्ध नहीं है।",
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

        val prefixes = listOf(
            "open ",
            "open the ",
            "launch ",
            "start ",
            "run ",
            "please open ",
            "खोलो ",
            "खोल दो ",
            "ऐप खोलो ",
            "ऐप खोल दो ",
            "चालू करो "
        )

        var foundPrefix = false

        for (prefix in prefixes) {
            if (value.startsWith(prefix)) {
                value = value.removePrefix(prefix).trim()
                foundPrefix = true
                break
            }
        }

        if (!foundPrefix) {

            val suffixes = listOf(
                " खोल दो",
                " खोलो",
                " खोलिए",
                " चालू करो",
                " open karo",
                " open kar do"
            )

            for (suffix in suffixes) {
                if (value.endsWith(suffix)) {
                    value = value.removeSuffix(suffix).trim()
                    foundPrefix = true
                    break
                }
            }
        }

        if (!foundPrefix) return null

        value = value
            .removePrefix("app ")
            .removeSuffix(" app")
            .trim()

        return resolveAlias(value)
    }

    private fun resolveAlias(value: String): String? {

        return when (value) {

            "youtube",
            "you tube",
            "यूट्यूब",
            "यु ट्यूब" -> "youtube"

            "chrome",
            "google chrome",
            "क्रोम" -> "chrome"

            "whatsapp",
            "what's app",
            "व्हाट्सएप",
            "व्हाट्स ऐप" -> "whatsapp"

            "chatgpt",
            "chat gpt",
            "चैट जीपीटी" -> "chatgpt"

            "gemini",
            "जेमिनी" -> "gemini"

            "maps",
            "google maps",
            "मैप",
            "मैप्स",
            "गूगल मैप" -> "maps"

            "gmail",
            "जीमेल" -> "gmail"

            "camera",
            "कैमरा" -> "camera"

            "settings",
            "setting",
            "सेटिंग",
            "सेटिंग्स" -> "settings"

            "phone",
            "dialer",
            "फोन",
            "डायलर" -> "phone"

            "contacts",
            "contact",
            "कॉन्टैक्ट",
            "कॉन्टैक्ट्स" -> "contacts"

            "calculator",
            "calc",
            "कैलकुलेटर" -> "calculator"

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

    private fun openCalculator(
        context: Context
    ): Result {

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
