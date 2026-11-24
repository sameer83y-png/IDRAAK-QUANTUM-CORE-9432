package com.idraak.quantum

import android.content.Context
import android.content.Intent

/**
 * Routes spoken message requests into Idraak's local message-composition flow.
 * No message is sent silently. Android's SMS composer is opened only after
 * the user confirms the recipient and message.
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
            "मैसेज तैयार करने के लिए कॉन्टैक्ट चुनिए। भेजने से पहले नाम और मैसेज की पुष्टि होगी।"
        } catch (_: Exception) {
            "मैसेज स्क्रीन नहीं खुल सकी। कृपया Idraak को दोबारा खोलकर कोशिश करें।"
        }
    }

    private fun extractMessageBody(command: String): String? {
        val value = command.trim()
        val lower = value.lowercase()

        val markers = listOf(
            "message bhejo", "msg bhejo", "message send karo", "msg send karo",
            "send a message", "send message", "text message",
            "मैसेज भेज दो", "मैसेज भेजो", "संदेश भेजो",
            "میسج بھیج دو", "میسج بھیجو", "پیغام بھیجو"
        )

        val marker = markers.firstOrNull { lower.contains(it) } ?: return null
        val index = lower.indexOf(marker) + marker.length
        var body = value.substring(index).trim()

        val bodyPrefixes = listOf(
            "ki ", "ke ", "that ", "saying ", "bolo ", "likho ",
            "कि ", "की ", "के ", "ये कि ", "یہ کہ ", "کہ "
        )
        val prefix = bodyPrefixes.firstOrNull { body.lowercase().startsWith(it) }
        if (prefix != null) body = body.drop(prefix.length).trim()

        body = body.trim().trim(':', '-', '،', ',', '.', ' ')
        return body.takeIf { it.isNotBlank() }
    }
}
