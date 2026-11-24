package com.idraak.quantum

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.idraak.quantum.engine.CallController

/**
 * Safe SMS composition:
 * - User selects the recipient with Android's contact picker.
 * - Idraak shows the exact recipient and message for confirmation.
 * - Android's SMS app opens with the message filled in.
 * - The user presses Send manually; Idraak never sends silently.
 */
class MessageControllerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MESSAGE_BODY = "idraak.extra.MESSAGE_BODY"
    }

    private var messageBody: String = ""

    private val contactPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) {
                finish()
                return@registerForActivityResult
            }

            val selectedUri = result.data?.data
            val contactResult = CallController.readSelectedContact(this, selectedUri)
            val contact = contactResult.contact

            if (!contactResult.success || contact == null) {
                showMessage(contactResult.message)
                finish()
                return@registerForActivityResult
            }

            showMessageConfirmation(contact.name, contact.phoneNumber)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageBody = intent.getStringExtra(EXTRA_MESSAGE_BODY).orEmpty().trim()

        if (messageBody.isBlank()) {
            showMessage("मैसेज खाली है।")
            finish()
            return
        }

        try {
            contactPicker.launch(CallController.createContactPickerIntent())
        } catch (_: ActivityNotFoundException) {
            showMessage("फोन में Contact Picker उपलब्ध नहीं है।")
            finish()
        } catch (_: Exception) {
            showMessage("कॉन्टैक्ट चुनने की स्क्रीन नहीं खुल सकी।")
            finish()
        }
    }

    private fun showMessageConfirmation(name: String, number: String) {
        val confirmationText = listOf(
            "किसे मैसेज भेजना है:",
            name,
            number,
            "",
            "मैसेज:",
            messageBody,
            "",
            "हाँ चुनने पर SMS ऐप खुलेगा। मैसेज भेजने के लिए आपको वहाँ Send दबाना होगा।"
        ).joinToString("\n")

        AlertDialog.Builder(this)
            .setTitle("मैसेज की पुष्टि")
            .setMessage(confirmationText)
            .setPositiveButton("हाँ, SMS ऐप खोलो") { _, _ ->
                openSmsComposer(number)
            }
            .setNegativeButton("रद्द करें") { _, _ ->
                showMessage("मैसेज रद्द कर दिया गया।")
                finish()
            }
            .setOnCancelListener { finish() }
            .setCancelable(true)
            .show()
    }

    private fun openSmsComposer(number: String) {
        try {
            val smsIntent = Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts("smsto", number, null)
            ).apply {
                putExtra("sms_body", messageBody)
            }
            startActivity(smsIntent)
            finish()
        } catch (_: ActivityNotFoundException) {
            showMessage("फोन में SMS ऐप उपलब्ध नहीं है।")
            finish()
        } catch (_: Exception) {
            showMessage("SMS ऐप नहीं खुल सका।")
            finish()
        }
    }

    private fun showMessage(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
}
