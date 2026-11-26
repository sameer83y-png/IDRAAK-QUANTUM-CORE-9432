package com.idraak.quantum.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale

/**
 * Reads basic device battery status locally.
 * Does not request network access or collect data outside the device.
 */
object DeviceStatusController {

    data class Result(
        val success: Boolean,
        val message: String
    )

    /**
     * Matches common battery and charging questions across Urdu, Roman Urdu,
     * Hindi, English and Arabic. Kept separate for context-free regression tests.
     */
    internal fun isBatteryStatusCommand(command: String): Boolean {
        val value = normalize(command)
        if (value.isBlank()) return false

        val phrases = listOf(
            // Urdu
            "بیٹری", "بیٹری کتنی ہے", "بیٹری کا حال بتاؤ", "فون کی بیٹری بتاؤ",
            "کتنی بیٹری باقی ہے", "چارج کتنے فیصد رہ گیا ہے", "چارج کتنا",
            "چارج کتنے فیصد ہے", "چارجنگ کی حالت بتاؤ", "فون چارج ہو رہا ہے",
            "کیا فون چارج ہو رہا ہے", "کیا چارجر لگا ہوا ہے", "چارجر لگا ہے یا نہیں",
            "چارجر لگا ہے", "چارجر کی حالت بتاؤ", "بیٹری کتنے فیصد ہے",
            "بیٹری کتنے فیصد باقی ہے",
            // Roman Urdu
            "battery kitni hai", "battery kitni baqi hai", "battery kitni bachi hai",
            "phone ki battery batao", "battery ka haal batao", "kitni battery hai",
            "kitna charge hai", "charge kitna hai", "charge kitne percent hai",
            "phone charge ho raha hai", "kya phone charge ho raha hai",
            "charger laga hai", "charger laga hua hai", "charging ho rahi hai",
            "charging status batao",
            // English
            "battery", "battery level", "battery percentage", "how much battery",
            "how much charge", "is phone charging", "is it charging",
            "charging status", "is the charger connected",
            // Hindi
            "बैटरी कितनी है", "बैटरी कितनी बची है", "बैटरी प्रतिशत",
            "बैटरी कितने प्रतिशत है", "फोन की बैटरी बताओ",
            "फोन चार्ज हो रहा है", "क्या फोन चार्ज हो रहा है",
            "चार्जिंग की स्थिति", "चार्ज कितना है", "चार्जर लगा है या नहीं",
            // Arabic
            "كم نسبة البطارية", "كم شحن البطارية", "حالة البطارية",
            "هل الهاتف يشحن", "هل الهاتف قيد الشحن", "هل الشاحن متصل"
        )
        return phrases.any { value.contains(normalize(it)) }
    }

    fun executeBatteryCommand(context: Context, command: String): Result? {
        if (!isBatteryStatusCommand(command)) return null

        return try {
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return Result(false, "بیٹری کی معلومات ابھی دستیاب نہیں۔")

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) {
                return Result(false, "بیٹری کا فیصد معلوم نہیں ہو سکا۔")
            }

            val percent = (level * 100 / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val chargingText = if (charging) {
                "فون چارج ہو رہا ہے۔"
            } else {
                "فون اس وقت چارج نہیں ہو رہا۔"
            }

            Result(true, "آپ کے فون کی بیٹری $percent فیصد ہے۔ $chargingText")
        } catch (error: Exception) {
            Result(false, "بیٹری کی معلومات میں مسئلہ: " +
                (error.localizedMessage ?: "نامعلوم خرابی"))
        }
    }

    private fun normalize(value: String): String {
        return value.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
