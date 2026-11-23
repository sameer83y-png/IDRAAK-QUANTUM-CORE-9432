package com.idraak.quantum

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.idraak.quantum.engine.HardwareController
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
            val started = voiceController.startListening()
            isListening = started

            voiceStatus = if (started) {
                "VOICE CHAT ACTIVE • LISTENING"
            } else {
                "Could not start voice recognition"
            }
        } else {
            voiceStatus = "Requesting microphone permission"
            microphonePermissionRequest = true
        }
    }

    fun requestedTorchState(message: String): Boolean? {
        val normalized = message.trim().lowercase()

        val turnOff =
            Regex("""\boff\b""").containsMatchIn(normalized) ||
                listOf(
                    "turn off",
                    "switch off",
                    "torch off",
                    "flashlight off",
                    "band",
                    "بند",
                    "बंद",
                    "बुझा"
                ).any { normalized.contains(it) }

        val turnOn =
            Regex("""\bon\b""").containsMatchIn(normalized) ||
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
                ).any { normalized.contains(it) }

        return when {
            turnOn && !turnOff -> true
            turnOff && !turnOn -> false
            else -> null
        }
    }

    fun executeTorchCommand(
        originalMessage: String,
        enabled: Boolean
    ) {
        val result = HardwareController.setTorch(
            context = context.applicationContext,
            enabled = enabled
        )

        OfflineBrain.recordExperience(
            context = context.applicationContext,
            intent = "FLASHLIGHT",
            status = if (result.success) "SUCCESS" else "FAILED",
            steps = listOf(
                originalMessage,
                if (enabled) "TORCH_ON" else "TORCH_OFF",
                result.message
            )
        )

        messages.add(
            ChatMessage(result.message, false)
        )

        voiceController.speak(result.message)

        voiceStatus = if (result.success) {
            "FLASHLIGHT CONTROL SUCCESS"
        } else {
            "FLASHLIGHT CONTROL FAILED"
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

                    messages.add(
                        ChatMessage(response, false)
                    )

                    voiceController.speak(response)
                    voiceStatus = "CAMERA PERMISSION DENIED"
                }
            }
        }
