package com.idraak.quantum.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.idraak.quantum.CameraCaptureActivity
import java.util.Locale

/**
 * Routes camera commands to Idraak's visible in-app CameraX screen.
 * Automatic capture is only started while the app is visibly foregrounded.
 */
object CameraController {

    data class Result(
        val success: Boolean,
        val message: String
    )

    private val cameraPhrases = listOf(
        "open camera", "launch camera", "start camera", "camera kholo",
        "camera khol do", "camera kholna", "camera on karo",
        "कैमरा खोलो", "कैमरा खोल दो", "कैमरा खोलिए", "कैमरा चालू करो",
        "کیمرا کھولو", "کیمرا کھول دو", "کیمرہ کھولیں",
        "افتح الكاميرا", "شغل الكاميرا", "افتح كاميرا"
    )

    private val photoPhrases = listOf(
        "take photo", "take a photo", "click photo", "snap photo",
        "photo lo", "photo lena", "photo kheecho", "photo khincho",
        "tasveer lo", "tasveer kheecho", "tasveer khincho",
        "फोटो लो", "फोटो लेना है", "फोटो खींचो", "फोटो खींच दो", "फोटो खींचिए",
        "तस्वीर लो", "तस्वीर खींचिए",
        "تصویر لو", "تصویر کھینچو", "تصویر کھینچیں", "تصویر لے لو",
        "التقط صورة", "التقط صورة لي", "خذ صورة", "خذ لي صورة"
    )

    fun isCameraCommand(command: String): Boolean {
        val value = command.trim().lowercase(Locale.ROOT)
        return photoPhrases.any { value.contains(it) } || cameraPhrases.any { value.contains(it) }
    }

    fun executeCameraCommand(context: Context, command: String): Result? {
        val value = command.trim().lowercase(Locale.ROOT)
        val shouldCapture = photoPhrases.any { value.contains(it) }
        val shouldOpenCamera = isCameraCommand(command)
        if (!shouldOpenCamera) return null

        return try {
            val processInfo = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(processInfo)
            val appIsVisible =
                processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE

            if (!appIsVisible) {
                return Result(
                    false,
                    "آواز سے تصویر لینے کے لیے پہلے ادراک کو اسکرین پر کھولیں۔ فون پس منظر میں کیمرہ خود شروع کرنے کی اجازت نہیں دیتا۔"
                )
            }

            val intent = Intent(context, CameraCaptureActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(CameraCaptureActivity.EXTRA_AUTO_CAPTURE, shouldCapture)
            }
            context.applicationContext.startActivity(intent)

            Result(
                true,
                if (shouldCapture) {
                    "ادراک کا کیمرہ کھل رہا ہے اور اجازت ملنے پر تصویر خود لے گا۔"
                } else {
                    "ادراک کا کیمرہ کھل رہا ہے۔"
                }
            )
        } catch (error: Exception) {
            Result(
                false,
                "ادراک کا کیمرہ شروع نہیں ہو سکا: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
            )
        }
    }
}
