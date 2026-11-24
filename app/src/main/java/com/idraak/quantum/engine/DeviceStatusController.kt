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
            "battery", "battery status", "battery level", "how much battery",
            "phone charge", "charging status", "is phone charging",
            "बैटरी", "बैटरी कितनी", "बैटरी कितनी है", "चार्ज कितना",
            "फोन कितना चार्ज", "चार्जिंग हो रही", "बैटरी स्टेटस",
            "بیٹری", "چارج کتنا", "بیٹری کتنی"
        )
        if (phrases.none { value.contains(it) }) return null

        return try {
            val intent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return Result(false, "बैटरी की जानकारी अभी उपलब्ध नहीं है।")

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) {
                return Result(false, "बैटरी प्रतिशत पढ़ा नहीं जा सका।")
            }

            val percent = (level * 100 / scale).coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val chargingText = if (charging) "फोन चार्ज हो रहा है।" else "फोन अभी चार्ज नहीं हो रहा है।"

            Result(true, "आपके फोन की बैटरी $percent% है। $chargingText")
        } catch (error: Exception) {
            Result(false, "बैटरी जानकारी में समस्या: " +
                (error.localizedMessage ?: "Unknown error"))
        }
    }
}
