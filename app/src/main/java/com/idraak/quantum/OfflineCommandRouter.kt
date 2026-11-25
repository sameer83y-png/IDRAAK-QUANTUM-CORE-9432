package com.idraak.quantum

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.idraak.quantum.engine.CameraController
import com.idraak.quantum.engine.DeviceStatusController
import com.idraak.quantum.engine.HardwareController
import com.idraak.quantum.engine.LearnedCommandStore
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
        val originalMessage = command.trim()
        // Detect common multilingual phrasing, then route through the existing Urdu handlers.
        val multilingualMessage = MultilingualCommandNormalizer.normalize(originalMessage)
        val message = LearnedCommandStore.resolve(appContext, multilingualMessage)
            ?: multilingualMessage

        if (message.isBlank()) {
            return Result(response = "براہِ کرم اپنا حکم بتائیں۔", intent = "EMPTY")
        }

        // Execute clearly chained commands in order. Only explicit sequence words
        // are treated as separators, so ordinary Urdu "اور" does not split a phrase.
        val sequenceParts = splitSequentialCommands(originalMessage)
        if (sequenceParts.size in 2..4) {
            val completedSteps = mutableListOf<String>()
            for ((index, step) in sequenceParts.withIndex()) {
                val stepResult = process(
                    context = appContext,
                    command = step,
                    allowForegroundUi = allowForegroundUi
                )
                completedSteps += "${index + 1}. ${stepResult.response}"

                val mustStop = stepResult.requiresCameraPermission ||
                    stepResult.requiresBrightnessPermission ||
                    stepResult.requiresForegroundUi ||
                    stepResult.intent in setOf(
                        "CLICK_CONFIRMATION_REQUIRED",
                        "CANCELLED",
                        "UNKNOWN",
                        "EMPTY",
                        "SERVICE_DISABLED",
                        "SCREEN_READ_EMPTY",
                        "TYPE_TEXT_FAILED",
                        "ACTION_FAILED",
                        "ACTION_ERROR"
                    ) ||
                    stepResult.intent.endsWith("_FAILED")

                if (mustStop) {
                    completedSteps += "اگلا مرحلہ روک دیا گیا ہے۔ پہلے اس مرحلے کو مکمل یا منظور کریں۔"
                    return Result(
                        response = "ترتیب وار کام کی رپورٹ:\n" + completedSteps.joinToString("\n"),
                        requiresCameraPermission = stepResult.requiresCameraPermission,
                        torchEnabled = stepResult.torchEnabled,
                        requiresBrightnessPermission = stepResult.requiresBrightnessPermission,
                        requiresForegroundUi = stepResult.requiresForegroundUi,
                        intent = "SEQUENCE_PAUSED"
                    )
                }
            }

            return Result(
                response = "ترتیب وار کام مکمل ہوا:\n" + completedSteps.joinToString("\n"),
                intent = "SEQUENCE_COMPLETED"
            )
        }

        LearnedCommandStore.handleTeaching(appContext, originalMessage)?.let { response ->
            OfflineBrain.recordExperience(
                context = appContext,
                intent = "COMMAND_ALIAS_TEACHING",
                status = "PENDING_VERIFICATION"
            )
            return Result(response = response, intent = "COMMAND_ALIAS_TEACHING")
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
            "نہیں", "مت", "مت کرو", "ہرگز نہیں"
        ).any { phrase ->
            Regex("(^|\\s)" + Regex.escape(phrase) + "(?=\\s|$)")
                .containsMatchIn(normalizedRefusalText)
        }
        val hasNegationBeforeAction =
            Regex(
                """(^|\s)(مت|نہ)\s+(کال|کھولو|بھیجو|تلاش|دباؤ|چلاؤ)(?=\s|$)"""
            ).containsMatchIn(normalizedRefusalText) ||
            Regex(
                """(^|\s)(کال|کھولو|بھیجو|تلاش|دباؤ)\s+(مت|نہ)\s+(کرو|کر دو|بھیجو|کھولو)(?=\s|$)"""
            ).containsMatchIn(normalizedRefusalText) ||
            Regex(
                """(^|\s)(کال|کھولو|بھیجو)\s+(مت|نہ)(?=\s|$)"""
            ).containsMatchIn(normalizedRefusalText)
        val mentionsCallAction = listOf(
            "کال", "فون"
        ).any { phrase -> normalizedRefusalText.contains(phrase) }
        val isMessageAction = MessageCommandHandler.tryHandle(
            context, message, launchActivity = false
        ) != null
        val actionVerbs = listOf(
            "ویب سائٹ", "تلاش", "کھولو", "چالو", "کال", "بھیجو",
            "پیغام", "تصویر", "کلک", "دباؤ", "سوائپ", "اسکرول",
            "واپس", "ہوم", "نوٹیفکیشن", "لاک", "پاور",
            "والیوم", "آواز", "روشنی", "ٹارچ"
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
                response = "ٹھیک ہے، آپ نے منع کیا ہے، اس لیے کوئی کارروائی نہیں کی گئی۔",
                intent = "CANCELLED"
            )
        }

        if (CallCommandHandler.tryHandle(context, message, launchActivity = allowForegroundUi)) {
            return Result(
                response = if (allowForegroundUi) "رابطہ منتخب کریں۔ فون ایپ کھولنے سے پہلے تصدیق لی جائے گی۔" else "کال کا عمل تیار ہے۔",
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

        // Device radio and display settings are opened through Android's own
        // local settings screens. Protected toggles are never falsely reported
        // as changed by an ordinary third-party app.
        val deviceSettingsResult = com.idraak.quantum.engine.DeviceSettingsCommandHandler.execute(
            context = appContext,
            command = message,
            allowForegroundUi = allowForegroundUi
        )
        if (deviceSettingsResult != null) {
            OfflineBrain.recordExperience(
                context = appContext,
                intent = deviceSettingsResult.intent,
                status = if (deviceSettingsResult.requiresForegroundUi) "AWAITING_FOREGROUND" else "SUCCESS",
                steps = listOf(message, deviceSettingsResult.response)
            )
            return Result(
                response = deviceSettingsResult.response,
                requiresForegroundUi = deviceSettingsResult.requiresForegroundUi,
                intent = deviceSettingsResult.intent
            )
        }

        // Website and browser-search commands are handed off to an external app.
        // Idraak itself keeps no network permission.
        val webNavigationResponse = WebNavigationCommandHandler.tryHandle(
            context = context,
            command = message,
            launchActivity = allowForegroundUi
        )
        if (webNavigationResponse != null) {
            return Result(
                response = webNavigationResponse,
                requiresForegroundUi = !allowForegroundUi,
                intent = "WEB_NAVIGATION"
            )
        }

        // Screen commands use the user's explicitly enabled Android Accessibility service.
        // UNKNOWN means this is not a screen command, so normal routing continues.
        val screenResult = OfflineScreenCommandHandler.execute(originalMessage).let { originalResult ->
            if (originalResult.intent == "UNKNOWN") {
                OfflineScreenCommandHandler.execute(message)
            } else {
                originalResult
            }
        }
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
                response = "کیمرہ کھولنے کا حکم تیار ہے۔ جاری رکھنے کے لیے ادراک کی اطلاع میں کھولنے کا اختیار دبائیں۔",
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
                response = "ایپ کھولنے کا حکم تیار ہے۔ جاری رکھنے کے لیے ادراک کی اطلاع میں کھولنے کا اختیار دبائیں۔",
                requiresForegroundUi = true,
                intent = "OPEN_APP"
            )
        }

        if (decision.intent == "FLASHLIGHT") {
            val enabled = requestedTorchState(message)
            if (enabled == null) {
                return Result(
                    response = "واضح بتائیں: ٹارچ جلانی ہے یا بجھانی ہے؟",
                    intent = "FLASHLIGHT"
                )
            }

            val permissionGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (!permissionGranted) {
                return Result(
                    response = "ٹارچ چلانے کے لیے کیمرے کی اجازت درکار ہے۔ ادراک کھول کر اجازت دیں۔",
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
                response = "آف لائن حکم مکمل نہیں ہو سکا: " +
                    (error.localizedMessage ?: "نامعلوم خرابی"),
                intent = decision.intent
            )
        }
    }

    private fun splitSequentialCommands(command: String): List<String> {
        val separator = Regex(
            """(?i)\\s+(?:اور\\s+پھر|اس\\s+کے\\s+بعد|and\\s+then|then|پھر|ثم)\\s+"""
        )
        return command.split(separator)
            .map { it.trim().trim(',', '،', ';', '؛', '.') }
            .filter { it.isNotBlank() }
            .take(5)
    }

    fun executeTorch(context: Context, originalCommand: String, enabled: Boolean): Result {
        val appContext = context.applicationContext
        val permissionGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            return Result(
                response = "کیمرے کی اجازت نہیں ملی، اس لیے ٹارچ کنٹرول نہیں کی گئی۔",
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
                response = "ٹارچ کنٹرول میں مسئلہ پیش آیا: " +
                    (error.localizedMessage ?: "نامعلوم خرابی"),
                intent = "FLASHLIGHT"
            )
        }
    }

    private fun requestedTorchState(command: String): Boolean? {
        val value = command.trim().lowercase()
        val turnOff = Regex("""\boff\b""").containsMatchIn(value) ||
            listOf("turn off", "switch off", "torch off", "flashlight off",
                "بند", "بجھا", "بجھا دو", "آف", "آف کرو", "بجھا دیں",
                "أطفئ", "اطفئ", "أغلق", "اغلق", "أوقف", "اوقف").any { value.contains(it) }
        val turnOn = Regex("""\bon\b""").containsMatchIn(value) ||
            listOf("turn on", "switch on", "torch on", "flashlight on",
                "جلا", "جلا دو", "چالو", "آن", "آن کرو", "جلا دیں",
                "شغل", "افتح", "أضيء", "اضئ").any { value.contains(it) }

        return when {
            turnOn && !turnOff -> true
            turnOff && !turnOn -> false
            else -> null
        }
    }
}
