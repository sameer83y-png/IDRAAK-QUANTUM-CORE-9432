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
 * Offline message hand-off for SMS and installed apps that accept text sharing.
 * Each selected app controls recipient selection and the final send action.
 */
class MessageControllerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MESSAGE_BODY = "idraak.extra.MESSAGE_BODY"
        const val EXTRA_TARGET_PACKAGE = "idraak.extra.TARGET_PACKAGE"
    }

    private var messageBody: String = ""
    private var targetPackage: String? = null

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
        targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE)

        if (messageBody.isBlank()) {
            showMessage("मैसेज खाली है।")
            finish()
            return
        }

        showDeliveryChoice()
    }

    private fun showDeliveryChoice() {
        val targetLabel = targetPackage?.let { appLabel(it) }
        val choices = if (targetPackage != null) {
            arrayOf("SMS", "$targetLabel में भेजें", "कोई समर्थित दूसरा ऐप")
        } else {
            arrayOf("SMS", "कोई समर्थित दूसरा ऐप")
        }

        AlertDialog.Builder(this)
            .setTitle("मैसेज किस तरीके से भेजना है?")
            .setItems(choices) { _, which ->
                when {
                    which == 0 -> openContactPicker()
                    targetPackage != null && which == 1 ->
                        showTargetAppConfirmation(targetLabel ?: "चुने हुए ऐप")
                    else -> showShareConfirmation()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun appLabel(packageName: String): String = when (packageName) {
        "com.whatsapp" -> "WhatsApp"
        "com.instagram.android" -> "Instagram"
        "com.facebook.orca" -> "Messenger"
        "com.facebook.katana" -> "Facebook"
        "org.telegram.messenger" -> "Telegram"
        "org.thoughtcrime.securesms" -> "Signal"
        else -> "चुने हुए ऐप"
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

    private fun showTargetAppConfirmation(label: String) {
        AlertDialog.Builder(this)
            .setTitle("$label में मैसेज भेजें?")
            .setMessage(
                "मैसेज का टेक्स्ट:\n\n$messageBody\n\nपुष्टि के बाद $label खुलेगा। " +
                    "वहाँ प्राप्तकर्ता चुनें और भेजने से पहले जाँच लें। अंतिम Send नियंत्रण उसी ऐप में रहेगा।"
            )
            .setPositiveButton("हाँ, $label खोलो") { _, _ ->
                openTargetApp()
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
                    "\n\nAndroid उपलब्ध ऐप दिखाएगा। प्राप्तकर्ता चुनना और भेजना उस ऐप के नियंत्रण में रहेगा।"
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
     * Try the explicitly requested app first. If it is missing or does not
     * accept ACTION_SEND text, fall back to Android's generic share chooser.
     */
    private fun openTargetApp() {
        val packageName = targetPackage
        if (packageName == null) {
            openShareChooser()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageBody)
                setPackage(packageName)
            }
            startActivity(intent)
            finish()
        } catch (_: ActivityNotFoundException) {
            showMessage(appLabel(packageName) + " में टेक्स्ट शेयरिंग उपलब्ध नहीं मिली। दूसरे समर्थित ऐप चुनें।")
            openShareChooser()
        } catch (_: Exception) {
            showMessage("चुना हुआ ऐप नहीं खुल सका। दूसरे समर्थित ऐप चुनें।")
            openShareChooser()
        }
    }

    /**
     * ACTION_SEND discovers installed apps that declare support for plain-text
     * sharing. A share target is not guaranteed to provide direct messaging.
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
