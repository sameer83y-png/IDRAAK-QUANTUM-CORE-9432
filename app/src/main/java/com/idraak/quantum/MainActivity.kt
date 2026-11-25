
package com.idraak.quantum

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.idraak.quantum.engine.OfflineBrain

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                IdraakHomeScreen()
            }
        }
    }
}

@Composable
fun IdraakHomeScreen() {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val backgroundColor = Color(0xFF050B18)
    val blue = Color(0xFF087DFF)

    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showMemoryDashboard by remember { mutableStateOf(false) }

    var voiceStatus by remember {
        mutableStateOf("OFFLINE BRAIN READY")
    }

    var isListening by remember { mutableStateOf(false) }
    var isContinuousVoiceChat by remember { mutableStateOf(false) }
    var isBackgroundVoiceActive by remember { mutableStateOf(false) }

    var recognizedSpeech by remember {
        mutableStateOf<String?>(null)
    }

    var microphonePermissionRequest by remember {
        mutableStateOf(false)
    }

    var pendingTorchCommand by remember {
        mutableStateOf<Pair<String, Boolean>?>(null)
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hello Boss! Main Idraak hoon. Mera Offline Brain ready hai. Aap mujhse baat kar sakte hain.",
                false
            )
        )
    }

    val controllerReference = remember {
        arrayOfNulls<VoiceController>(1)
    }

    val voiceController = remember(context) {
        VoiceController(
            context = context,
            onSpeechResult = { result ->
                mainHandler.post {
                    recognizedSpeech = result
                    isListening = false
                }
            },
            onStatusChanged = { status ->
                mainHandler.post {
                    voiceStatus = status

                    if (
                        status.contains("Listening", true) ||
                        status.contains("Recognizing", true) ||
                        status.contains("Processing", true) ||
                        status.contains("Ready.", true)
                    ) {
                        isListening = true
                    } else if (
                        status.contains("recognized", true) ||
                        status.contains("finished", true) ||
                        status.contains("Speaking", true) ||
                        status.contains("error", true) ||
                        status.contains("failed", true) ||
                        status.contains("stopped", true) ||
                        status.contains("timeout", true) ||
                        status.contains("No speech", true)
                    ) {
                        isListening = false
                    }
                }
            },
            onSpeechFinished = {
                mainHandler.post {
                    if (isContinuousVoiceChat) {
                        val permissionGranted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                        if (permissionGranted) {
                            val started =
                                controllerReference[0]?.startListening() == true

                            isListening = started

                            if (started) {
                                voiceStatus =
                                    "VOICE CHAT ACTIVE • LISTENING"
                            }
                        } else {
                            isContinuousVoiceChat = false
                            isListening = false
                            voiceStatus =
                                "Microphone permission required"
                        }
                    }
                }
            }
        ).also {
            controllerReference[0] = it
        }
    }

    var selectedVoiceLanguage by remember(voiceController) {
        mutableStateOf(voiceController.getRecognitionLanguage())
    }

    var selectedSpeechRate by remember(voiceController) {
        mutableStateOf(voiceController.getSpeechRate())
    }

    DisposableEffect(voiceController) {
        onDispose {
            isContinuousVoiceChat = false
            voiceController.shutdown()
            controllerReference[0] = null
        }
    }

    fun stopVoiceChat() {
        isContinuousVoiceChat = false
        isListening = false
        voiceController.cancelListening()
        voiceController.stopSpeaking()
        voiceStatus = "VOICE CHAT STOPPED"
    }

    fun stopBackgroundVoiceService() {
        try {
            val intent = Intent(
                context,
                VoiceForegroundService::class.java
            ).apply {
                action = VoiceForegroundService.ACTION_STOP
            }

            context.startService(intent)
            isBackgroundVoiceActive = false
            voiceStatus = "BACKGROUND VOICE STOPPING"
        } catch (error: Exception) {
            voiceStatus =
                "Could not stop background voice: " +
                    (error.localizedMessage ?: "Unknown error")
        }
    }

    fun startBackgroundVoiceService() {
        try {
            val intent = Intent(
                context,
                VoiceForegroundService::class.java
            ).apply {
                action = VoiceForegroundService.ACTION_START
            }

            ContextCompat.startForegroundService(
                context,
                intent
            )

            isBackgroundVoiceActive = true
            voiceStatus = "BACKGROUND VOICE STARTING"
        } catch (error: Exception) {
            isBackgroundVoiceActive = false
            voiceStatus =
                "Background voice start failed: " +
                    (error.localizedMessage ?: "Unknown error")
        }
    }

    fun speakDashboardSuggestions() {
        val suggestions = SmartSuggestionEngine.build(
            context = context.applicationContext,
            voiceActive = true,
            lastUserMessage = messages.lastOrNull { it.isUser }?.text.orEmpty()
        )
        val firstSuggestion = suggestions.firstOrNull()
        val spokenSummary = if (firstSuggestion != null) {
            "Idraak सुझाव: ${firstSuggestion.title}. ${firstSuggestion.detail}."
        } else {
            "Idraak तैयार है। आप बोलकर कमांड दे सकते हैं।"
        }
        voiceController.speak(spokenSummary)
        voiceStatus = "OFFLINE SUGGESTION SPEAKING"
    }

    fun startVoiceChat() {
        if (isBackgroundVoiceActive) {
            stopBackgroundVoiceService()
            voiceStatus =
                "Background voice stopped. Tap START VOICE CHAT again."
            return
        }

        isContinuousVoiceChat = true

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            speakDashboardSuggestions()
            isListening = false
            voiceStatus = "VOICE SESSION STARTING • OFFLINE"
        } else {
            voiceStatus = "Requesting microphone permission"
            microphonePermissionRequest = true
        }
    }

    fun executeTorchCommand(
        originalMessage: String,
        enabled: Boolean
    ) {
        val result = OfflineCommandRouter.executeTorch(
            context = context.applicationContext,
            originalCommand = originalMessage,
            enabled = enabled
        )

        messages.add(ChatMessage(result.response, false))
        voiceController.speak(result.response)
        voiceStatus = if (result.requiresCameraPermission) {
            "CAMERA PERMISSION REQUIRED"
        } else {
            "FLASHLIGHT CONTROL PROCESSED"
        }
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val pending = pendingTorchCommand
            pendingTorchCommand = null

            if (pending != null) {
                if (granted) {
                    executeTorchCommand(
                        originalMessage = pending.first,
                        enabled = pending.second
                    )
                } else {
                    val response =
                        "Camera permission नहीं मिली। टॉर्च कंट्रोल नहीं किया गया।"

                    messages.add(ChatMessage(response, false))
                    voiceController.speak(response)
                    voiceStatus = "CAMERA PERMISSION DENIED"
                }
            }
        }

    fun sendMessage(messageText: String = inputText) {
        val message = messageText.trim()
        if (message.isEmpty()) return

        messages.add(ChatMessage(message, true))
        inputText = ""

        if (VoiceSessionCommandHandler.isStopListeningCommand(message)) {
            isContinuousVoiceChat = false
            isListening = false
            voiceController.cancelListening()

            if (isBackgroundVoiceActive) {
                val stopIntent = Intent(
                    context,
                    VoiceForegroundService::class.java
                ).apply {
                    action = VoiceForegroundService.ACTION_STOP
                }
                try {
                    context.startService(stopIntent)
                } catch (_: Exception) {
                    voiceStatus = "Could not stop background voice service"
                }
                isBackgroundVoiceActive = false
            }

            val response = "ठीक है। Idraak की voice listening बंद कर दी गई है।"
            messages.add(ChatMessage(response, false))
            voiceStatus = "VOICE LISTENING STOPPED"
            voiceController.speak(response)
            return
        }

        val result = try {
            OfflineCommandRouter.process(
                context = context.applicationContext,
                command = message
            )
        } catch (error: Exception) {
            OfflineCommandRouter.Result(
                response = "Offline कमांड पूरी नहीं हो सकी: " +
                    (error.localizedMessage ?: "Unknown error"),
                intent = "ERROR"
            )
        }

        if (result.requiresBrightnessPermission) {
            messages.add(ChatMessage(result.response, false))
            voiceController.speak(result.response)
            voiceStatus = "BRIGHTNESS PERMISSION REQUIRED"
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (error: Exception) {
                voiceStatus = "COULD NOT OPEN BRIGHTNESS PERMISSION SETTINGS"
            }
            return
        }

        if (result.requiresCameraPermission) {
            val enabled = result.torchEnabled

            if (enabled == null) {
                messages.add(ChatMessage(result.response, false))
                voiceController.speak(result.response)
                voiceStatus = "FLASHLIGHT COMMAND NEEDS ON/OFF"
                return
            }

            pendingTorchCommand = message to enabled
            messages.add(ChatMessage(result.response, false))
            voiceController.speak(result.response)
            voiceStatus = "REQUESTING CAMERA PERMISSION"

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
            return
        }

        messages.add(ChatMessage(result.response, false))
        voiceController.speak(result.response)

        voiceStatus = when (result.intent) {
            "SAFE_CALL" -> "SAFE CALL CONTROLLER OPENED"
            "FLASHLIGHT" -> "FLASHLIGHT CONTROL PROCESSED"
            "ERROR" -> "OFFLINE COMMAND FAILED"
            else -> "OFFLINE COMMAND ROUTER ACTIVE"
        }
    }

    LaunchedEffect(recognizedSpeech) {
        val spokenText = recognizedSpeech

        if (!spokenText.isNullOrBlank()) {
            inputText = spokenText
            sendMessage(spokenText)
            recognizedSpeech = null
        }
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted && isContinuousVoiceChat) {
                speakDashboardSuggestions()
                isListening = false
                voiceStatus = "VOICE SESSION STARTING • OFFLINE"
            } else {
                isContinuousVoiceChat = false
                isListening = false
                voiceStatus = "Microphone permission denied"
            }
        }

    LaunchedEffect(microphonePermissionRequest) {
        if (microphonePermissionRequest) {
            microphonePermissionRequest = false
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    val backgroundNotificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            // Android allows a foreground service to run even if
            // notification permission is denied. The service will
            // still appear in Android's active-apps interface.
            startBackgroundVoiceService()
        }

    val backgroundMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val notificationGranted =
                    Build.VERSION.SDK_INT < 33 ||
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED

                if (
                    Build.VERSION.SDK_INT >= 33 &&
                    !notificationGranted
                ) {
                    backgroundNotificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    startBackgroundVoiceService()
                }
            } else {
                isBackgroundVoiceActive = false
                voiceStatus =
                    "Microphone permission denied. Background voice is OFF."
            }
        }

    fun requestBackgroundVoiceStart() {
        if (isBackgroundVoiceActive) {
            stopBackgroundVoiceService()
            return
        }

        if (isContinuousVoiceChat) {
            stopVoiceChat()
            voiceStatus =
                "Voice Chat stopped. Tap BACKGROUND VOICE again to start."
            return
        }

        val microphoneGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!microphoneGranted) {
            backgroundMicrophonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
            return
        }

        val notificationGranted =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

        if (
            Build.VERSION.SDK_INT >= 33 &&
            !notificationGranted
        ) {
            backgroundNotificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            startBackgroundVoiceService()
        }
    }

    val backgroundVoiceReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(
                receiverContext: Context?,
                intent: Intent?
            ) {
                when (intent?.action) {
                    VoiceForegroundService.ACTION_STATUS -> {
                        val status =
                            intent.getStringExtra(
                                VoiceForegroundService.EXTRA_STATUS
                            ) ?: return

                        voiceStatus = status

                        if (
                            status.contains("stopped", true) ||
                            status.contains("permission required", true) ||
                            status.contains("error", true) ||
                            status.contains("failed", true) ||
                            status.contains("could not start", true)
                        ) {
                            isBackgroundVoiceActive = false
                        } else if (
                            status.contains("starting", true) ||
                            status.contains("listening", true) ||
                            status.contains("loading", true) ||
                            status.contains("recognition engine", true)
                        ) {
                            isBackgroundVoiceActive = true
                        }
                    }

                    VoiceForegroundService.ACTION_COMMAND -> {
                        val command =
                            intent.getStringExtra(
                                VoiceForegroundService.EXTRA_TEXT
                            )

                        if (!command.isNullOrBlank()) {
                            messages.add(
                                ChatMessage(command, true)
                            )
                        }
                    }

                    VoiceForegroundService.ACTION_RESPONSE -> {
                        val response =
                            intent.getStringExtra(
                                VoiceForegroundService.EXTRA_TEXT
                            )

                        if (!response.isNullOrBlank()) {
                            messages.add(
                                ChatMessage(response, false)
                            )
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(context, backgroundVoiceReceiver) {
        val filter = IntentFilter().apply {
            addAction(VoiceForegroundService.ACTION_STATUS)
            addAction(VoiceForegroundService.ACTION_COMMAND)
            addAction(VoiceForegroundService.ACTION_RESPONSE)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                backgroundVoiceReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(
                backgroundVoiceReceiver,
                filter
            )
        }

        onDispose {
            try {
                context.unregisterReceiver(backgroundVoiceReceiver)
            } catch (_: Exception) {
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "core")

    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        when {
            showMemoryDashboard -> {
                MemoryDashboardScreen(
                    onBack = {
                        showMemoryDashboard = false
                    }
                )
            }

            showSettings -> {
                SettingsScreen(
                    onBack = {
                        showSettings = false
                    },
                    onOpenMemory = {
                        showMemoryDashboard = true
                    },
                    selectedVoiceLanguage = selectedVoiceLanguage,
                    onVoiceLanguageSelected = { language ->
                        selectedVoiceLanguage = language

                        if (isBackgroundVoiceActive) {
                            // Change the model inside the running foreground service.
                            // It restarts recognition locally without stopping the service.
                            val intent = Intent(
                                context,
                                VoiceForegroundService::class.java
                            ).apply {
                                action = VoiceForegroundService.ACTION_SET_LANGUAGE
                                putExtra(
                                    VoiceForegroundService.EXTRA_LANGUAGE,
                                    language.name
                                )
                            }

                            try {
                                context.startService(intent)
                                voiceStatus = "BACKGROUND VOICE LANGUAGE CHANGING"
                            } catch (error: Exception) {
                                voiceStatus =
                                    "Could not change background voice language: " +
                                        (error.localizedMessage ?: "Unknown error")
                            }
                        } else {
                            val resumeForegroundVoiceChat =
                                isContinuousVoiceChat && isListening

                            isContinuousVoiceChat = false
                            isListening = false
                            voiceController.cancelListening()

                            voiceController.setRecognitionLanguage(
                                language,
                                resumeListening = resumeForegroundVoiceChat
                            )

                            if (resumeForegroundVoiceChat) {
                                isContinuousVoiceChat = true
                                isListening = voiceController.startListening()
                                voiceStatus = if (isListening) {
                                    "VOICE CHAT ACTIVE • LANGUAGE CHANGED"
                                } else {
                                    "Language saved • voice restart pending"
                                }
                            } else {
                                voiceStatus = when (language) {
                                    VoskModelStore.VoskLanguage.HINDI ->
                                        "HINDI OFFLINE MODEL SELECTED"

                                    VoskModelStore.VoskLanguage.INDIAN_ENGLISH ->
                                        "INDIAN ENGLISH OFFLINE MODEL SELECTED"
                                }
                            }
                        }
                    },
                    selectedSpeechRate = selectedSpeechRate,
                    onSpeechRateChanged = { rate ->
                        selectedSpeechRate = rate.coerceIn(0.75f, 1.5f)
                        voiceController.setSpeechRate(selectedSpeechRate)
                    }
                )
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF101D38),
                                    backgroundColor,
                                    Color(0xFF02050D)
                                )
                            )
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "IDRAAK",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 5.sp
                            )

                            Text(
                                text = "QUANTUM CORE",
                                color = Color(0xFF00B7FF),
                                fontSize = 11.sp,
                                letterSpacing = 4.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                if (isContinuousVoiceChat) {
                                    stopVoiceChat()
                                }
                                showSettings = true
                            }
                        ) {
                            Text(
                                "SETTINGS",
                                color = Color(0xFF65C8FF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .scale(pulse)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF8BEAFF),
                                        Color(0xFF087DFF),
                                        Color(0xFF06152D)
                                    )
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "IQ",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "OFFLINE SYSTEM ONLINE",
                        color = Color(0xFF43E8B2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = voiceStatus,
                        color = if (
                            isListening ||
                            isContinuousVoiceChat ||
                            isBackgroundVoiceActive
                        ) {
                            Color(0xFF43E8B2)
                        } else {
                            Color(0xFF8EA6C9)
                        },
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isContinuousVoiceChat || isBackgroundVoiceActive) {
                                "IDRAAK VOICE CONTROL ACTIVE"
                            } else {
                                "VOICE-ONLY CONTROL"
                            },
                            color = Color(0xFF65C8FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "बोलकर कहें: ऐप खोलो, कॉल करो, कैमरा खोलो या फोन की सेटिंग बदलो।",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "सुझाव Idraak आवाज़ में बताएगा। चैट बॉक्स और टेक्स्ट चैट उपलब्ध नहीं हैं।",
                            color = Color(0xFF8EA6C9),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isContinuousVoiceChat) {
                                    stopVoiceChat()
                                } else {
                                    startVoiceChat()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor =
                                    if (isContinuousVoiceChat) {
                                        Color(0xFFB83250)
                                    } else {
                                        Color(0xFF087DFF)
                                    }
                            )
                        ) {
                            Text(
                                if (isContinuousVoiceChat) {
                                    "STOP VOICE CHAT"
                                } else {
                                    "🎙 START VOICE CHAT"
                                }
                            )
                        }

                        Button(
                            onClick = { stopVoiceChat() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF263B59)
                            )
                        ) {
                            Text("STOP VOICE")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            requestBackgroundVoiceStart()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                if (isBackgroundVoiceActive) {
                                    Color(0xFFB83250)
                                } else {
                                    Color(0xFF087DFF)
                                }
                        )
                    ) {
                        Text(
                            if (isBackgroundVoiceActive) {
                                "STOP BACKGROUND VOICE"
                            } else {
                                "START BACKGROUND VOICE"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Background listening uses the microphone while active. Use the notification STOP button to end it.",
                        color = Color(0xFF8EA6C9),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            top = 4.dp,
                            bottom = 4.dp
                        )
                    )

                    Text(
                        text = "IDRAAK OFFLINE BRAIN • LOCAL MODE",
                        color = Color(0xFF7B91B5),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenMemory: () -> Unit,
    selectedVoiceLanguage: VoskModelStore.VoskLanguage,
    onVoiceLanguageSelected: (VoskModelStore.VoskLanguage) -> Unit,
    selectedSpeechRate: Float,
    onSpeechRateChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    fun isScreenControlEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val expectedComponent = ComponentName(
            context,
            com.idraak.quantum.engine.ScreenControlService::class.java
        ).flattenToString()

        return enabledServices.split(':').any {
            it.equals(expectedComponent, ignoreCase = true)
        }
    }

    var screenControlEnabled by remember {
        mutableStateOf(isScreenControlEnabled())
    }
    var accessibilitySettingsError by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                screenControlEnabled = isScreenControlEnabled()
            }
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            activity?.lifecycle?.removeObserver(observer)
        }
    }

    val memoryCount = remember {
        OfflineBrain.getExperienceCount(context)
    }

    val recentExperiences = remember {
        OfflineBrain.getRecentExperiences(
            context = context,
            limit = 10
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF101D38),
                        Color(0xFF050B18),
                        Color(0xFF02050D)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(
                "← BACK TO IDRAAK",
                color = Color(0xFF65C8FF)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SYSTEM SETTINGS",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "IDRAAK QUANTUM CORE • OFFLINE CONFIGURATION",
            color = Color(0xFF8EA6C9),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsStatusCard(
            title = "AI BRAIN",
            status = "OFFLINE BRAIN ACTIVE",
            detail = "Idraak runs with its own offline brain. External AI connections and internet services are disabled."
        )

        SettingsStatusCard(
            title = "LOCAL EXPERIENCE MEMORY",
            status = "$memoryCount RECORDS SAVED",
            detail = "Experiences are stored locally on this device. This is an initial memory foundation, not yet a complete long-term learning system."
        )

        SettingsStatusCard(
            title = "VOICE SYSTEM",
            status = "OFFLINE VOSK + ANDROID TEXT TO SPEECH",
            detail = "Hindi and Indian English speech recognition models are bundled with the app. The selected Vosk model runs locally; Android Text-to-Speech is used for voice output."
        )

        Text(
            text = "OFFLINE SPEECH LANGUAGE",
            color = Color(0xFF00B7FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose the language Idraak should recognize while listening.",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                onVoiceLanguageSelected(
                    VoskModelStore.VoskLanguage.HINDI
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (
                        selectedVoiceLanguage ==
                        VoskModelStore.VoskLanguage.HINDI
                    ) {
                        Color(0xFF087DFF)
                    } else {
                        Color(0xFF263B59)
                    }
            )
        ) {
            Text("HINDI OFFLINE")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                onVoiceLanguageSelected(
                    VoskModelStore.VoskLanguage.INDIAN_ENGLISH
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor =
                    if (
                        selectedVoiceLanguage ==
                        VoskModelStore.VoskLanguage.INDIAN_ENGLISH
                    ) {
                        Color(0xFF087DFF)
                    } else {
                        Color(0xFF263B59)
                    }
            )
        ) {
            Text("INDIAN ENGLISH OFFLINE")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "VOICE OUTPUT SPEED",
            color = Color(0xFF00B7FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Adjust how fast Idraak speaks. This setting is saved on this device.",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )

        Text(
            text = String.format(java.util.Locale.US, "%.2fx", selectedSpeechRate),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Slider(
            value = selectedSpeechRate,
            onValueChange = onSpeechRateChanged,
            valueRange = 0.75f..1.5f
        )

        Spacer(modifier = Modifier.height(18.dp))

        SettingsStatusCard(
            title = "PHONE CONTROLLERS",
            status = "APP CONTROL • SCREEN CONTROL • FLASHLIGHT • CAMERA • VOLUME • BRIGHTNESS",
            detail = "Phone controls run locally. Android may require you to enable special access or grant a permission before a control can work."
        )

        SettingsStatusCard(
            title = "SCREEN CONTROL ACCESS",
            status = if (screenControlEnabled) {
                "ACCESSIBILITY SERVICE ENABLED"
            } else {
                "ACCESSIBILITY PERMISSION NEEDED"
            },
            detail = "This permission lets Idraak inspect visible screen labels locally for your requested tap, and perform navigation, swipe and scroll actions. Screen events are not saved or transmitted."
        )

        Button(
            onClick = {
                accessibilitySettingsError = null
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                } catch (_: Exception) {
                    accessibilitySettingsError =
                        "Android Accessibility Settings नहीं खुल सकीं। Settings ऐप से Accessibility खोलें।"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (screenControlEnabled) {
                    Color(0xFF263B59)
                } else {
                    Color(0xFF087DFF)
                }
            )
        ) {
            Text(
                if (screenControlEnabled) {
                    "MANAGE SCREEN CONTROL ACCESS"
                } else {
                    "ENABLE IDRAAK SCREEN CONTROL"
                },
                fontWeight = FontWeight.Bold
            )
        }

        accessibilitySettingsError?.let { error ->
            Text(
                text = error,
                color = Color(0xFFFFB4AB),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "MEMORY MANAGEMENT",
            color = Color(0xFF00B7FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View, search and manage Idraak's saved local memory records.",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onOpenMemory,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF087DFF)
            )
        ) {
            Text(
                text = "OPEN MEMORY DASHBOARD",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "RECENT MEMORY RECORDS",
            color = Color(0xFF00B7FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (recentExperiences.isEmpty()) {
            Text(
                text = "No experience records saved yet.",
                color = Color(0xFF9CB0CC),
                fontSize = 12.sp
            )
        } else {
            recentExperiences.reversed().forEach { experience ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    color = Color(0xFF18243A),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = experience,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "MORE SETTINGS WILL BE ADDED IN SEPARATE MODULES.",
            color = Color(0xFF7B91B5),
            fontSize = 10.sp
        )
    }
}

@Composable
fun SettingsStatusCard(
    title: String,
    status: String,
    detail: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = Color(0xFF14243D),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF65C8FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = status,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = detail,
                color = Color(0xFF9CB0CC),
                fontSize = 12.sp
            )
        }
    }
}
