package com.idraak.quantum

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.idraak.quantum.engine.CameraController
import com.idraak.quantum.engine.DeviceStatusController
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
        val requiresBrightnessPermission: Boolean = false,
        val intent: String = "UNKNOWN"
    )

    fun process(context: Context, command: String): Result {
        val appContext = context.applicationContext
        val message = command.trim()

        if (message.isBlank()) {
            return Result(response = "कृपया कोई कमांड बोलिए।", intent = "EMPTY")
        }

        if (CallCommandHandler.tryHandle(context, message)) {
            return Result(
                response = "कॉन्टैक्ट चुनिए। उसके बाद Dialer खोलने से पहले पुष्टि होगी।",
                intent = "SAFE_CALL"
            )
        }

        // Prepare an SMS offline and let the user confirm before opening the SMS app.
        val messageResponse = MessageCommandHandler.tryHandle(context, message)
        if (messageResponse != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "MESSAGE_COMPOSE",
                status = "AWAITING_USER_CONFIRMATION",
                steps = listOf(message, messageResponse)
            )
            return Result(response = messageResponse, intent = "MESSAGE_COMPOSE")
        }

        // Battery status is read directly from Android's local battery broadcast.
        val batteryResult = DeviceStatusController.executeBatteryCommand(appContext, message)
        if (batteryResult != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "BATTERY_STATUS",
                status = if (batteryResult.success) "SUCCESS" else "FAILED",
                steps = listOf(message, batteryResult.message)
            )
            return Result(response = batteryResult.message, intent = "BATTERY_STATUS")
        }

        val volumeResult = HardwareController.executeVolumeCommand(appContext, message)
        if (volumeResult != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "VOLUME_CONTROL",
                status = if (volumeResult.success) "SUCCESS" else "FAILED",
                steps = listOf(message, volumeResult.message)
            )
            return Result(response = volumeResult.message, intent = "VOLUME_CONTROL")
        }

        val brightnessResult = HardwareController.executeBrightnessCommand(appContext, message)
        if (brightnessResult != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "BRIGHTNESS_CONTROL",
                status = if (brightnessResult.success) "SUCCESS" else "FAILED",
                steps = listOf(message, brightnessResult.message)
            )
            return Result(
                response = brightnessResult.message,
                requiresBrightnessPermission = brightnessResult.permissionRequired,
                intent = "BRIGHTNESS_CONTROL"
            )
        }

        // Open the installed camera app; the user controls capture and saving.
        val cameraResult = CameraController.executeCameraCommand(appContext, message)
        if (cameraResult != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "CAMERA_OPEN",
                status = if (cameraResult.success) "SUCCESS" else "FAILED",
                steps = listOf(message, cameraResult.message)
            )
            return Result(
                response = cameraResult.message,
                intent = "CAMERA_OPEN"
            )
        }

        val decision = OfflineBrain.understand(message)

        if (decision.intent == "FLASHLIGHT") {
            val enabled = requestedTorchState(message)
            if (enabled == null) {
                return Result(
                    response = "कृपया साफ बताइए: टॉर्च चालू करनी है या बंद करनी है?",
                    intent = "FLASHLIGHT"
                )
            }

            val permissionGranted = ContextCompat.checkSelfPermission(
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

            return executeTorch(appContext, message, enabled)
        }

        return try {
            Result(
                response = OfflineBrain.respond(context = appContext, userMessage = message),
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

    fun executeTorch(context: Context, originalCommand: String, enabled: Boolean): Result {
        val appContext = context.applicationContext
        val permissionGranted = ContextCompat.checkSelfPermission(
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
            val hardwareResult = HardwareController.setTorch(appContext, enabled)
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
            Result(response = hardwareResult.message, intent = "FLASHLIGHT")
        } catch (error: Exception) {
            Result(
                response = "टॉर्च कंट्रोल में समस्या आई: " +
                    (error.localizedMessage ?: "Unknown error"),
                intent = "FLASHLIGHT"
            )
        }
    }

    private fun requestedTorchState(command: String): Boolean? {
        val value = command.trim().lowercase()
        val turnOff = Regex("""\boff\b""").containsMatchIn(value) ||
            listOf("turn off", "switch off", "torch off", "flashlight off",
                "band", "بند", "बंद", "बुझा").any { value.contains(it) }
        val turnOn = Regex("""\bon\b""").containsMatchIn(value) ||
            listOf("turn on", "switch on", "torch on", "flashlight on",
                "chalu", "चालू", "जलाओ", "جلا", "چالو").any { value.contains(it) }

        return when {
            turnOn && !turnOff -> true
            turnOff && !turnOn -> false
            else -> null
        }
    }
}
