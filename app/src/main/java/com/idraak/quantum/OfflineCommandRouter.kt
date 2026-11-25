package com.idraak.quantum

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.idraak.quantum.engine.CameraController
import com.idraak.quantum.engine.DeviceStatusController
import com.idraak.quantum.engine.HardwareController
import com.idraak.quantum.engine.OfflineBrain
import com.idraak.quantum.engine.OfflineScreenCommandHandler

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
        val requiresForegroundUi: Boolean = false,
        val intent: String = "UNKNOWN"
    )

    fun process(context: Context, command: String, allowForegroundUi: Boolean = true): Result {
        val appContext = context.applicationContext
        val message = command.trim()

        if (message.isBlank()) {
            return Result(response = "कृपया कोई कमांड बोलिए।", intent = "EMPTY")
        }

        // A clear refusal must take priority over call/message handlers.
        // Otherwise phrases such as "Rahul ko call mat karo" could be
        // mistaken for a request to open the call flow.
        val normalizedRefusalText = message
            .lowercase()
            .replace('’', '\'')
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}'\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val hasExplicitNegation = listOf(
            "don't", "don t", "dont", "do not", "never", "not", "no",
            "mat", "mat karo", "nahi", "nahin",
            "मत", "मत करो", "नहीं", "नही",
            "نہیں", "مت", "مت کرو"
        ).any { phrase ->
            Regex("(^|\\s)" + Regex.escape(phrase) + "(?=\\s|$)")
                .containsMatchIn(normalizedRefusalText)
        }
        // Also catch common Hindi/Urdu "na" refusals without treating
        // every conversational sentence ending in "na" as a refusal.
        val hasNegationBeforeAction =
            Regex(
                """(^|\\s)(na|naa|ना|نہ)\\s+(call|open|send|bhejo|bhejna|kholo|kholna|chalao|click|tap|press|कॉल|खोलो|खोलना|भेजो|भेजना|दबाओ|क्लिक|کال|کھولو|بھیجو|بھیجنا|دباؤ)(?=\\s|$)"""
            ).containsMatchIn(normalizedRefusalText) ||
            Regex(
                """(^|\\s)(call|open|send|bhejo|kholo|click|tap|press|कॉल|खोलो|भेजो|दबाओ|کال|کھولو|بھیجو)\\s+(na|naa|ना|نہ)\\s+(karo|karna|kar do|bhejo|bhejna|kholo|kholna|करो|करना|कर दो|भेजो|भेजना|खोलो|खोलना|دباؤ|کرو|بھیجو)(?=\\s|$)"""
            ).containsMatchIn(normalizedRefusalText) ||
            Regex(
                """(^|\\s)(call|open|send|bhejo|kholo|कॉल|खोलो|भेजो|کال|کھولو|بھیجو)\\s+(na|naa|ना|نہ)(?=\\s|$)"""
            ).containsMatchIn(normalizedRefusalText)
        val mentionsCallAction = listOf(
            "call", "phone", "phone call", "फोन", "कॉल", "کال", "فون"
        ).any { phrase -> message.lowercase().contains(phrase) }
        val isMessageAction = MessageCommandHandler.tryHandle(
            context, message, launchActivity = false
        ) != null
        val actionVerbs = listOf(
            "open ", "launch ", "start ", "run ", "call", "phone call",
            "send ", "message", "text ", "take ", "photo", "click ",
            "tap ", "press ", "swipe ", "scroll ", "back", "home",
            "notification", "lock", "power menu", "quick settings",
            "volume", "brightness", "torch", "flashlight",
            "खोल", "चालू", "कॉल", "भेज", "मैसेज", "संदेश", "फोटो",
            "तस्वीर", "खींच", "क्लिक", "दब", "स्वाइप", "स्क्रॉल",
            "पीछे", "होम", "नोटिफिकेशन", "लॉक", "पावर", "वॉल्यूम",
            "आवाज़", "आवाज", "ब्राइटनेस", "चमक", "टॉर्च", "फ्लैशलाइट",
            "کھول", "کال", "بھیج", "میسج", "پیغام", "تصویر", "کلک",
            "دب", "سوائپ", "اسکرول", "واپس", "ہوم", "نوٹیفکیشن",
            "لاک", "پاور", "والیوم", "آواز", "روشنی"
        )
        val mentionsOtherAction = actionVerbs.any {
            normalizedRefusalText.contains(it)
        }

        if ((hasExplicitNegation || hasNegationBeforeAction) && (mentionsCallAction || isMessageAction || mentionsOtherAction)) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "USER_CANCELLED_ACTION",
                status = "REJECTED",
                steps = listOf("Explicit refusal detected")
            )
            return Result(
                response = "ठीक है। आपने मना किया है, इसलिए कोई कार्रवाई नहीं की गई।",
                intent = "CANCELLED"
            )
        }

        if (CallCommandHandler.tryHandle(context, message, launchActivity = allowForegroundUi)) {
            return Result(
                response = if (allowForegroundUi) "कॉन्टैक्ट चुनिए। उसके बाद Dialer खोलने से पहले पुष्टि होगी।" else "कॉल कमांड तैयार है।",
                requiresForegroundUi = !allowForegroundUi,
                intent = "SAFE_CALL"
            )
        }

        // Prepare an SMS offline and let the user confirm before opening the SMS app.
        val messageResponse = MessageCommandHandler.tryHandle(context, message, launchActivity = allowForegroundUi)
        if (messageResponse != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "MESSAGE_COMPOSE",
                status = "AWAITING_USER_CONFIRMATION",
                steps = listOf(message, messageResponse)
            )
            return Result(
                response = messageResponse,
                requiresForegroundUi = !allowForegroundUi && MessageCommandHandler.requiresForegroundUi(message),
                intent = "MESSAGE_COMPOSE"
            )
        }

        // Screen commands use the user's explicitly enabled Android Accessibility service.
        // UNKNOWN means this is not a screen command, so normal routing continues.
        val screenResult = OfflineScreenCommandHandler.execute(message)
        if (screenResult.intent != "UNKNOWN") {
            // Preserve OfflineBrain's pending-confirmation flow for sensitive
            // screen targets such as Send, Delete, Purchase, or Pay.
            // The brain records the pending request and handles a later yes/no.
            if (screenResult.intent == "CLICK_CONFIRMATION_REQUIRED") {
                return Result(
                    response = OfflineBrain.respond(appContext, message),
                    intent = "CLICK_CONFIRMATION_REQUIRED"
                )
            }

            OfflineBrain.recordExperience(
                context = appContext,
                intent = screenResult.intent,
                status = if (screenResult.success) "SUCCESS" else "FAILED",
                steps = listOf(message, screenResult.message)
            )
            return Result(
                response = screenResult.message,
                intent = screenResult.intent
            )
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
        if (!allowForegroundUi && CameraController.isCameraCommand(message)) {
            return Result(
                response = "कैमरा कमांड तैयार है। आगे बढ़ने के लिए Idraak नोटिफिकेशन में OPEN & RUN दबाइए।",
                requiresForegroundUi = true,
                intent = "CAMERA_OPEN"
            )
        }
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

        if (!allowForegroundUi && decision.intent == "OPEN_APP") {
            return Result(
                response = "ऐप खोलने की कमांड तैयार है। आगे बढ़ने के लिए Idraak नोटिफिकेशन में OPEN & RUN दबाइए।",
                requiresForegroundUi = true,
                intent = "OPEN_APP"
            )
        }

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
