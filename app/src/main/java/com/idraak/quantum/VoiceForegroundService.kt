package com.idraak.quantum

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class VoiceForegroundService : Service() {

    @Volatile
    private var serviceActive = false

    @Volatile
    private var stopAfterSpeech = false

    private var voiceController: VoiceController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopVoiceService()
                return START_NOT_STICKY
            }

            ACTION_SPEAK_RESULT -> {
                val response = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                if (serviceActive && response.isNotBlank()) {
                    voiceController?.cancelListening()
                    voiceController?.speak(response)
                } else {
                    sendStatus("Background voice is not active for spoken response")
                }
            }

            ACTION_SET_LANGUAGE -> {
                val languageName = intent.getStringExtra(EXTRA_LANGUAGE)
                val language = try {
                    VoskModelStore.VoskLanguage.valueOf(
                        languageName ?: ""
                    )
                } catch (_: IllegalArgumentException) {
                    null
                }

                if (serviceActive && language != null) {
                    voiceController?.setRecognitionLanguage(
                        language,
                        resumeListening = true
                    )
                } else if (language == null) {
                    sendStatus("Unsupported offline voice language")
                }
            }

            ACTION_START, null -> {
                startVoiceService()
            }
        }

        return START_NOT_STICKY
    }

    private fun startVoiceService() {
        if (serviceActive) return

        val microphoneGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!microphoneGranted) {
            sendStatus("Microphone permission required")
            stopSelf()
            return
        }

        try {
            val notification = createNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification
                )
            }

            serviceActive = true
            isRunning = true
            sendStatus("Background voice service starting")

            voiceController = VoiceController(
                context = applicationContext,

                onSpeechResult = { recognizedText ->
                    processCommand(recognizedText)
                },

                onStatusChanged = { status ->
                    sendStatus(status)
                },

                onSpeechFinished = {
                    if (serviceActive) {
                        if (stopAfterSpeech) {
                            stopVoiceService()
                        } else {
                            restartListening()
                        }
                    }
                }
            )

            val started =
                voiceController?.startListening() == true

            if (!started) {
                sendStatus("Could not start offline listening")
            }

        } catch (error: Exception) {
            sendStatus(
                "Background voice error: " +
                    (error.localizedMessage ?: "Unknown error")
            )
            stopVoiceService()
        }
    }

    /**
     * All background voice commands now pass through
     * OfflineCommandRouter, the same router used by
     * the foreground voice integration.
     *
     * No Gemini or remote server request is made here.
     */
    private fun processCommand(command: String) {
        if (!serviceActive || command.isBlank()) return

        voiceController?.cancelListening()
        sendCommand(command)

        if (VoiceSessionCommandHandler.isStopListeningCommand(command)) {
            stopAfterSpeech = true
            val response = "ठीक है। Idraak की background voice listening बंद की जा रही है।"
            sendResponse(response)
            voiceController?.speak(response)
            return
        }

        val result = try {
            OfflineCommandRouter.process(
                context = applicationContext,
                command = command,
                allowForegroundUi = false
            )
        } catch (error: Exception) {
            OfflineCommandRouter.Result(
                response = "कमांड पूरी नहीं हो सकी। " +
                    (error.localizedMessage ?: "Unknown error"),
                intent = "ERROR"
            )
        }

        if (!serviceActive) return

        if (result.requiresForegroundUi) {
            showForegroundCommandAction(command)
            val response = "कमांड तैयार है। Idraak के नोटिफिकेशन में OPEN & RUN दबाकर इसे आगे बढ़ाइए।"
            sendResponse(response)
            voiceController?.speak(response)
            return
        }

        // Android permission screens must be opened from the visible app.
        // Never try to bypass background-activity or runtime-permission rules.
        val response = when {
            result.requiresBrightnessPermission ->
                result.response +
                    " ब्राइटनेस की अनुमति देने के लिए Idraak ऐप खोलिए, फिर यही कमांड दोबारा बोलिए।"

            result.requiresCameraPermission ->
                result.response +
                    " टॉर्च की अनुमति देने के लिए Idraak ऐप खोलिए, फिर यही कमांड दोबारा बोलिए।"

            else -> result.response
        }

        sendResponse(response)
        voiceController?.speak(response)
    }

    private fun restartListening() {
        if (!serviceActive) return

        try {
            val started =
                voiceController?.startListening() == true

            if (started) {
                sendStatus("Background offline listening active")
            } else {
                sendStatus("Background listening could not restart")
            }
        } catch (error: Exception) {
            sendStatus(
                "Listening restart error: " +
                    (error.localizedMessage ?: "Unknown error")
            )
        }
    }

    private fun stopVoiceService() {
        val wasActive = serviceActive
        serviceActive = false
        isRunning = false
        stopAfterSpeech = false

        try {
            voiceController?.shutdown()
        } catch (_: Exception) {
        }

        voiceController = null

        if (wasActive) {
            sendStatus("Background voice service stopped")
        }

        // Always stop the Service, even if startForeground failed before
        // serviceActive became true or STOP arrived during initialization.
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Idraak Background Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Shows when Idraak offline voice listening is active"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun showForegroundCommandAction(command: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(command))
    }

    private fun createNotification(pendingCommand: String? = null): Notification {
        val openAppIntent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openAppPendingIntent =
            PendingIntent.getActivity(
                this,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent = Intent(
            this,
            VoiceForegroundService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                101,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("IDRAAK is listening")
            .setContentText(
                if (pendingCommand.isNullOrBlank()) {
                    "Offline voice control is active. Tap STOP to end listening."
                } else {
                    "कमांड तैयार है। आगे बढ़ने के लिए OPEN & RUN दबाइए।"
                }
            )
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (!pendingCommand.isNullOrBlank()) {
            val commandIntent = Intent(this, ForegroundCommandActivity::class.java).apply {
                putExtra(ForegroundCommandActivity.EXTRA_COMMAND, pendingCommand)
            }
            val commandPendingIntent = PendingIntent.getActivity(
                this,
                102,
                commandIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )
            builder.addAction(android.R.drawable.ic_media_play, "OPEN & RUN", commandPendingIntent)
        }

        builder.addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
        return builder.build()
    }

    private fun sendStatus(status: String) {
        val intent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
        }

        sendBroadcast(intent)
    }

    private fun sendCommand(command: String) {
        val intent = Intent(ACTION_COMMAND).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, command)
        }

        sendBroadcast(intent)
    }

    private fun sendResponse(response: String) {
        val intent = Intent(ACTION_RESPONSE).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, response)
        }

        sendBroadcast(intent)
    }

    override fun onDestroy() {
        serviceActive = false
        isRunning = false

        try {
            voiceController?.shutdown()
        } catch (_: Exception) {
        }

        voiceController = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START =
            "com.idraak.quantum.action.START_BACKGROUND_VOICE"

        const val ACTION_STOP =
            "com.idraak.quantum.action.STOP_BACKGROUND_VOICE"

        const val ACTION_SET_LANGUAGE =
            "com.idraak.quantum.action.SET_BACKGROUND_VOICE_LANGUAGE"

        const val ACTION_SPEAK_RESULT =
            "com.idraak.quantum.action.SPEAK_BACKGROUND_RESULT"

        @Volatile
        var isRunning: Boolean = false
            private set

        const val EXTRA_LANGUAGE = "language"

        const val ACTION_STATUS =
            "com.idraak.quantum.action.BACKGROUND_VOICE_STATUS"

        const val ACTION_COMMAND =
            "com.idraak.quantum.action.BACKGROUND_VOICE_COMMAND"

        const val ACTION_RESPONSE =
            "com.idraak.quantum.action.BACKGROUND_VOICE_RESPONSE"

        const val EXTRA_STATUS = "status"
        const val EXTRA_TEXT = "text"

        private const val CHANNEL_ID =
            "idraak_background_voice_channel"

        private const val NOTIFICATION_ID = 4201
    }
}
