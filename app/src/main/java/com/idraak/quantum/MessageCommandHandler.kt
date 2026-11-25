package com.idraak.quantum

import android.content.Context
import android.content.Intent

/**
 * Detects offline message-composition commands and opens the local message flow.
 * Text is handed to Android's app chooser; recipient selection and sending remain
 * under the chosen messaging app's own controls.
 */
object MessageCommandHandler {

    fun tryHandle(context: Context, command: String): String? {
        val body = extractMessageBody(command) ?: return null
        if (body.isBlank()) {
            return "मैसेज किसे और क्या भेजना है, यह बताइए। उदाहरण: राहुल को मैसेज भेजो कि मैं घर आ रहा हूँ।"
        }

        return try {
            val intent = Intent(context, MessageControllerActivity::class.java).apply {
                putExtra(MessageControllerActivity.EXTRA_MESSAGE_BODY, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "मैसेज तैयार है। पहले टेक्स्ट की पुष्टि करें, फिर SMS या उपलब्ध मैसेजिंग ऐप चुनें।"
        } catch (_: Exception) {
            "मैसेज स्क्रीन नहीं खुल सकी। कृपया Idraak को दोबारा खोलकर कोशिश करें।"
        }
    }

    /**
     * Returns null only when this is not a message command.
     * An empty string means the user asked to send a message but did not dictate
     * its body yet, so the router can ask for the missing information instead
     * of silently treating the command as unknown.
     */
    private fun extractMessageBody(command: String): String? {
        val value = command.trim()
        val lower = value.lowercase()

        val markers = listOf(
            "whatsapp par message bhejo", "whatsapp pe message bhejo",
            "instagram par dm bhejo", "instagram pe dm bhejo",
            "facebook par message bhejo", "messenger par message bhejo",
            "telegram par message bhejo", "signal par message bhejo",
            "direct message bhejo", "chat message bhejo",
            "message send karo", "msg send karo",
            "message bhejo", "msg bhejo", "message kar do", "msg kar do",
            "dm bhejo", "dm karo", "send a message", "send message",
            "send a text", "text message", "send dm", "send a dm",
            "व्हाट्सएप पर मैसेज भेजो", "इंस्टाग्राम पर मैसेज भेजो",
            "फेसबुक पर मैसेज भेजो", "मैसेंजर पर मैसेज भेजो",
            "मैसेज भेज दो", "मैसेज भेजो", "मैसेज कर दो",
            "संदेश भेजो", "डीएम भेजो", "चैट भेजो",
            "واٹس ایپ پر میسج بھیجو", "انسٹاگرام پر ڈی ایم بھیجو",
            "میسج بھیج دو", "میسج بھیجو", "پیغام بھیجو"
        )

        val marker = markers
            .sortedByDescending { it.length }
            .firstOrNull { lower.contains(it) }
            ?: return null

        val index = lower.indexOf(marker) + marker.length
        var body = value.substring(index).trim()

        val bodyPrefixes = listOf(
            "ki ", "ke ", "that ", "saying ", "bolo ", "likho ", "to ",
            "कि ", "की ", "के ", "ये कि ", "यह कि ", "کہ ", "یہ کہ "
        )
        val prefix = bodyPrefixes.firstOrNull { body.lowercase().startsWith(it) }
        if (prefix != null) body = body.drop(prefix.length).trim()

        body = body.trim().trim(':', '-', '،', ',', '.', ' ')
        return body
    }
}
