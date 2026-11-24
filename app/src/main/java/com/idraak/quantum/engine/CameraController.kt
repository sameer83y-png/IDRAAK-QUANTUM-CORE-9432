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

    fun executeCameraCommand(context: Context, command: String): Result? {
        val value = command.trim().lowercase(Locale.ROOT)

        val cameraPhrases = listOf(
            "open camera", "launch camera", "start camera", "camera kholo",
            "camera khol do", "कैमरा खोलो", "कैमरा खोल दो", "कैमरा चालू करो",
            "کیمرا کھولو", "کیمرا کھول دو"
        )

        val photoPhrases = listOf(
            "take photo", "take a photo", "click photo", "snap photo",
            "photo lo", "photo lena", "photo kheecho", "photo khincho",
            "फोटो लो", "फोटो लेना है", "फोटो खींचो", "फोटो खींच दो",
            "तस्वीर लो", "تصویر لو", "تصویر کھینچو"
        )

        val shouldCapture = photoPhrases.any { value.contains(it) }
        val shouldOpenCamera = shouldCapture ||
            cameraPhrases.any { value.contains(it) }

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
                    "आवाज़ से फोटो लेने के लिए Idraak को पहले स्क्रीन पर खोलिए। Android बैकग्राउंड में कैमरा अपने-आप शुरू करने की अनुमति नहीं देता।"
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
                    "Idraak कैमरा खुल रहा है और अनुमति मिलने पर फोटो अपने-आप खींचेगा।"
                } else {
                    "Idraak कैमरा खुल रहा है।"
                }
            )
        } catch (error: Exception) {
            Result(
                false,
                "Idraak कैमरा शुरू नहीं हो सका: " +
                    (error.localizedMessage ?: "Unknown error")
            )
        }
    }
}
