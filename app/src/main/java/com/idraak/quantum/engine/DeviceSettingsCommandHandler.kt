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
            containsAny(value, "وائی فائی", "وائیفائی", "وائی فائی کی سیٹنگ", "وائی فائی کی ترتیبات", "وائی فائی کی سیٹنگ کھولو", "وائی فائی دکھاؤ",
                "wifi", "wi-fi", "إعدادات الواي فاي", "اعدادات الواي فاي", "افتح إعدادات الواي فاي") ->
                Triple(Settings.ACTION_WIFI_SETTINGS, "وائی فائی", "WIFI_SETTINGS")

            containsAny(value, "بلوٹوتھ", "بلوٹوتھ کی سیٹنگ کھولو", "بلوٹوتھ کی ترتیبات کھولو", "bluetooth", "إعدادات البلوتوث", "اعدادات البلوتوث", "افتح إعدادات البلوتوث") ->
                Triple(Settings.ACTION_BLUETOOTH_SETTINGS, "بلوٹوتھ", "BLUETOOTH_SETTINGS")

            containsAny(value, "لوکیشن", "مقام", "مقام کی ترتیبات", "location", "gps",
                "إعدادات الموقع", "اعدادات الموقع", "افتح إعدادات الموقع", "إعدادات GPS") ->
                Triple(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "مقام", "LOCATION_SETTINGS")

            containsAny(value, "این ایف سی", "این ایف سی کی ترتیبات", "nfc",
                "إعدادات NFC", "اعدادات NFC", "افتح إعدادات NFC") ->
                Triple(Settings.ACTION_NFC_SETTINGS, "این ایف سی", "NFC_SETTINGS")

            containsAny(value, "ڈسپلے سیٹنگ", "اسکرین سیٹنگ", "اسکرین کی ترتیبات",
                "display settings", "screen settings",
                "إعدادات الشاشة", "اعدادات الشاشة", "إعدادات العرض", "افتح إعدادات الشاشة") ->
                Triple(Settings.ACTION_DISPLAY_SETTINGS, "اسکرین", "DISPLAY_SETTINGS")

            containsAny(value, "آواز کی سیٹنگ", "آواز کی ترتیبات", "sound settings", "آڈیو سیٹنگ",
                "إعدادات الصوت", "اعدادات الصوت", "افتح إعدادات الصوت") ->
                Triple(Settings.ACTION_SOUND_SETTINGS, "آواز", "SOUND_SETTINGS")

            containsAny(value, "airplane mode", "flight mode", "aeroplane mode",
                "ہوائی جہاز موڈ", "فلائٹ موڈ", "ہوائی جہاز کی ترتیبات",
                "हवाई जहाज मोड", "एयरप्लेन मोड",
                "وضع الطيران", "إعدادات وضع الطيران") ->
                Triple(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "ہوائی جہاز موڈ", "AIRPLANE_MODE_SETTINGS")

            containsAny(value, "battery saver", "battery saving mode", "power saving mode",
                "بیٹری سیور", "بیٹری بچت موڈ", "توانائی کی بچت",
                "बैटरी सेवर", "बैटरी बचत मोड",
                "توفير الطاقة", "وضع توفير الطاقة") ->
                Triple(Settings.ACTION_BATTERY_SAVER_SETTINGS, "بیٹری سیور", "BATTERY_SAVER_SETTINGS")

            containsAny(value, "accessibility settings", "accessibility options",
                "رسائی کی ترتیبات", "اسکرین ریڈر کی ترتیبات",
                "एक्सेसिबिलिटी सेटिंग", "सुलभता सेटिंग",
                "إعدادات إمكانية الوصول", "إمكانية الوصول") ->
                Triple(Settings.ACTION_ACCESSIBILITY_SETTINGS, "رسائی کی ترتیبات", "ACCESSIBILITY_SETTINGS")

            containsAny(value, "app settings", "apps settings", "installed apps", "application settings",
                "ایپس کی ترتیبات", "ایپ کی ترتیبات", "نصب شدہ ایپس",
                "ऐप सेटिंग", "ऐप्स की सेटिंग", "इंस्टॉल ऐप",
                "إعدادات التطبيقات", "التطبيقات المثبتة") ->
                Triple(Settings.ACTION_APPLICATION_SETTINGS, "ایپس", "APPLICATION_SETTINGS")

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
