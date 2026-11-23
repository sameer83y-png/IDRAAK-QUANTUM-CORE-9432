fun sendMessage(messageText: String = inputText) {
    val message = messageText.trim()
    if (message.isEmpty()) return

    messages.add(ChatMessage(message, true))
    inputText = ""

    // Safe Call Command
    if (
        CallCommandHandler.tryHandle(
            context = context,
            message = message
        )
    ) {
        val response =
            "कॉन्टैक्ट चुनिए। उसके बाद कॉल करने से पहले पुष्टि होगी।"

        messages.add(ChatMessage(response, false))
        voiceController.speak(response)
        voiceStatus = "CALL CONTROLLER OPENED"
        return
    }

    val decision = OfflineBrain.understand(message)

    if (decision.intent == "FLASHLIGHT") {
        val enabled = requestedTorchState(message)

        if (enabled == null) {
            val response =
                "कृपया साफ बताइए: टॉर्च चालू करनी है या बंद करनी है?"

            messages.add(ChatMessage(response, false))
            voiceController.speak(response)
            voiceStatus = "FLASHLIGHT COMMAND NEEDS ON/OFF"
            return
        }

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            executeTorchCommand(
                originalMessage = message,
                enabled = enabled
            )
        } else {
            pendingTorchCommand = message to enabled
            voiceStatus = "REQUESTING CAMERA PERMISSION"

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }

        return
    }

    val response = try {
        OfflineBrain.respond(
            context = context.applicationContext,
            userMessage = message
        )
    } catch (error: Exception) {
        "Boss, Offline Brain mein ek error aaya: " +
            (error.localizedMessage ?: "Unknown error")
    }

    messages.add(ChatMessage(response, false))
    voiceController.speak(response)
    voiceStatus = "OFFLINE BRAIN ACTIVE"
}
