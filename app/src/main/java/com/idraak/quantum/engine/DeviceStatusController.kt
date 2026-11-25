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

    fun executeBatteryCommand(context: Context, command: String): Result? {
        val value = command.trim().lowercase(Locale.ROOT)
        val phrases = listOf(
            "بیٹری", "بیٹری کتنی ہے", "بیٹری کا حال بتاؤ",
            "چارج کتنا", "چارج کتنے فیصد ہے", "چارجنگ کی حالت بتاؤ",
            "فون چارج ہو رہا ہے", "کیا فون چارج ہو رہا ہے",
            "چارجر لگا ہے", "چارجر کی حالت بتاؤ"
        )
        if (phrases.none { value.contains(it) }) return null

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
            val chargingText = if (charging) "فون چارج ہو رہا ہے۔" else "فون اس وقت چارج نہیں ہو رہا۔"

            Result(true, "آپ کے فون کی بیٹری $percent فیصد ہے۔ $chargingText")
        } catch (error: Exception) {
            Result(false, "بیٹری کی معلومات میں مسئلہ: " +
                (error.localizedMessage ?: "نامعلوم خرابی"))
        }
    }
}
