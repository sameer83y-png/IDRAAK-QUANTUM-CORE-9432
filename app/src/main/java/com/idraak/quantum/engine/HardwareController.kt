
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
            ?: return Result(false, "फोन का Volume Controller उपलब्ध नहीं है।")

        val stream = AudioManager.STREAM_MUSIC
        val current = audioManager.getStreamVolume(stream)
        val maximum = audioManager.getStreamMaxVolume(stream)

        return try {
            when {
                listOf("volume up", "increase volume", "आवाज बढ़ाओ", "वॉल्यूम बढ़ाओ", "आवाज़ तेज करो", "volume badhao", "volume tez karo", "awaz badhao", "awaaz tez karo", "آواز بڑھاؤ", "والیوم بڑھاؤ", "آواز تیز کرو")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                    Result(true, "मीडिया वॉल्यूम बढ़ा दिया गया है।")
                }

                listOf("volume down", "decrease volume", "आवाज कम करो", "वॉल्यूम कम करो", "आवाज़ धीमी करो", "volume kam karo", "awaz kam karo", "awaaz dheemi karo", "آواز کم کرو", "والیوم کم کرو", "آواز آہستہ کرو")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                    Result(true, "मीडिया वॉल्यूम कम कर दिया गया है।")
                }

                listOf("mute volume", "mute sound", "आवाज बंद करो", "वॉल्यूम म्यूट करो", "आवाज़ म्यूट", "awaz mute karo", "awaaz band karo", "آواز خاموش کرو", "والیوم میوٹ کرو")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                    Result(true, "मीडिया आवाज़ म्यूट कर दी गई है।")
                }

                listOf("unmute volume", "sound on", "आवाज चालू करो", "वॉल्यूम अनम्यूट", "awaz chalu karo", "awaaz on karo", "آواز کھولو", "والیوم اَن میوٹ کرو")
                    .any { value.contains(it) } -> {
                    audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                    Result(true, "मीडिया आवाज़ चालू कर दी गई है।")
                }

                listOf("maximum volume", "volume full", "आवाज फुल करो", "वॉल्यूम फुल करो", "volume maximum karo", "awaz full karo", "آواز پوری کرو", "والیوم فل کرو")
                    .any { value.contains(it) } -> {
                    audioManager.setStreamVolume(stream, maximum, 0)
                    Result(true, "मीडिया वॉल्यूम अधिकतम कर दिया गया है।")
                }

                listOf("minimum volume", "volume zero", "आवाज बिल्कुल कम करो", "volume zero karo", "awaz bilkul kam karo", "آواز بالکل کم کرو")
                    .any { value.contains(it) } -> {
                    audioManager.setStreamVolume(stream, 0, 0)
                    Result(true, "मीडिया वॉल्यूम शून्य कर दिया गया है।")
                }

                else -> null
            }
        } catch (_: SecurityException) {
            Result(false, "Android ने वॉल्यूम बदलने की अनुमति नहीं दी।")
        } catch (error: Exception) {
            Result(false, "वॉल्यूम कंट्रोल में समस्या: " +
                (error.localizedMessage ?: "Unknown error"))
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
            listOf("brightness up", "increase brightness", "screen brighter", "चमक बढ़ाओ", "ब्राइटनेस बढ़ाओ", "रोशनी बढ़ाओ", "brightness badhao", "screen bright karo", "roshni badhao", "روشنی بڑھاؤ", "برائٹنس بڑھاؤ")
                .any { value.contains(it) } -> "UP"
            listOf("brightness down", "decrease brightness", "screen dim", "चमक कम करो", "ब्राइटनेस कम करो", "रोशनी कम करो", "brightness kam karo", "screen dim karo", "roshni kam karo", "روشنی کم کرو", "برائٹنس کم کرو")
                .any { value.contains(it) } -> "DOWN"
            listOf("maximum brightness", "brightness full", "चमक पूरी करो", "ब्राइटनेस फुल करो", "brightness full karo", "roshni full karo", "روشنی پوری کرو", "برائٹنس فل کرو")
                .any { value.contains(it) } -> "MAX"
            listOf("minimum brightness", "brightness minimum", "चमक सबसे कम करो", "ब्राइटनेस जीरो करो", "brightness minimum karo", "roshni bilkul kam karo", "روشنی کم سے کم کرو", "برائٹنس زیرو کرو")
                .any { value.contains(it) } -> "MIN"
            else -> return null
        }

        if (!Settings.System.canWrite(context)) {
            return Result(
                success = false,
                message = "स्क्रीन ब्राइटनेस बदलने के लिए Android की Modify System Settings अनुमति चाहिए।",
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

            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                next
            )
            Result(
                success = true,
                message = when (action) {
                    "UP" -> "स्क्रीन ब्राइटनेस बढ़ा दी गई है।"
                    "DOWN" -> "स्क्रीन ब्राइटनेस कम कर दी गई है।"
                    "MAX" -> "स्क्रीन ब्राइटनेस अधिकतम कर दी गई है।"
                    else -> "स्क्रीन ब्राइटनेस न्यूनतम कर दी गई है।"
                }
            )
        } catch (error: Exception) {
            Result(
                success = false,
                message = "ब्राइटनेस कंट्रोल में समस्या: " +
                    (error.localizedMessage ?: "Unknown error")
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
                message = "टॉर्च चलाने के लिए Camera permission जरूरी है।",
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
                        "फोन का Camera Hardware उपलब्ध नहीं है।"
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
                    "आपके फोन में समर्थित Flashlight नहीं मिली।"
                )

            cameraManager.setTorchMode(
                cameraId,
                enabled
            )

            Result(
                success = true,
                message = if (enabled) {
                    "टॉर्च चालू कर दी गई है।"
                } else {
                    "टॉर्च बंद कर दी गई है।"
                }
            )

        } catch (_: SecurityException) {
            Result(
                false,
                "Camera permission नहीं मिली।"
            )
        } catch (_: CameraAccessException) {
            Result(
                false,
                "फोन का Camera Hardware अभी उपलब्ध नहीं है।"
            )
        } catch (error: Exception) {
            Result(
                false,
                "टॉर्च कंट्रोल में समस्या आई: ${
                    error.localizedMessage ?: "Unknown error"
                }"
            )
        }
    }
}
