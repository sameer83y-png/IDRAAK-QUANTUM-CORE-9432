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
                    sendStatus("پس منظر میں آواز سننے کا نظام فعال نہیں ہے")
                }
            }

            ACTION_SET_LANGUAGE -> {
                val languageName = intent.getStringExtra(EXTRA_LANGUAGE)
                val language = try {
                    UrduVoiceLanguage.valueOf(
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
                    sendStatus("یہ آف لائن زبان دستیاب نہیں")
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
            sendStatus("مائیکروفون کی اجازت درکار ہے")
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
            sendStatus("پس منظر میں آواز سننا شروع ہو رہا ہے")

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
                sendStatus("آف لائن سننا شروع نہیں ہو سکا")
            }

        } catch (error: Exception) {
            sendStatus(
                "پس منظر کی آواز میں خرابی: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
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

        sendCommand(command)

        if (VoiceSessionCommandHandler.isStopListeningCommand(command)) {
            // Explicit stop requests must interrupt capture immediately.
            voiceController?.cancelListening()
            stopAfterSpeech = true
            val response = "ٹھیک ہے، ادراک پس منظر میں سننا بند کر رہا ہے۔"
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
                response = "حکم مکمل نہیں ہو سکا۔ " +
                    (error.localizedMessage ?: "نامعلوم خرابی"),
                intent = "ERROR"
            )
        }

        if (!serviceActive) return

        if (result.requiresForegroundUi) {
            showForegroundCommandAction(command)
            val response = "حکم تیار ہے۔ جاری رکھنے کے لیے ادراک کی اطلاع میں کھولنے کا اختیار دبائیں۔"
            sendResponse(response)
            voiceController?.speak(response)
            return
        }

        // Android permission screens must be opened from the visible app.
        // Never try to bypass background-activity or runtime-permission rules.
        val response = when {
            result.requiresBrightnessPermission ->
                result.response +
                    " روشنی کی اجازت دینے کے لیے ادراک کھولیں، پھر یہی حکم دوبارہ کہیں۔"

            result.requiresCameraPermission ->
                result.response +
                    " ٹارچ کی اجازت دینے کے لیے ادراک کھولیں، پھر یہی حکم دوبارہ کہیں۔"

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
                sendStatus("پس منظر میں آف لائن سننا فعال ہے")
            } else {
                sendStatus("پس منظر میں سننا دوبارہ شروع نہیں ہو سکا")
            }
        } catch (error: Exception) {
            sendStatus(
                "سننا دوبارہ شروع کرنے میں خرابی: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
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
            sendStatus("پس منظر میں آواز سننا بند ہو گیا")
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
                "ادراک کی پس منظر آواز",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "یہ اطلاع اس وقت دکھائی جاتی ہے جب ادراک پس منظر میں سن رہا ہو"
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
            .setContentTitle("ادراک سن رہا ہے")
            .setContentText(
                if (pendingCommand.isNullOrBlank()) {
                    "آف لائن آواز فعال ہے۔ سننا بند کرنے کے لیے توقف دبائیں۔"
                } else {
                    "حکم تیار ہے۔ اسے جاری رکھنے کے لیے ادراک کی اطلاع میں کھولنے کا اختیار دبائیں۔"
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
            builder.addAction(android.R.drawable.ic_media_play, "کھولیں اور چلائیں", commandPendingIntent)
        }

        builder.addAction(android.R.drawable.ic_media_pause, "بند کریں", stopPendingIntent)
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
