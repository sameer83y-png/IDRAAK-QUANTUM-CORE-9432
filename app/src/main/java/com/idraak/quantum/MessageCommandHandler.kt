package com.idraak.quantum

import android.content.Context
import android.content.Intent

/**
 * Detects offline message-composition commands and opens the local message flow.
 * Recipient selection and final sending remain inside Android or the chosen app.
 */
object MessageCommandHandler {

    private data class MessageRequest(
        val body: String,
        val targetPackage: String?
    )

    fun requiresForegroundUi(command: String): Boolean =
        extractRequest(command)?.body?.isNotBlank() == true

    fun tryHandle(context: Context, command: String, launchActivity: Boolean = true): String? {
        val request = extractRequest(command) ?: return null
        if (request.body.isBlank()) {
            return "پیغام کا متن بتائیں۔ مثال: احمد کو واٹس ایپ پر پیغام بھیجیں کہ میں گھر آ رہا ہوں۔"
        }

        if (!launchActivity) {
            val destination = request.targetPackage?.let { "منتخب ایپ" } ?: "ایس ایم ایس یا دستیاب پیغام رسانی ایپ"
            return "پیغام تیار ہے۔ ادراک کھول کر تصدیق کریں اور $destination میں وصول کنندہ منتخب کریں۔"
        }

        return try {
            val intent = Intent(context, MessageControllerActivity::class.java).apply {
                putExtra(MessageControllerActivity.EXTRA_MESSAGE_BODY, request.body)
                request.targetPackage?.let {
                    putExtra(MessageControllerActivity.EXTRA_TARGET_PACKAGE, it)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val destination = request.targetPackage?.let { "منتخب ایپ" } ?: "ایس ایم ایس یا دستیاب پیغام رسانی ایپ"
            "پیغام تیار ہے۔ متن کی تصدیق کریں اور $destination میں وصول کنندہ منتخب کریں۔"
        } catch (_: Exception) {
            "پیغام کی اسکرین نہیں کھل سکی۔ ادراک دوبارہ کھول کر کوشش کریں۔"
        }
    }

    /**
     * null = not a message command; empty body = message command with missing text.
     */
    private fun extractRequest(command: String): MessageRequest? {
        val value = command.trim()
        val lower = value.lowercase()

        val markers = listOf(
            "whatsapp par message bhejo", "whatsapp pe message bhejo",
            "whatsapp par message bhej do", "whatsapp pe message bhej do",
            "whatsapp par msg bhejo", "whatsapp pe msg bhejo",
            "instagram par dm bhejo", "instagram pe dm bhejo",
            "instagram par dm bhej do", "instagram pe dm bhej do",
            "facebook par message bhejo", "facebook par message bhej do",
            "messenger par message bhejo", "messenger par message bhej do",
            "telegram par message bhejo", "telegram par message bhej do",
            "signal par message bhejo", "signal par message bhej do",
            "direct message bhejo", "chat message bhejo",
            "message send karo", "msg send karo",
            "send a whatsapp message", "send whatsapp message",
            "send a message on whatsapp", "send whatsapp msg",
            "send instagram message", "send instagram dm",
            "send a message on instagram", "send telegram message",
            "send a message on telegram", "send facebook message",
            "send messenger message", "send signal message",
            "message bhejo", "msg bhejo", "message kar do", "msg kar do",
            "dm bhejo", "dm karo", "send a message", "send message",
            "send a text", "text message", "send dm", "send a dm",
            "व्हाट्सएप पर मैसेज भेजो", "व्हाट्सएप पे मैसेज भेजो",
            "व्हाट्सएप पर मैसेज भेज दो", "इंस्टाग्राम पर मैसेज भेजो",
            "इंस्टाग्राम पर डीएम भेजो", "इंस्टा पर डीएम भेजो",
            "फेसबुक पर मैसेज भेजो", "मैसेंजर पर मैसेज भेजो",
            "मैसेज भेज दो", "मैसेज भेजो", "मैसेज कर दो",
            "संदेश भेजो", "डीएम भेजो", "चैट भेजो",
            "واٹس ایپ پر میسج بھیج دو", "واٹس ایپ پر میسج بھیجو",
            "واٹس ایپ پر میسج بھیجیں", "واٹس ایپ پہ میسج بھیجو",
            "واٹس ایپ پہ میسج بھیجیں", "واٹس ایپ پر پیغام بھیجو",
            "واٹس ایپ پر پیغام بھیجیں", "انسٹاگرام پر ڈی ایم بھیجو",
            "انسٹاگرام پر ڈی ایم بھیجیں", "انسٹاگرام پہ ڈی ایم بھیجو",
            "فیس بک پر پیغام بھیجو", "ٹیلیگرام پر پیغام بھیجو",
            "میسنجر پر پیغام بھیجو", "میسج بھیج دو", "میسج بھیجو",
            "میسج بھیجیں", "پیغام بھیج دو", "پیغام بھیجو", "پیغام بھیجیں"
        )

        val marker = markers
            .sortedByDescending { it.length }
            .firstOrNull { lower.contains(it) }
            ?: return null

        val index = lower.indexOf(marker) + marker.length
        var body = value.substring(index).trim()

        // Support compact spoken formats such as "send a WhatsApp message to Rahul: hello".
        // Only treat a colon as a recipient separator when the remaining text starts
        // with an explicit recipient preposition, so message text like "meet at 5:30"
        // is preserved unchanged.
        val hasRecipientPrefix = body.lowercase().startsWith("to ") ||
            body.lowercase().startsWith("for ") ||
            body.startsWith("को ") || body.startsWith("کے لیے ")
        val recipientSeparator = body.indexOf(':')
        if (hasRecipientPrefix && recipientSeparator >= 0 && recipientSeparator < body.lastIndex) {
            body = body.substring(recipientSeparator + 1).trim()
        }

        val bodyPrefixes = listOf(
            "ki ", "ke ", "that ", "saying ", "bolo ", "likho ", "to ",
            "کہہ دو ", "کہنا ", "کہ ", "متن یہ ہے ", "لکھیں کہ ",
            "कि ", "की ", "के ", "ये कि ", "यह कि ", "کہ ", "یہ کہ "
        )
        val prefix = bodyPrefixes.firstOrNull { body.lowercase().startsWith(it) }
        if (prefix != null) body = body.drop(prefix.length).trim()

        // Commands may include recipient/app details before the actual message,
        // e.g. "send a message to Rahul on WhatsApp saying hello".
        // Keep only the text after a clear spoken-content introducer.
        val lowerBody = body.lowercase()
        val contentIntroducers = listOf(
            " saying ", " that ", " message is ", " text is ",
            " لکھو ", " بولو ", " کہو ", " کہ "
        )
        val contentStart = contentIntroducers
            .map { introducer -> lowerBody.indexOf(introducer).let { index ->
                if (index >= 0) index to introducer.length else null
            } }
            .filterNotNull()
            .minByOrNull { it.first }
        if (contentStart != null) {
            body = body.substring(contentStart.first + contentStart.second).trim()
        }

        body = body.trim().trim(':', '-', '،', ',', '.', ' ')

        val targetPackage = when {
            listOf("whatsapp", "व्हाट्सएप", "واٹس ایپ").any { lower.contains(it) } ->
                "com.whatsapp"
            listOf("instagram", "انسٹاگرام", "انستا").any { lower.contains(it) } ->
                "com.instagram.android"
            listOf("messenger", "मैसेंजर").any { lower.contains(it) } ->
                "com.facebook.orca"
            listOf("facebook", "फेसबुक").any { lower.contains(it) } ->
                "com.facebook.katana"
            lower.contains("telegram") -> "org.telegram.messenger"
            lower.contains("signal") -> "org.thoughtcrime.securesms"
            else -> null
        }

        return MessageRequest(body = body, targetPackage = targetPackage)
    }
}
