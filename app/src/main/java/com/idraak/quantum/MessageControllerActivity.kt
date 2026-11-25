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
 * Safe local message composition:
 * - The user chooses SMS or another installed sharing app (such as WhatsApp).
 * - SMS recipient is selected with Android's contact picker and confirmed.
 * - WhatsApp/share text is handed to Android's app chooser.
 * - Idraak never sends a message silently.
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

            showSmsConfirmation(contact.name, contact.phoneNumber)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        messageBody = intent.getStringExtra(EXTRA_MESSAGE_BODY).orEmpty().trim()

        if (messageBody.isBlank()) {
            showMessage("मैसेज खाली है।")
            finish()
            return
        }

        showDeliveryChoice()
    }

    private fun showDeliveryChoice() {
        AlertDialog.Builder(this)
            .setTitle("मैसेज किस ऐप से तैयार करना है?")
            .setItems(arrayOf("SMS", "WhatsApp / दूसरा ऐप")) { _, which ->
                when (which) {
                    0 -> openContactPicker()
                    1 -> openShareChooser()
                    else -> finish()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun openContactPicker() {
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

    private fun showSmsConfirmation(name: String, number: String) {
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

    private fun openShareChooser() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageBody)
            }
            startActivity(Intent.createChooser(shareIntent, "मैसेज भेजने के लिए ऐप चुनें"))
            finish()
        } catch (_: ActivityNotFoundException) {
            showMessage("मैसेज शेयर करने के लिए कोई ऐप उपलब्ध नहीं है।")
            finish()
        } catch (_: Exception) {
            showMessage("शेयर स्क्रीन नहीं खुल सकी।")
            finish()
        }
    }

    private fun showMessage(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
}
