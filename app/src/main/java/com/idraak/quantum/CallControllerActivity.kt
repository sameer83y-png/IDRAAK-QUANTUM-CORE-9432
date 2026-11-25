package com.idraak.quantum

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.idraak.quantum.engine.CallController

class CallControllerActivity : ComponentActivity() {

    private val contactPicker =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != RESULT_OK) {
                finish()
                return@registerForActivityResult
            }

            val selectedUri = result.data?.data

            val contactResult =
                CallController.readSelectedContact(
                    context = this,
                    selectedUri = selectedUri
                )

            val contact = contactResult.contact

            if (!contactResult.success || contact == null) {
                showMessage(contactResult.message)
                finish()
                return@registerForActivityResult
            }

            showCallConfirmation(
                name = contact.name,
                number = contact.phoneNumber
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            contactPicker.launch(
                CallController.createContactPickerIntent()
            )
        } catch (_: ActivityNotFoundException) {
            showMessage("آپ کے فون میں رابطہ منتخب کرنے کی سہولت دستیاب نہیں۔")
            finish()
        } catch (error: Exception) {
            showMessage(
                "رابطہ منتخب کرنے کی اسکرین نہیں کھل سکی: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
            )
            finish()
        }
    }

    private fun showCallConfirmation(
        name: String,
        number: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("رابطے کی تصدیق")
            .setMessage(
                "کیا آپ اس رابطے کا نمبر فون ایپ میں کھولنا چاہتے ہیں؟\n\n" +
                    "نام: $name\n" +
                    "نمبر: $number\n\n" +
                    "کال سے پہلے آپ فون ایپ میں نمبر دیکھ سکتے ہیں۔"
            )
            .setPositiveButton("فون ایپ کھولیں") { _, _ ->

                val result = CallController.openDialer(
                    context = this,
                    phoneNumber = number
                )

                showMessage(result.message)
                finish()
            }
            .setNegativeButton("منسوخ کریں") { _, _ ->
                showMessage("کال کا عمل منسوخ کر دیا گیا ہے۔")
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .setCancelable(true)
            .show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
