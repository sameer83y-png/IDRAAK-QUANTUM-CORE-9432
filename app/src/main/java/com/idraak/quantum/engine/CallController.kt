
package com.idraak.quantum.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract

/**
 * Idraak Call Controller
 *
 * Safe calling flow:
 * 1. Opens the phone contact picker.
 * 2. Reads the phone number selected by the user.
 * 3. Opens the system dialer.
 * 4. The user presses the Call button manually.
 *
 * This controller never places a call automatically.
 * No READ_CONTACTS or CALL_PHONE permission is required.
 */
object CallController {

    data class SelectedContact(
        val name: String,
        val phoneNumber: String
    )

    data class Result(
        val success: Boolean,
        val message: String,
        val contact: SelectedContact? = null
    )

    /**
     * Create an intent to let the user select a phone contact.
     *
     * Launch this intent from an Activity using
     * an Activity Result launcher.
     */
    fun createContactPickerIntent(): Intent {
        return Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
    }

    /**
     * Read the phone number from the URI returned by
     * the contact picker.
     */
    fun readSelectedContact(
        context: Context,
        selectedUri: Uri?
    ): Result {

        if (selectedUri == null) {
            return Result(
                success = false,
                message = "कोई कॉन्टैक्ट नहीं चुना गया।"
            )
        }

        var cursor: Cursor? = null

        return try {
            cursor = context.contentResolver.query(
                selectedUri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )

            if (cursor == null || !cursor.moveToFirst()) {
                return Result(
                    success = false,
                    message = "चुने गए कॉन्टैक्ट का नंबर नहीं मिला।"
                )
            }

            val nameIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val numberIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            if (numberIndex < 0) {
                return Result(
                    success = false,
                    message = "कॉन्टैक्ट का फोन नंबर उपलब्ध नहीं है।"
                )
            }

            val name = if (nameIndex >= 0) {
                cursor.getString(nameIndex).orEmpty()
            } else {
                "Unknown Contact"
            }

            val number = cursor.getString(numberIndex).orEmpty()

            if (!isValidPhoneNumber(number)) {
                return Result(
                    success = false,
                    message = "इस कॉन्टैक्ट का फोन नंबर सही नहीं है।"
                )
            }

            Result(
                success = true,
                message = "$name का नंबर मिल गया है।",
                contact = SelectedContact(
                    name = name,
                    phoneNumber = number.trim()
                )
            )

        } catch (_: SecurityException) {
            Result(
                success = false,
                message = "कॉन्टैक्ट पढ़ने की अनुमति उपलब्ध नहीं है। दोबारा चुनें।"
            )
        } catch (error: Exception) {
            Result(
                success = false,
                message = "कॉन्टैक्ट पढ़ने में समस्या आई: " +
                    (error.localizedMessage ?: "Unknown error")
            )
        } finally {
            try {
                cursor?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Open the system dialer with the selected number.
     *
     * The user must press the Call button manually.
     */
    fun openDialer(
        context: Context,
        phoneNumber: String
    ): Result {

        val number = phoneNumber.trim()

        if (!isValidPhoneNumber(number)) {
            return Result(
                success = false,
                message = "फोन नंबर सही नहीं है।"
            )
        }

        return try {
            val dialIntent = Intent(
                Intent.ACTION_DIAL,
                Uri.fromParts("tel", number, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(dialIntent)

            Result(
                success = true,
                message = "Dialer खुल गया है। कॉल करने से पहले नंबर जाँच लें।"
            )

        } catch (_: ActivityNotFoundException) {
            Result(
                success = false,
                message = "आपके फोन में Dialer उपलब्ध नहीं है।"
            )
        } catch (error: Exception) {
            Result(
                success = false,
                message = "Dialer खोलने में समस्या आई: " +
                    (error.localizedMessage ?: "Unknown error")
            )
        }
    }

    /**
     * Basic phone-number validation.
     */
    private fun isValidPhoneNumber(value: String): Boolean {
        val cleaned = value.trim()

        if (cleaned.isBlank()) return false

        if (!cleaned.matches(Regex("[0-9+*#() .-]+"))) {
            return false
        }

        val digitCount = cleaned.count { it.isDigit() }

        return digitCount in 3..20
    }
}
