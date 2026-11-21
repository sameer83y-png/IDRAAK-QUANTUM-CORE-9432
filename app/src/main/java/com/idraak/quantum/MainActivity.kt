package com.idraak.quantum

import android.Manifest
import android.content.pm.PackageManager
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
    var recognizedSpeech by remember { mutableStateOf<String?>(null) }

    var microphonePermissionRequest by remember {
        mutableStateOf(false)
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

    fun startVoiceChat() {
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

    fun sendMessage(messageText: String = inputText) {
        val message = messageText.trim()
        if (message.isEmpty()) return

        messages.add(ChatMessage(message, true))
        inputText = ""

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
                val started = voiceController.startListening()
                isListening = started

                voiceStatus = if (started) {
                    "VOICE CHAT ACTIVE • LISTENING"
                } else {
                    "Could not start listening"
                }
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
                        isContinuousVoiceChat = false
                        isListening = false
                        voiceController.cancelListening()
                        voiceController.setRecognitionLanguage(language)
                        selectedVoiceLanguage = language

                        voiceStatus = when (language) {
                            VoskModelStore.VoskLanguage.HINDI ->
                                "HINDI OFFLINE MODEL SELECTED"

                            VoskModelStore.VoskLanguage.INDIAN_ENGLISH ->
                                "INDIAN ENGLISH OFFLINE MODEL SELECTED"
                        }
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
                            isListening || isContinuousVoiceChat
                        ) {
                            Color(0xFF43E8B2)
                        } else {
                            Color(0xFF8EA6C9)
                        },
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement =
                            Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { message ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    if (message.isUser) {
                                        Arrangement.End
                                    } else {
                                        Arrangement.Start
                                    }
                            ) {
                                Surface(
                                    color = if (message.isUser) {
                                        Color(0xFF123B70)
                                    } else {
                                        Color(0xFF17243A)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth(0.88f)
                                ) {
                                    Text(
                                        text = message.text,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Message Idraak...",
                                    color = Color.LightGray
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { sendMessage() }
                            )
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        Button(
                            onClick = { sendMessage() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = blue
                            )
                        ) {
                            Text("SEND")
                        }
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
    onVoiceLanguageSelected: (VoskModelStore.VoskLanguage) -> Unit
) {
    val context = LocalContext.current

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
            detail = "Gemini connection has been removed from the main chat flow. OfflineBrain currently recognizes basic commands using local rules."
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
                onVoiceLanguageSelected(VoskModelStore.VoskLanguage.HINDI)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (
                    selectedVoiceLanguage == VoskModelStore.VoskLanguage.HINDI
                ) Color(0xFF087DFF) else Color(0xFF263B59)
            )
        ) {
            Text("HINDI OFFLINE")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                onVoiceLanguageSelected(VoskModelStore.VoskLanguage.INDIAN_ENGLISH)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (
                    selectedVoiceLanguage == VoskModelStore.VoskLanguage.INDIAN_ENGLISH
                ) Color(0xFF087DFF) else Color(0xFF263B59)
            )
        ) {
            Text("INDIAN ENGLISH OFFLINE")
        }

        Spacer(modifier = Modifier.height(18.dp))

        SettingsStatusCard(
            title = "PHONE CONTROLLERS",
            status = "NOT CONNECTED YET",
            detail = "Camera, screen interaction, app control and hardware control will be added as separate modules."
        )

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
