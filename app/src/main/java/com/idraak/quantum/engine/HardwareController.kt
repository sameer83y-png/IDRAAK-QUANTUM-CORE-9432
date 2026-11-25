
package com.idraak.quantum.engine

import android.Manifest
import android.media.AudioManager
import android.provider.Settings
import java.util.Locale
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat

/**
 * Idraak Hardware Controller
 *
 * Handles supported phone hardware operations.
 * Current implementation: camera flashlight.
 *
 * CAMERA permission must be granted by the user
 * before the torch can be controlled.
 */
object HardwareController {

    data class Result(
        val success: Boolean,
        val message: String,
        val permissionRequired: Boolean = false
    )

    /**
     * Controls the phone media volume using Android's AudioManager.
     * Commands are local and never require network access.
     */
    fun executeVolumeCommand(
        context: Context,
        command: String
    ): Result? {
        val value = command.trim().lowercase(Locale.ROOT)
        val audioManager = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Result(false, "فون کا آواز کنٹرول دستیاب نہیں۔")

        val stream = AudioManager.STREAM_MUSIC
        val current = audioManager.getStreamVolume(stream)
        val maximum = audioManager.getStreamMaxVolume(stream)

        return try {
            when {
                listOf("volume up", "increase volume", "आवाज बढ़ाओ", "वॉल्यूम बढ़ाओ", "आवाज़ तेज करो", "volume badhao", "volume tez karo", "awaz badhao", "awaaz tez karo", "آواز بڑھاؤ", "والیوم بڑھاؤ", "آواز تیز کرو", "آواز اونچی کرو", "آواز بڑھا دو",
                    "ارفع الصوت", "زد الصوت", "علي الصوت", "ارفع مستوى الصوت")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                    Result(true, "میڈیا کی آواز بڑھا دی گئی ہے۔")
                }

                listOf("volume down", "decrease volume", "आवाज कम करो", "वॉल्यूम कम करो", "आवाज़ धीमी करो", "volume kam karo", "awaz kam karo", "awaaz dheemi karo", "آواز کم کرو", "والیوم کم کرو", "آواز آہستہ کرو", "آواز کم کر دو",
                    "اخفض الصوت", "خفض الصوت", "قلل الصوت", "اخفض مستوى الصوت")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                    Result(true, "میڈیا کی آواز کم کر دی گئی ہے۔")
                }

                listOf("mute volume", "mute sound", "आवाज बंद करो", "वॉल्यूम म्यूट करो", "आवाज़ म्यूट", "awaz mute karo", "awaaz band karo", "آواز خاموش کرو", "والیوم میوٹ کرو", "آواز بند کر دو", "خاموش کر دو",
                    "اكتم الصوت", "كتم الصوت", "اجعل الصوت صامتا")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                    Result(true, "میڈیا کی آواز خاموش کر دی گئی ہے۔")
                }

