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
            return "मैसेज का पूरा टेक्स्ट बोलिए। उदाहरण: राहुल को मैसेज भेजो कि मैं घर आ रहा हूँ।"
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

    private fun extractMessageBody(command: String): String? {
        val value = command.trim()
        val lower = value.lowercase()

        // Generic phrases plus common app-specific ways people describe messaging.
        // The app chooser remains generic; these words do not guarantee that a
        // named app supports receiving shared text or automatic sending.
        val markers = listOf(
            "message bhejo", "msg bhejo", "message send karo", "msg send karo",
            "message kar do", "msg kar do", "dm bhejo", "dm karo",
            "direct message bhejo", "chat message bhejo",
            "send a message", "send message", "send a text", "text message",
            "send dm", "send a dm",
            "whatsapp par message bhejo", "whatsapp pe message bhejo",
            "instagram par dm bhejo", "instagram pe dm bhejo",
            "facebook par message bhejo", "messenger par message bhejo",
            "telegram par message bhejo", "signal par message bhejo",
            "मैसेज भेज दो", "मैसेज भेजो", "मैसेज कर दो",
            "संदेश भेजो", "डीएम भेजो", "चैट भेजो",
            "व्हाट्सएप पर मैसेज भेजो", "इंस्टाग्राम पर मैसेज भेजो",
            "फेसबुक पर मैसेज भेजो", "मैसेंजर पर मैसेज भेजो",
            "میسج بھیج دو", "میسج بھیجو", "پیغام بھیجو",
            "واٹس ایپ پر میسج بھیجو", "انسٹاگرام پر ڈی ایم بھیجو"
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
        return body.takeIf { it.isNotBlank() }
    }
}
