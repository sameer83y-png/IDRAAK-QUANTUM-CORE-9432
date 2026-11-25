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
            showMessage("پیغام خالی ہے۔")
            finish()
            return
        }

        showDeliveryChoice()
    }

    private fun showDeliveryChoice() {
        val targetLabel = targetPackage?.let { appLabel(it) }
        val choices = if (targetPackage != null) {
            arrayOf("ایس ایم ایس", "$targetLabel میں بھیجیں", "کوئی دوسری موزوں ایپ")
        } else {
            arrayOf("ایس ایم ایس", "کوئی دوسری موزوں ایپ")
        }

        AlertDialog.Builder(this)
            .setTitle("پیغام کس طریقے سے بھیجنا ہے؟")
            .setItems(choices) { _, which ->
                when {
                    which == 0 -> openContactPicker()
                    targetPackage != null && which == 1 ->
                        showTargetAppConfirmation(targetLabel ?: "منتخب ایپ")
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
        else -> "منتخب ایپ"
    }

    private fun openContactPicker() {
        try {
            contactPicker.launch(CallController.createContactPickerIntent())
        } catch (_: ActivityNotFoundException) {
            showMessage("فون میں رابطہ منتخب کرنے کی سہولت دستیاب نہیں۔")
            finish()
        } catch (_: Exception) {
            showMessage("رابطہ منتخب کرنے کی اسکرین نہیں کھل سکی۔")
            finish()
        }
    }

    private fun showSmsConfirmation(name: String, number: String) {
        val confirmationText = listOf(
            "کسے پیغام بھیجنا ہے:",
            name,
            number,
            "",
            "پیغام:",
            messageBody,
            "",
            "تصدیق کے بعد ایس ایم ایس ایپ کھلے گی۔ بھیجنے کا آخری اختیار اسی ایپ میں ہوگا۔"
        ).joinToString("\n")

        AlertDialog.Builder(this)
            .setTitle("پیغام کی تصدیق")
            .setMessage(confirmationText)
            .setPositiveButton("ہاں، ایس ایم ایس ایپ کھولو") { _, _ ->
                openSmsComposer(number)
            }
            .setNegativeButton("منسوخ کریں") { _, _ ->
                showMessage("پیغام منسوخ کر دیا گیا ہے۔")
                finish()
            }
            .setOnCancelListener { finish() }
            .setCancelable(true)
            .show()
    }

    private fun showTargetAppConfirmation(label: String) {
        AlertDialog.Builder(this)
            .setTitle("$label میں پیغام بھیجیں؟")
            .setMessage(
                "پیغام کا متن:\n\n$messageBody\n\nتصدیق کے بعد $label کھلے گی۔ " +
                    "وہاں وصول کنندہ منتخب کریں اور بھیجنے سے پہلے جائزہ لیں۔ آخری اختیار اسی ایپ میں ہوگا۔"
            )
            .setPositiveButton("ہاں، $label کھولو") { _, _ ->
                openTargetApp()
            }
            .setNegativeButton("منسوخ کریں") { _, _ ->
                showMessage("پیغام منسوخ کر دیا گیا ہے۔")
                finish()
            }
            .setOnCancelListener { finish() }
            .setCancelable(true)
            .show()
    }

    private fun showShareConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("پیغام کی تصدیق")
            .setMessage(
                "یہ پیغام دستیاب ایپ میں شیئر کرنے کے لیے کھلے گا:\n\n" +
                    messageBody +
                    "\n\nفون دستیاب ایپس دکھائے گا۔ وصول کنندہ منتخب کرنا اور بھیجنا اسی ایپ کے اختیار میں ہوگا۔"
            )
            .setPositiveButton("ہاں، ایپ منتخب کریں") { _, _ ->
                openShareChooser()
            }
            .setNegativeButton("منسوخ کریں") { _, _ ->
                showMessage("پیغام منسوخ کر دیا گیا ہے۔")
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
            showMessage("فون میں ایس ایم ایس ایپ دستیاب نہیں۔")
            finish()
        } catch (_: Exception) {
            showMessage("ایس ایم ایس ایپ نہیں کھل سکی۔")
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
            showMessage("منتخب ایپ نہیں کھل سکی۔ کوئی دوسری موزوں ایپ منتخب کریں۔")
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
            startActivity(Intent.createChooser(shareIntent, "پیغام کے لیے موزوں ایپ منتخب کریں"))
            finish()
        } catch (_: ActivityNotFoundException) {
            showMessage("اس پیغام کے لیے کوئی موزوں ایپ دستیاب نہیں۔")
            finish()
        } catch (_: Exception) {
            showMessage("ایپ منتخب کرنے کی اسکرین نہیں کھل سکی۔")
            finish()
        }
    }

    private fun showMessage(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show()
    }
}
