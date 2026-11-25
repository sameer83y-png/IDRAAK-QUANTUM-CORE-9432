package com.idraak.quantum.engine

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens Android-owned device setting panels.
 *
 * Android deliberately prevents ordinary apps from silently toggling several
 * protected radios (Wi-Fi, Bluetooth, location and NFC). Idraak therefore
 * hands the user to the official local settings screen instead of pretending
 * that a restricted toggle succeeded. No network permission is needed.
 */
object DeviceSettingsCommandHandler {

    data class Result(
        val response: String,
        val requiresForegroundUi: Boolean,
        val intent: String
    )

    fun execute(
        context: Context,
        command: String,
        allowForegroundUi: Boolean
    ): Result? {
        val value = command.trim().lowercase()
        val target = when {
            containsAny(value, "وائی فائی", "وائیفائی", "wifi", "wi-fi") ->
                Triple(Settings.ACTION_WIFI_SETTINGS, "وائی فائی", "WIFI_SETTINGS")

            containsAny(value, "بلوٹوتھ", "bluetooth") ->
                Triple(Settings.ACTION_BLUETOOTH_SETTINGS, "بلوٹوتھ", "BLUETOOTH_SETTINGS")

            containsAny(value, "لوکیشن", "مقام", "location", "gps") ->
                Triple(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "مقام", "LOCATION_SETTINGS")

            containsAny(value, "این ایف سی", "nfc") ->
                Triple(Settings.ACTION_NFC_SETTINGS, "این ایف سی", "NFC_SETTINGS")

            containsAny(value, "ڈسپلے سیٹنگ", "اسکرین سیٹنگ", "display settings", "screen settings") ->
                Triple(Settings.ACTION_DISPLAY_SETTINGS, "اسکرین", "DISPLAY_SETTINGS")

            containsAny(value, "آواز کی سیٹنگ", "sound settings", "آڈیو سیٹنگ") ->
                Triple(Settings.ACTION_SOUND_SETTINGS, "آواز", "SOUND_SETTINGS")

            else -> return null
        }

        if (!allowForegroundUi) {
            return Result(
                response = "${target.second} کی ترتیبات کھولنے کے لیے ادراک کو سامنے لانا ضروری ہے۔",
                requiresForegroundUi = true,
                intent = target.third
            )
        }

        return try {
            val intent = Intent(target.first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                return Result(
                    response = "${target.second} کی ترتیبات اس فون پر دستیاب نہیں ہیں۔",
                    requiresForegroundUi = false,
                    intent = target.third
                )
            }
            context.applicationContext.startActivity(intent)
            Result(
                response = "${target.second} کی نظامی ترتیبات کھول دی گئی ہیں۔ محفوظ تبدیلی کے لیے وہاں اختیار منتخب کریں۔",
                requiresForegroundUi = false,
                intent = target.third
            )
        } catch (_: Exception) {
            Result(
                response = "${target.second} کی ترتیبات نہیں کھل سکیں۔ فون کی ترتیبات سے اسے کھولیں۔",
                requiresForegroundUi = false,
                intent = target.third
            )
        }
    }

    private fun containsAny(value: String, vararg phrases: String): Boolean =
        phrases.any { value.contains(it) }
}
