
package com.idraak.quantum

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.idraak.quantum.engine.HardwareController
import com.idraak.quantum.engine.OfflineBrain

/**
 * Shared offline command router.
 *
 * Main Voice and Background Voice will use this same router.
 * No Gemini or remote server calls are made here.
 */
object OfflineCommandRouter {

    data class Result(
        val response: String,
        val requiresCameraPermission: Boolean = false,
        val torchEnabled: Boolean? = null,
        val intent: String = "UNKNOWN"
    )

    fun process(
        context: Context,
        command: String
    ): Result {
        val appContext = context.applicationContext
        val message = command.trim()

        if (message.isBlank()) {
            return Result(
                response = "कृपया कोई कमांड बोलिए।",
                intent = "EMPTY"
            )
        }

        // Safe Call: existing contact selection and confirmation flow.
        if (CallCommandHandler.tryHandle(context, message)) {
            return Result(
                response = "कॉन्टैक्ट चुनिए। उसके बाद Dialer खोलने से पहले पुष्टि होगी।",
                intent = "SAFE_CALL"
            )
        }

        // Media volume is controlled locally by Android AudioManager.
        val volumeResult = HardwareController.executeVolumeCommand(
            appContext,
            message
        )
        if (volumeResult != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "VOLUME_CONTROL",
                status = if (volumeResult.success) "SUCCESS" else "FAILED",
                steps = listOf(message, volumeResult.message)
            )
            return Result(
                response = volumeResult.message,
                intent = "VOLUME_CONTROL"
            )
        }

        val decision = OfflineBrain.understand(message)

        // Torch commands are handled through the same route
        // for both foreground and background voice.
        if (decision.intent == "FLASHLIGHT") {
            val enabled = requestedTorchState(message)

            if (enabled == null) {
                return Result(
                    response = "कृपया साफ बताइए: टॉर्च चालू करनी है या बंद करनी है?",
                    intent = "FLASHLIGHT"
                )
            }

            val permissionGranted =
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                return Result(
                    response = "टॉर्च चलाने के लिए Camera permission चाहिए। Idraak ऐप खोलकर अनुमति दीजिए।",
                    requiresCameraPermission = true,
                    torchEnabled = enabled,
                    intent = "FLASHLIGHT"
                )
            }

            return executeTorch(
                context = appContext,
                originalCommand = message,
                enabled = enabled
            )
        }

        // App opening, screen control, memory and normal offline
        // conversation continue through the existing OfflineBrain.
        return try {
            Result(
                response = OfflineBrain.respond(
                    context = appContext,
                    userMessage = message
                ),
                intent = decision.intent
            )
        } catch (error: Exception) {
            Result(
                response = "Offline कमांड पूरी नहीं हो सकी: " +
                    (error.localizedMessage ?: "Unknown error"),
                intent = decision.intent
            )
        }
    }

    fun executeTorch(
        context: Context,
        originalCommand: String,
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
                response = "Camera permission नहीं मिली। टॉर्च कंट्रोल नहीं किया गया।",
                requiresCameraPermission = true,
                torchEnabled = enabled,
                intent = "FLASHLIGHT"
            )
        }

        return try {
            val hardwareResult = HardwareController.setTorch(
                context = appContext,
                enabled = enabled
            )

            OfflineBrain.recordExperience(
                context = appContext,
                intent = "FLASHLIGHT",
                status = if (hardwareResult.success) "SUCCESS" else "FAILED",
                steps = listOf(
                    originalCommand,
                    if (enabled) "TORCH_ON" else "TORCH_OFF",
                    hardwareResult.message
                )
            )

            Result(
                response = hardwareResult.message,
                intent = "FLASHLIGHT"
            )
        } catch (error: Exception) {
            Result(
                response = "टॉर्च कंट्रोल में समस्या आई: " +
                    (error.localizedMessage ?: "Unknown error"),
                intent = "FLASHLIGHT"
            )
        }
    }

    private fun requestedTorchState(
        command: String
    ): Boolean? {
        val value = command.trim().lowercase()

        val turnOff =
            Regex("""\boff\b""").containsMatchIn(value) ||
                listOf(
                    "turn off",
                    "switch off",
                    "torch off",
                    "flashlight off",
                    "band",
                    "بند",
                    "बंद",
                    "बुझा"
                ).any { value.contains(it) }

        val turnOn =
            Regex("""\bon\b""").containsMatchIn(value) ||
                listOf(
                    "turn on",
                    "switch on",
                    "torch on",
                    "flashlight on",
                    "chalu",
                    "चालू",
                    "जलाओ",
                    "جلا",
                    "چالو"
                ).any { value.contains(it) }

        return when {
            turnOn && !turnOff -> true
            turnOff && !turnOn -> false
            else -> null
        }
    }
}