                listOf("unmute volume", "sound on", "आवाज चालू करो", "वॉल्यूम अनम्यूट", "awaz chalu karo", "awaaz on karo", "آواز کھولو", "والیوم اَن میوٹ کرو", "آواز بحال کرو", "خاموشی ختم کرو",
                    "ألغ كتم الصوت", "أعد الصوت", "شغل الصوت")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    Result(true, "میڈیا کی آواز بحال کر دی گئی ہے۔")
                }

                listOf("maximum volume", "volume full", "आवाज फुल करो", "वॉल्यूम फुल करो", "volume maximum karo", "awaz full karo", "آواز پوری کرو", "والیوم فل کرو", "آواز زیادہ سے زیادہ کرو",
                    "ارفع الصوت للحد الأقصى", "أقصى مستوى للصوت", "اجعل الصوت بأقصى مستوى")
                    .any { value.contains(it) } -> {
                    audioManager.setStreamVolume(stream, maximum, 0)
                    Result(true, "میڈیا کی آواز زیادہ سے زیادہ کر دی گئی ہے۔")
                }

                listOf("minimum volume", "volume zero", "आवाज बिल्कुल कम करो", "volume zero karo", "awaz bilkul kam karo", "آواز بالکل کم کرو", "آواز صفر کرو",
                    "اخفض الصوت إلى الصفر", "اجعل الصوت صفرا", "الصوت عند الصفر")
                    .any { value.contains(it) } -> {
                    audioManager.setStreamVolume(stream, 0, 0)
                    Result(true, "میڈیا کی آواز صفر کر دی گئی ہے۔")
                }

                else -> null
            }
        } catch (_: SecurityException) {
            Result(false, "فون نے آواز تبدیل کرنے کی اجازت نہیں دی۔")
        } catch (error: Exception) {
            Result(false, "آواز کنٹرول کرنے میں مسئلہ: " +
                (error.localizedMessage ?: "نامعلوم خرابی"))
        }
    }


    /**
     * Changes screen brightness through Android's system settings.
     * WRITE_SETTINGS is a special user-granted permission, not a normal runtime permission.
     */
    fun executeBrightnessCommand(
        context: Context,
        command: String
    ): Result? {
        val value = command.trim().lowercase(Locale.ROOT)
        val action = when {
            listOf("brightness up", "increase brightness", "screen brighter", "चमक बढ़ाओ", "ब्राइटनेस बढ़ाओ", "रोशनी बढ़ाओ", "brightness badhao", "screen bright karo", "roshni badhao", "روشنی بڑھاؤ", "برائٹنس بڑھاؤ", "روشنی بڑھا دو",
                "ارفع السطوع", "زد السطوع", "ارفع سطوع الشاشة")
                .any { value.contains(it) } -> "UP"
            listOf("brightness down", "decrease brightness", "screen dim", "चमक कम करो", "ब्राइटनेस कम करो", "रोशनी कम करो", "brightness kam karo", "screen dim karo", "roshni kam karo", "روشنی کم کرو", "برائٹنس کم کرو", "روشنی کم کر دو",
                "اخفض السطوع", "خفض السطوع", "قلل سطوع الشاشة")
                .any { value.contains(it) } -> "DOWN"
            listOf("maximum brightness", "brightness full", "चमक पूरी करो", "ब्राइटनेस फुल करो", "brightness full karo", "roshni full karo", "روشنی پوری کرو", "برائٹنس فل کرو", "روشنی زیادہ سے زیادہ کرو",
                "أقصى سطوع", "ارفع السطوع للحد الأقصى")
                .any { value.contains(it) } -> "MAX"
            listOf("minimum brightness", "brightness minimum", "चमक सबसे कम करो", "ब्राइटनेस जीरो करो", "brightness minimum karo", "roshni bilkul kam karo", "روشنی کم سے کم کرو", "برائٹنس زیرو کرو", "روشنی بالکل کم کرو",
                "أدنى سطوع", "اخفض السطوع إلى الصفر")
                .any { value.contains(it) } -> "MIN"
            else -> return null
        }

        if (!Settings.System.canWrite(context)) {
            return Result(
                success = false,
                message = "اسکرین کی روشنی بدلنے کے لیے نظام کی ترتیبات میں تبدیلی کی اجازت درکار ہے۔",
                permissionRequired = true
            )
        }

        return try {
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            ).coerceIn(0, 255)
            val next = when (action) {
                "UP" -> (current + 26).coerceAtMost(255)
                "DOWN" -> (current - 26).coerceAtLeast(0)
                "MAX" -> 255
                else -> 0
            }

            val brightnessSaved = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                next
            )
            if (!brightnessSaved) {
                return Result(
                    success = false,
                    message = "فون نے اسکرین کی روشنی بدلنے کی اجازت نہیں دی۔ ترتیبات میں نظام کی تبدیلی کی اجازت دیکھیں۔"
                )
            }
            Result(
                success = true,
                message = when (action) {
                    "UP" -> "اسکرین کی روشنی بڑھا دی گئی ہے۔"
                    "DOWN" -> "اسکرین کی روشنی کم کر دی گئی ہے۔"
                    "MAX" -> "اسکرین کی روشنی زیادہ سے زیادہ کر دی گئی ہے۔"
                    else -> "اسکرین کی روشنی کم سے کم کر دی گئی ہے۔"
                }
            )
        } catch (error: Exception) {
            Result(
                success = false,
                message = "روشنی کنٹرول کرنے میں مسئلہ: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
            )
        }
    }

    fun setTorch(
        context: Context,
        enabled: Boolean
    ): Result {
        val appContext = context.applicationContext

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            return Result(
                success = false,
                message = "ٹارچ چلانے کے لیے کیمرے کی اجازت ضروری ہے۔",
                permissionRequired = true
            )
        }

        return try {
            val cameraManager =
                appContext.getSystemService(
                    Context.CAMERA_SERVICE
                ) as? CameraManager
                    ?: return Result(
                        false,
                        "فون کا کیمرہ دستیاب نہیں۔"
                    )

            val cameraId = cameraManager.cameraIdList
                .firstOrNull { id ->
                    val characteristics =
                        cameraManager.getCameraCharacteristics(id)

                    val hasFlash =
                        characteristics.get(
                            CameraCharacteristics.FLASH_INFO_AVAILABLE
                        ) == true

                    val isBackCamera =
                        characteristics.get(
                            CameraCharacteristics.LENS_FACING
                        ) == CameraCharacteristics.LENS_FACING_BACK

                    hasFlash && isBackCamera
                }
                ?: return Result(
                    false,
                    "آپ کے فون میں ٹارچ دستیاب نہیں۔"
                )

            cameraManager.setTorchMode(
                cameraId,
                enabled
            )

            Result(
                success = true,
                message = if (enabled) {
                    "ٹارچ جلا دی گئی ہے۔"
                } else {
                    "ٹارچ بجھا دی گئی ہے۔"
                }
            )

        } catch (_: SecurityException) {
            Result(
                false,
                "کیمرے کی اجازت نہیں ملی۔"
            )
        } catch (_: CameraAccessException) {
            Result(
                false,
                "فون کا کیمرہ ابھی دستیاب نہیں۔"
            )
        } catch (error: Exception) {
            Result(
                false,
                "ٹارچ کنٹرول میں مسئلہ پیش آیا: ${
                    error.localizedMessage ?: "نامعلوم خرابی"
                }"
            )
        }
    }
}
