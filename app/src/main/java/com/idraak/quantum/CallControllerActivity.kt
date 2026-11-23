
package com.idraak.quantum

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.idraak.quantum.engine.CallController

class CallControllerActivity : Activity() {

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
            showMessage("आपके फोन में Contact Picker उपलब्ध नहीं है।")
            finish()
        } catch (error: Exception) {
            showMessage(
                "Contact Picker नहीं खुल सका: " +
                    (error.localizedMessage ?: "Unknown error")
            )
            finish()
        }
    }

    private fun showCallConfirmation(
        name: String,
        number: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("Confirm Contact")
            .setMessage(
                "क्या आप इस कॉन्टैक्ट का नंबर Dialer में खोलना चाहते हैं?\n\n" +
                    "नाम: $name\n" +
                    "नंबर: $number\n\n" +
                    "कॉल करने से पहले आप Dialer में नंबर जाँच सकते हैं।"
            )
            .setPositiveButton("OPEN DIALER") { _, _ ->

                val result = CallController.openDialer(
                    context = this,
                    phoneNumber = number
                )

                showMessage(result.message)
                finish()
            }
            .setNegativeButton("CANCEL") { _, _ ->
                showMessage("कॉल करने की प्रक्रिया रद्द कर दी गई।")
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
