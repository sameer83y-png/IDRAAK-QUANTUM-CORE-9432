
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
import com.idraak.quantum.engine.LocalMemoryStore

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

enum class IdraakTheme(
    val title: String,
    val backgroundColor: Long,
    val surfaceColor: Long,
    val accentColor: Long,
    val glowColor: Long
) {
    QUANTUM_BLUE("نیلا", 0xFF050B18, 0xFF101D38, 0xFF087DFF, 0xFF8BEAFF),
    EMERALD("زمرد", 0xFF04130F, 0xFF0C2A20, 0xFF00A978, 0xFF75F0C5),
    VIOLET("بنفشی", 0xFF10091C, 0xFF24143A, 0xFF986BFF, 0xFFD0B8FF),
    AMBER("عنبر", 0xFF171006, 0xFF33230D, 0xFFE59A27, 0xFFFFD27D);

    fun save(context: Context) {
        context.applicationContext
            .getSharedPreferences("idraak_visual_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("selected_theme", name)
            .apply()
    }

    companion object {
        fun load(context: Context): IdraakTheme {
            val saved = context.applicationContext
                .getSharedPreferences("idraak_visual_preferences", Context.MODE_PRIVATE)
                .getString("selected_theme", QUANTUM_BLUE.name)
            return entries.firstOrNull { it.name == saved } ?: QUANTUM_BLUE
        }
    }
}

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

    var selectedTheme by remember(context) {
        mutableStateOf(IdraakTheme.load(context))
    }
    val backgroundColor = Color(selectedTheme.backgroundColor)
    val accentColor = Color(selectedTheme.accentColor)
    val glowColor = Color(selectedTheme.glowColor)

    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showMemoryDashboard by remember { mutableStateOf(false) }

    var voiceStatus by remember {
        mutableStateOf("آف لائن ذہن تیار ہے")
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
                "السلام علیکم! میں ادراک ہوں۔ میرا آف لائن ذہن تیار ہے۔ آپ مجھ سے بات کر سکتے ہیں۔",
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
                        status.contains("سن رہا ہوں", true) ||
                        status.contains("سمجھ رہا ہوں", true) ||
                        status.contains("عمل جاری ہے", true) ||
                        status.contains("تیار ہوں۔", true)
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
                                    "آواز سننے کا عمل جاری ہے"
                            }
                        } else {
                            isContinuousVoiceChat = false
                            isListening = false
                            voiceStatus =
                                "مائیکروفون کی اجازت درکار ہے"
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
        voiceStatus = "آواز سننا بند ہے"
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
            voiceStatus = "پس منظر میں سننا بند ہو رہا ہے"
        } catch (error: Exception) {
            voiceStatus =
                "پس منظر میں سننا بند نہیں ہو سکا: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
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
            voiceStatus = "پس منظر میں سننا شروع ہو رہا ہے"
        } catch (error: Exception) {
            isBackgroundVoiceActive = false
            voiceStatus =
                "پس منظر میں آواز شروع نہیں ہو سکی: " +
                    (error.localizedMessage ?: "نامعلوم خرابی")
        }
    }

    fun speakDashboardSuggestions() {
        val suggestions = SmartSuggestionEngine.build(
            context = context.applicationContext,
            voiceActive = true,
            lastUserMessage = messages.lastOrNull { it.isUser }?.text.orEmpty()
        )
        val spokenSummary = if (suggestions.isNotEmpty()) {
            "ادراک کی تجاویز۔ " + suggestions.joinToString(separator = "۔ ") { suggestion ->
                "${suggestion.title}: ${suggestion.detail}"
            } + "۔ آپ ان میں سے کوئی کام بول کر بتا سکتے ہیں۔"
        } else {
            "ادراک تیار ہے۔ آپ بول کر حکم دے سکتے ہیں۔"
        }
        voiceController.speak(spokenSummary)
        voiceStatus = "تجویز سنائی جا رہی ہے"
    }

    fun startVoiceChat() {
        if (isBackgroundVoiceActive) {
            stopBackgroundVoiceService()
            voiceStatus =
                "پس منظر میں سننا بند ہے۔ دوبارہ شروع کرنے کے لیے آواز کا بٹن دبائیں۔"
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
            voiceStatus = "آف لائن آواز شروع ہو رہی ہے"
        } else {
            voiceStatus = "مائیکروفون کی اجازت مانگی جا رہی ہے"
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
            "کیمرے کی اجازت درکار ہے"
        } else {
            "ٹارچ کا حکم مکمل ہوا"
        }
    }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            PermissionRequestGate.markResult(context, Manifest.permission.CAMERA, granted)
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
                        "کیمرے کی اجازت نہیں ملی۔ ادراک بار بار نہیں پوچھے گا۔ اجازت دینے کے لیے فون کی ایپ ترتیبات کھولیں۔"

                    messages.add(ChatMessage(response, false))
                    voiceController.speak(response)
                    voiceStatus = "کیمرے کی اجازت مسترد ہوئی"
                }
            }
        }

    fun sendMessage(messageText: String = inputText) {
        val message = messageText.trim()
        if (message.isEmpty()) return

        // Stop Urdu microphone capture before processing a command or speaking.
        // This prevents Idraak from hearing its own TTS output as a new command.
        voiceController.cancelListening()

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
                    voiceStatus = "پس منظر میں آواز بند نہیں ہو سکی"
                }
                isBackgroundVoiceActive = false
            }

            val response = "ٹھیک ہے، ادراک نے سننا بند کر دیا ہے۔"
            messages.add(ChatMessage(response, false))
            voiceStatus = "آواز سننا بند ہے"
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
                response = "آف لائن حکم مکمل نہیں ہو سکا: " +
                    (error.localizedMessage ?: "نامعلوم خرابی"),
                intent = "ERROR"
            )
        }

        if (result.requiresBrightnessPermission) {
            messages.add(ChatMessage(result.response, false))
            voiceController.speak(result.response)
            voiceStatus = "اسکرین کی روشنی کی اجازت درکار ہے"
            if (PermissionRequestGate.tryMarkSpecialSettingsOpened(context, "modify_system_settings")) {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (error: Exception) {
                    voiceStatus = "روشنی کی اجازت کی ترتیبات نہیں کھل سکیں"
                }
            } else {
                val response = "روشنی کی خصوصی اجازت پہلے مانگی جا چکی ہے۔ فون کی ترتیبات میں ادراک کے لیے نظام کی ترتیبات بدلنے کی اجازت فعال کریں۔"
                messages.add(ChatMessage(response, false))
                voiceController.speak(response)
                voiceStatus = "روشنی کی اجازت کے لیے ترتیبات کھولیں"
            }
            return
        }

        if (result.requiresCameraPermission) {
            val enabled = result.torchEnabled

            if (enabled == null) {
                messages.add(ChatMessage(result.response, false))
                voiceController.speak(result.response)
                voiceStatus = "بتائیں ٹارچ جلانی ہے یا بجھانی ہے"
                return
            }

            pendingTorchCommand = message to enabled
            messages.add(ChatMessage(result.response, false))
            voiceController.speak(result.response)
            voiceStatus = "کیمرے کی اجازت مانگی جا رہی ہے"

            if (PermissionRequestGate.tryMarkRequested(context, Manifest.permission.CAMERA)) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                val response = "کیمرے کی اجازت پہلے مانگی جا چکی ہے۔ فون کی ایپ ترتیبات میں اسے فعال کریں۔"
                messages.add(ChatMessage(response, false))
                voiceController.speak(response)
                voiceStatus = "کیمرے کی اجازت کے لیے ایپ ترتیبات کھولیں"
            }
            return
        }

        messages.add(ChatMessage(result.response, false))
        voiceController.speak(result.response)

        voiceStatus = when (result.intent) {
            "SAFE_CALL" -> "محفوظ کال کا نظام کھل گیا"
            "FLASHLIGHT" -> "ٹارچ کا حکم مکمل ہوا"
            "ERROR" -> "آف لائن حکم ناکام ہوا"
            else -> "آف لائن حکم کا نظام فعال ہے"
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
            PermissionRequestGate.markResult(context, Manifest.permission.RECORD_AUDIO, granted)
            if (granted && isContinuousVoiceChat) {
                speakDashboardSuggestions()
                isListening = false
                voiceStatus = "آف لائن آواز شروع ہو رہی ہے"
            } else {
                isContinuousVoiceChat = false
                isListening = false
                voiceStatus = "مائیکروفون کی اجازت مسترد ہوئی"
            }
        }

    LaunchedEffect(microphonePermissionRequest) {
        if (microphonePermissionRequest) {
            microphonePermissionRequest = false
            if (PermissionRequestGate.tryMarkRequested(context, Manifest.permission.RECORD_AUDIO)) {
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                isContinuousVoiceChat = false
                val response = "مائیکروفون کی اجازت پہلے مانگی جا چکی ہے۔ فون کی ایپ ترتیبات میں اسے فعال کریں۔"
                voiceController.speak(response)
                voiceStatus = "مائیکروفون کی اجازت کے لیے ایپ ترتیبات کھولیں"
            }
        }
    }

    val backgroundNotificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            PermissionRequestGate.markResult(context, Manifest.permission.POST_NOTIFICATIONS, granted)
            // Android permits the foreground service even without notification permission.
            startBackgroundVoiceService()
        }

    val backgroundMicrophonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            PermissionRequestGate.markResult(context, Manifest.permission.RECORD_AUDIO, granted)
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
                    if (PermissionRequestGate.tryMarkRequested(context, Manifest.permission.POST_NOTIFICATIONS)) {
                        backgroundNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        startBackgroundVoiceService()
                    }
                } else {
                    startBackgroundVoiceService()
                }
            } else {
                isBackgroundVoiceActive = false
                voiceStatus =
                    "مائیکروفون کی اجازت مسترد ہوئی۔ پس منظر میں سننا بند ہے۔"
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
                "آواز سننا بند ہے۔ دوبارہ شروع کرنے کے لیے پس منظر آواز کا بٹن دبائیں۔"
            return
        }

        val microphoneGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!microphoneGranted) {
            if (PermissionRequestGate.tryMarkRequested(context, Manifest.permission.RECORD_AUDIO)) {
                backgroundMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                val response = "مائیکروفون کی اجازت پہلے مانگی جا چکی ہے۔ فون کی ایپ ترتیبات میں اسے فعال کریں۔"
                voiceController.speak(response)
                voiceStatus = "مائیکروفون کی اجازت کے لیے ایپ ترتیبات کھولیں"
            }
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
            if (PermissionRequestGate.tryMarkRequested(context, Manifest.permission.POST_NOTIFICATIONS)) {
                backgroundNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startBackgroundVoiceService()
            }
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

        ContextCompat.registerReceiver(
            context,
            backgroundVoiceReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
                    },
                    accentColor = accentColor
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
                                voiceStatus = "آواز کی زبان تبدیل ہو رہی ہے"
                            } catch (error: Exception) {
                                voiceStatus =
                                    "پس منظر کی آواز کی زبان تبدیل نہیں ہو سکی: " +
                                        (error.localizedMessage ?: "نامعلوم خرابی")
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
                                    "اردو آواز فعال ہے"
                                } else {
                                    "زبان محفوظ ہو گئی ہے"
                                }
                            } else {
                                voiceStatus = "اردو آف لائن آواز کا نظام منتخب ہے"
                            }
                        }
                    },
                    selectedSpeechRate = selectedSpeechRate,
                    onSpeechRateChanged = { rate ->
                        selectedSpeechRate = rate.coerceIn(0.75f, 1.5f)
                        voiceController.setSpeechRate(selectedSpeechRate)
                    },
                    selectedTheme = selectedTheme,
                    onThemeSelected = { theme ->
                        selectedTheme = theme
                        theme.save(context)
                    }
                )
            }

            else -> {
                val dashboardSuggestions = SmartSuggestionEngine.build(
                    context = context.applicationContext,
                    voiceActive = isContinuousVoiceChat || isBackgroundVoiceActive,
                    lastUserMessage = messages.lastOrNull { it.isUser }?.text.orEmpty()
                )

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
                                text = "ادراک",
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 5.sp
                            )

                            Text(
                                text = "کوانٹم مرکز",
                                color = accentColor,
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
                                "ترتیبات",
                                color = accentColor,
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
                                        glowColor,
                                        accentColor,
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
                        text = "آف لائن نظام فعال ہے",
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
                                "ادراک کا صوتی کنٹرول فعال ہے"
                            } else {
                                "صرف آواز کے ذریعے کنٹرول"
                            },
                            color = accentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "بول کر کہیں: ایپ کھولو، کال کرو، کیمرہ کھولو یا فون کی ترتیبات بدلو۔",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ادراک تجاویز آواز میں بتائے گا۔ یہاں تحریری گفتگو دستیاب نہیں۔",
                            color = Color(0xFF8EA6C9),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    SmartSuggestionsPanel(
                        suggestions = dashboardSuggestions,
                        onSuggestionSelected = { suggestionCommand ->
                            sendMessage(suggestionCommand)
                        }
                    )

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
                                        accentColor
                                    }
                            )
                        ) {
                            Text(
                                if (isContinuousVoiceChat) {
                                    "آواز کا کنٹرول بند کریں"
                                } else {
                                    "🎙 آواز کا کنٹرول شروع کریں"
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
                            Text("آواز بند کریں")
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
                                    accentColor
                                }
                        )
                    ) {
                        Text(
                            if (isBackgroundVoiceActive) {
                                "پس منظر میں سننا بند کریں"
                            } else {
                                "پس منظر میں سننا شروع کریں"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "پس منظر میں سننے کے دوران مائیکروفون استعمال ہوتا ہے۔ بند کرنے کے لیے اطلاع میں توقف کا بٹن دبائیں۔",
                        color = Color(0xFF8EA6C9),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            top = 4.dp,
                            bottom = 4.dp
                        )
                    )

                    Text(
                        text = "ادراک کا آف لائن ذہن • مقامی نظام",
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
    selectedVoiceLanguage: UrduVoiceLanguage,
    onVoiceLanguageSelected: (UrduVoiceLanguage) -> Unit,
    selectedSpeechRate: Float,
    onSpeechRateChanged: (Float) -> Unit,
    selectedTheme: IdraakTheme,
    onThemeSelected: (IdraakTheme) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val themeBackgroundColor = Color(selectedTheme.backgroundColor)
    val themeAccentColor = Color(selectedTheme.accentColor)

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
        LocalMemoryStore.getCount(context)
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
                        Color(selectedTheme.surfaceColor),
                        themeBackgroundColor,
                        Color(0xFF02050D)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(
                "← ادراک پر واپس جائیں",
                color = themeAccentColor
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "نظام کی ترتیبات",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "ادراک کوانٹم مرکز • آف لائن ترتیب",
            color = Color(0xFF8EA6C9),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsStatusCard(
            title = "مصنوعی ذہن",
            status = "آف لائن ذہن فعال ہے",
            detail = "ادراک اپنا مقامی ذہن استعمال کرتا ہے۔ بیرونی مصنوعی ذہن اور انٹرنیٹ خدمات بند ہیں۔",
            accentColor = themeAccentColor,
        )

        SettingsStatusCard(
            title = "مقامی یادداشت",
            status = "$memoryCount اندراجات محفوظ ہیں",
            detail = "آپ کے کہنے پر باتیں اور پسند ناپسند محفوظ مقامی یادداشت میں رکھی جاتی ہیں۔ نئی باتیں آپ کی تصدیق تک زیرِ جانچ رہتی ہیں۔",
            accentColor = themeAccentColor,
        )

        SettingsStatusCard(
            title = "آواز کا نظام",
            status = "آف لائن اردو آواز پہچان اور فون کی آواز",
            detail = "صرف اردو سمجھنے کے لیے مقامی وائس ماڈل استعمال ہوتا ہے۔ جواب کے لیے فون میں آف لائن اردو آواز موجود ہونا ضروری ہے۔",
            accentColor = themeAccentColor,
        )

        Text(
            text = "زبانِ گفتگو",
            color = themeAccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "ادراک صرف اردو میں آپ کی بات سنے گا۔",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                onVoiceLanguageSelected(UrduVoiceLanguage.URDU)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeAccentColor)
        ) {
            Text("صرف اردو")
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "آواز کی رفتار",
            color = themeAccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "ادراک کے بولنے کی رفتار بدلیں۔ یہ ترتیب اسی فون میں محفوظ ہوگی۔",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )

        Text(
            text = String.format(java.util.Locale.US, "%.2f گنا", selectedSpeechRate),
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

        Text(
            text = "ظاہری انداز",
            color = themeAccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "ادراک کا ظاہری انداز منتخب کریں۔",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        IdraakTheme.entries.forEach { theme ->
            Button(
                onClick = { onThemeSelected(theme) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTheme == theme) {
                        Color(theme.accentColor)
                    } else {
                        Color(theme.surfaceColor)
                    }
                )
            ) {
                Text(
                    text = if (selectedTheme == theme) "✓ ${theme.title}" else theme.title,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))

        SettingsStatusCard(
            title = "فون کنٹرول",
            status = "ایپ • اسکرین • ٹارچ • کیمرہ • آواز • روشنی",
            detail = "فون کے کنٹرول مقامی طور پر چلتے ہیں۔ بعض کاموں کے لیے خصوصی اجازت درکار ہو سکتی ہے۔",
            accentColor = themeAccentColor,
        )

        SettingsStatusCard(
            title = "اسکرین کنٹرول کی اجازت",
            status = if (screenControlEnabled) {
                "اسکرین کنٹرول فعال ہے"
            } else {
                "اسکرین کنٹرول کی اجازت درکار ہے"
            },
            detail = "یہ اجازت ادراک کو اسکرین پر موجود الفاظ دیکھنے اور آپ کے کہنے پر کلک، سوائپ اور اسکرول کرنے دیتی ہے۔ اسکرین کی معلومات محفوظ یا ارسال نہیں کی جاتیں۔",
            accentColor = themeAccentColor,
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
                        "اسکرین کنٹرول کی ترتیبات نہیں کھل سکیں۔ فون کی ترتیبات سے رسائی کا اختیار کھولیں۔"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (screenControlEnabled) {
                    Color(0xFF263B59)
                } else {
                    themeAccentColor
                }
            )
        ) {
            Text(
                if (screenControlEnabled) {
                    "اسکرین کنٹرول کی اجازت سنبھالیں"
                } else {
                    "ادراک کا اسکرین کنٹرول فعال کریں"
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
            text = "یادداشت کا انتظام",
            color = themeAccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "ادراک کی محفوظ مقامی یادداشت دیکھیں، تلاش کریں اور سنبھالیں۔",
            color = Color(0xFF9CB0CC),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onOpenMemory,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = themeAccentColor
            )
        ) {
            Text(
                text = "یادداشت کھولیں",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "حالیہ یادداشت",
            color = themeAccentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (recentExperiences.isEmpty()) {
            Text(
                text = "ابھی کوئی تجربہ محفوظ نہیں ہوا۔",
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
            text = "ظاہری انداز اور آواز کی ترتیبات اسی فون میں محفوظ ہیں۔",
            color = Color(0xFF7B91B5),
            fontSize = 10.sp
        )
    }
}

@Composable
fun SettingsStatusCard(
    title: String,
    status: String,
    detail: String,
    accentColor: Color = Color(0xFF65C8FF)
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
                color = accentColor,
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
