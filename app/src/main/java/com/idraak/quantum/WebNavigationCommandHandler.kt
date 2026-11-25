package com.idraak.quantum

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Opens an explicitly requested website or search in an external browser/app.
 *
 * Idraak itself does not access the network and does not need INTERNET permission.
 * Any network activity belongs to the external app opened by Android.
 */
object WebNavigationCommandHandler {

    private data class Request(
        val uri: Uri,
        val description: String
    )

    fun isNavigationCommand(command: String): Boolean =
        parseRequest(command) != null

    fun tryHandle(
        context: Context,
        command: String,
        launchActivity: Boolean = true
    ): String? {
        val request = parseRequest(command) ?: return null

        if (!launchActivity) {
            return "ویب سائٹ یا تلاش تیار ہے۔ جاری رکھنے کے لیے ادراک کی اطلاع میں کھولنے کا اختیار دبائیں۔"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, request.uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.applicationContext.startActivity(intent)
            request.description
        } catch (_: ActivityNotFoundException) {
            "اس لنک کو کھولنے کے لیے کوئی موزوں براؤزر یا ایپ دستیاب نہیں۔"
        } catch (_: Exception) {
            "ویب سائٹ یا تلاش نہیں کھل سکی۔ دوبارہ کوشش کریں۔"
        }
    }

    private fun parseRequest(command: String): Request? {
        val value = command.trim()
            .lowercase(Locale.ROOT)
            .replace('’', '\'')

        if (value.isBlank()) return null

        val searchMarkers = listOf(
            "search youtube for ",
            "search on youtube for ",
            "youtube search for ",
            "youtube par search karo ",
            "youtube pe search karo ",
            "youtube par khojo ",
            "यूट्यूब पर सर्च करो ",
            "यूट्यूब पर खोजो ",
            "यूट्यूब में खोजो ",
            "یوٹیوب پر تلاش کرو ",
            "یوٹیوب میں تلاش کرو ",
            "یوٹیوب پر سرچ کرو ",
            "یوٹیوب میں سرچ کرو ",
            "search google for ",
            "search on google for ",
            "google search for ",
            "google par search karo ",
            "google pe search karo ",
            "गूगल पर सर्च करो ",
            "गूगल पर खोजो ",
            "گوگل پر تلاش کرو ",
            "search web for ",
            "search the web for ",
            "web search for ",
            "internet par search karo ",
            "internet par khojo ",
            "इंटरनेट पर सर्च करो ",
            "इंटरनेट पर खोजो ",
            "ویب پر تلاش کرو "
        )

        val marker = searchMarkers
            .sortedByDescending { it.length }
            .firstOrNull { value.startsWith(it) }

        // Natural Urdu word order: "یوٹیوب پر [موضوع] تلاش کرو".
        if (marker == null) {
            val youtubeLead = listOf("یوٹیوب پر ", "یوٹیوب میں ")
                .firstOrNull { value.startsWith(it) }
            val youtubeTail = listOf(
                " تلاش کرو", " تلاش کریں", " ڈھونڈو", " ڈھونڈیں",
                " سرچ کرو", " سرچ کریں"
            ).firstOrNull { value.endsWith(it) }
            if (youtubeLead != null && youtubeTail != null) {
                val query = command.trim()
                    .drop(youtubeLead.length)
                    .dropLast(youtubeTail.length)
                    .trim()
                if (query.isNotBlank()) {
                    val encoded = Uri.encode(query)
                    return Request(
                        uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded"),
                        description = "یوٹیوب پر آپ کی تلاش کھول رہا ہوں۔"
                    )
                }
            }
        }

        if (marker != null) {
            val query = command.trim().drop(marker.length).trim()
                .trim(':', '-', '،', ',', '.', ' ')
            if (query.isBlank()) return null

            val isYouTube = marker.contains("youtube") ||
                marker.contains("यूट्यूब") ||
                marker.contains("یوٹیوب")
            val encodedQuery = Uri.encode(query)
            val uri = if (isYouTube) {
                Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
            } else {
                Uri.parse("https://www.google.com/search?q=$encodedQuery")
            }

            return Request(
                uri = uri,
                description = if (isYouTube) {
                    "یوٹیوب پر تلاش کھول رہا ہوں۔"
                } else {
                    "ویب تلاش کھول رہا ہوں۔"
                }
            )
        }

        val websitePrefixes = listOf(
            "open website ",
            "go to website ",
            "visit website ",
            "open site ",
            "website kholo ",
            "website khol do ",
            "वेबसाइट खोलो ",
            "वेबसाइट खोल दो ",
            "वेबसाइट पर जाओ ",
            "ویب سائٹ کھولو ",
            "ویب سائٹ پر جاؤ "
        )

        val openPrefix = listOf(
            "open ",
            "go to ",
            "visit "
        ).firstOrNull { value.startsWith(it) }

        val websitePrefix = websitePrefixes
            .sortedByDescending { it.length }
            .firstOrNull { value.startsWith(it) }

        val prefix = websitePrefix ?: openPrefix ?: return null
        val rawAddress = command.trim().drop(prefix.length).trim()
        if (rawAddress.isBlank()) return null

        // Plain app names such as YouTube must continue through app discovery.
        if (websitePrefix == null && !looksLikeWebAddress(rawAddress)) {
            return null
        }

        val candidate = if (
            rawAddress.startsWith("https://", ignoreCase = true) ||
            rawAddress.startsWith("http://", ignoreCase = true)
        ) {
            rawAddress
        } else {
            "https://$rawAddress"
        }

        val uri = try {
            Uri.parse(candidate)
        } catch (_: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host.orEmpty()

        if (
            scheme !in setOf("http", "https") ||
            host.isBlank() ||
            !host.contains('.') ||
            host.any { it.isWhitespace() }
        ) {
            return null
        }

        return Request(
            uri = uri,
            description = "ویب سائٹ کھول رہا ہوں۔"
        )
    }

    private fun looksLikeWebAddress(value: String): Boolean {
        val candidate = value
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')

        return candidate.contains('.') &&
            candidate.none { it.isWhitespace() } &&
            candidate.matches(Regex("[A-Za-z0-9.-]+(?::[0-9]{1,5})?"))
    }
}
