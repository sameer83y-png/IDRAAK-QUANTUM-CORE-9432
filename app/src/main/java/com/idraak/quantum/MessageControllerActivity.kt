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
 * Offline message hand-off for SMS and any installed app that accepts text sharing.
 *
 * Android and third-party apps control their own recipient and send flows. Idraak
 * never attempts to bypass those app controls or silently send a message.
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
            .setTitle("मैसेज किस तरीके से भेजना है?")
            .setItems(arrayOf("SMS", "कोई समर्थित दूसरा ऐप")) { _, which ->
                when (which) {
                    0 -> openContactPicker()
                    1 -> showShareConfirmation()
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
            "पुष्टि के बाद SMS ऐप खुलेगा। भेजने का अंतिम नियंत्रण SMS ऐप में रहेगा।"
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

    private fun showShareConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("मैसेज की पुष्टि")
            .setMessage(
                "यह मैसेज समर्थित ऐप में साझा करने के लिए खुलेगा:\n\n" +
                    messageBody +
                    "\n\nआगे बढ़ने पर Android उपलब्ध ऐप दिखाएगा। प्राप्तकर्ता चुनना और भेजना उस ऐप के नियंत्रण में रहेगा।"
            )
            .setPositiveButton("हाँ, ऐप चुनो") { _, _ ->
                openShareChooser()
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

    /**
     * ACTION_SEND discovers installed apps that declare support for plain-text
     * sharing. This can include messaging apps, but each app decides what it
     * does with the shared text and whether a recipient/send action is available.
     */
    private fun openShareChooser() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageBody)
            }
            startActivity(Intent.createChooser(shareIntent, "मैसेज के लिए समर्थित ऐप चुनें"))
            finish()
        } catch (_: ActivityNotFoundException) {
            showMessage("इस मैसेज के लिए कोई समर्थित ऐप उपलब्ध नहीं है।")
            finish()
        } catch (_: Exception) {
            showMessage("ऐप चुनने की स्क्रीन नहीं खुल सकी।")
            finish()
        }
    }

    private fun showMessage(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
}
