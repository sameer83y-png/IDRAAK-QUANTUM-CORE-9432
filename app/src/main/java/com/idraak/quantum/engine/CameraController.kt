package com.idraak.quantum.engine

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import java.util.Locale

/**
 * Opens the device's installed camera app for a user-directed photo.
 * The user remains in control of the shutter and saving the image.
 * No camera feed is accessed or uploaded by Idraak.
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
            "take photo", "take a photo", "open photo camera",
            "photo lo", "photo lena", "फोटो लो", "फोटो लेना है",
            "तस्वीर लो", "تصویر لو"
        )

        if (cameraPhrases.none { value.contains(it) } &&
            photoPhrases.none { value.contains(it) }) {
            return null
        }

        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return Result(false, "इस फोन में Camera ऐप उपलब्ध नहीं मिली।")
            }
            context.applicationContext.startActivity(intent)
            Result(true, "Camera खुल गया है। फोटो लेने और सेव करने के लिए स्क्रीन पर खुद बटन दबाइए।")
        } catch (error: Exception) {
            Result(
                false,
                "Camera खोलने में समस्या आई: " +
                    (error.localizedMessage ?: "Unknown error")
            )
        }
    }
}
