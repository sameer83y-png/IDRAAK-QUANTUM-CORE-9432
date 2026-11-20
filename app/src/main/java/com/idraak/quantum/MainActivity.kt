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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.idraak.quantum.engine.AIEngine
import com.idraak.quantum.engine.GeminiAIEngine
import com.idraak.quantum.security.GeminiKeyStore
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    val backgroundColor = Color(0xFF050B18)
    val blue = Color(0xFF087DFF)

    var inputText by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var voiceStatus by remember {
        mutableStateOf("VOICE SYSTEM READY")
    }
    var isListening by remember { mutableStateOf(false) }
    var isContinuousVoiceChat by remember {
        mutableStateOf(false)
    }
    var recognizedSpeech by remember {
        mutableStateOf<String?>(null)
    }
    var isGenerating by remember { mutableStateOf(false) }

    var hasGeminiKey by remember {
        mutableStateOf(GeminiKeyStore.get(context) != null)
    }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hello Boss! Main Idraak hoon. Aap mujhse baat kar sakte hain.",
                false
            )
        )
    }

    // Safe reference for callbacks from VoiceController.
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
                        status.contains(
                            "Listening",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "Recognizing",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "Processing",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "Ready.",
                            ignoreCase = true
                        )
                    ) {
                        isListening = true
                    } else if (
                        status.contains(
                            "recognized",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "finished",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "Speaking",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "error",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "failed",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "stopped",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "timeout",
                            ignoreCase = true
                        ) ||
                        status.contains(
                            "No speech",
                            ignoreCase = true
                        )
                    ) {
                        isListening = false
                    }
                }
            },

            onSpeechFinished = {

                mainHandler.post {

                    if (
                        isContinuousVoiceChat &&
                        !isGenerating
                    ) {

                        val permissionGranted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                        if (permissionGranted) {

                            val started =
                                controllerReference[0]
                                    ?.startListening() == true

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

        if (isGenerating) {
            voiceStatus = "Please wait for AI response"
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

            if (started) {
                voiceStatus =
                    "VOICE CHAT ACTIVE • LISTENING"
            } else {
                voiceStatus =
                    "Could not start voice recognition"
            }

        } else {
            voiceStatus = "Requesting microphone permission"

            // Permission launcher is handled below.
            microphonePermissionRequest = true
        }
    }

    fun sendMessage(messageText: String = inputText) {

        val message = messageText.trim()

        if (message.isEmpty() || isGenerating) return

        messages.add(ChatMessage(message, true))
        inputText = ""

        if (!hasGeminiKey) {

            val demoResponse = AIEngine.getResponse(message)

            messages.add(ChatMessage(demoResponse, false))

            if (isContinuousVoiceChat || isListening) {
                voiceController.speak(demoResponse)
            } else {
                voiceController.speak(demoResponse)
            }

            voiceStatus = "DEMO MODE ACTIVE"
            return
        }

        isGenerating = true
        voiceStatus = "GEMINI AI PROCESSING..."

        scope.launch {

            try {

                val response =
                    GeminiAIEngine.generateResponse(
                        context = context.applicationContext,
                        userMessage = message
                    )

                messages.add(ChatMessage(response, false))

                voiceController.speak(response)

                voiceStatus = "GEMINI AI CONNECTED"

            } catch (error: Exception) {

                val errorMessage =
                    error.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown connection error"

                val safeMessage =
                    "Boss, Gemini se connection nahi ho paya.\n\n" +
                    "Error: $errorMessage\n\n" +
                    "Settings aur API Key check kijiye."

                messages.add(
                    ChatMessage(safeMessage, false)
                )

                voiceController.speak(safeMessage)

                voiceStatus = "GEMINI CONNECTION ERROR"

            } finally {
                isGenerating = false
            }
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

    var microphonePermissionRequest by remember {
        mutableStateOf(false)
    }

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                if (isContinuousVoiceChat) {

                    val started =
                        voiceController.startListening()

                    isListening = started

                    voiceStatus = if (started) {
                        "VOICE CHAT ACTIVE • LISTENING"
                    } else {
                        "Could not start listening"
                    }
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

    val transition =
        rememberInfiniteTransition(label = "core")

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

        if (showSettings) {

            SettingsScreen(
                onBack = {
                    hasGeminiKey =
                        GeminiKeyStore.get(context) != null

                    showSettings = false
                }
            )

        } else {

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

                // Original Idraak Core Orb preserved.
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
                    text = "SYSTEM ONLINE",
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

                if (isGenerating) {

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = "Idraak is thinking...",
                        color = Color(0xFF65C8FF),
                        fontSize = 11.sp
                    )
                }

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
                        onValueChange = {
                            inputText = it
                        },
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
                            onSend = {
                                sendMessage()
                            }
                        )
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    Button(
                        onClick = {
                            sendMessage()
                        },
                        enabled = !isGenerating,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = blue
                        )
                    ) {

                        Text(
                            if (isGenerating) "WAIT" else "SEND"
                        )
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
                        enabled = !isGenerating ||
                            isContinuousVoiceChat,
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
                        onClick = {
                            stopVoiceChat()
                        },
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
                    text = if (hasGeminiKey) {
                        "GEMINI AI • DEMO FALLBACK READY"
                    } else {
                        "LOCAL DEMO AI • VOICE CHAT FOUNDATION"
                    },
                    color = Color(0xFF7B91B5),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {

    val context = LocalContext.current

    var apiKey by remember { mutableStateOf("") }

    var hasSavedKey by remember {
        mutableStateOf(GeminiKeyStore.get(context) != null)
    }

    var statusMessage by remember {
        mutableStateOf("")
    }

    val backgroundColor = Color(0xFF050B18)

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
            text = "Configure your IDRAAK QUANTUM CORE",
            color = Color(0xFF8EA6C9),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsStatusCard(
            title = "GEMINI AI CONNECTION",
            status = if (hasSavedKey) {
                "API KEY SAVED"
            } else {
                "DEMO MODE ACTIVE"
            },
            detail = if (hasSavedKey) {
                "Gemini API key is saved securely on this device."
            } else {
                "No API key saved. Idraak will continue using Demo Mode."
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "GEMINI API KEY",
            color = Color(0xFF65C8FF),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Paste your Gemini API Key")
            },
            placeholder = {
                Text("Enter API key")
            },
            singleLine = true,
            visualTransformation =
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {

                if (apiKey.isBlank()) {

                    statusMessage =
                        "Please enter your API key first."

                } else {

                    val saved =
                        GeminiKeyStore.save(context, apiKey)

                    if (saved) {

                        hasSavedKey = true
                        apiKey = ""

                        statusMessage =
                            "Gemini API key saved securely."

                    } else {

                        statusMessage =
                            "Could not save the API key. Please try again."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF087DFF)
            )
        ) {

            Text("SAVE GEMINI API KEY")
        }

        if (hasSavedKey) {

            TextButton(
                onClick = {

                    val deleted =
                        GeminiKeyStore.delete(context)

                    if (deleted) {

                        hasSavedKey = false

                        statusMessage =
                            "Saved API key removed."

                    } else {

                        statusMessage =
                            "Could not remove the saved key."
                    }
                }
            ) {

                Text(
                    "REMOVE SAVED API KEY",
                    color = Color(0xFFFF8297)
                )
            }
        }

        if (statusMessage.isNotBlank()) {

            Text(
                text = statusMessage,
                color = Color(0xFF43E8B2),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SettingsStatusCard(
            title = "VOICE SYSTEM",
            status = "CONTINUOUS VOICE CHAT FOUNDATION",
            detail = "Speech recognition and Android text-to-speech are connected to the chat screen."
        )

        SettingsStatusCard(
            title = "LOCAL MEMORY",
            status = "DEMO SESSION ONLY",
            detail = "Chat messages are currently stored only during this session."
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DUAL VOICE CHARACTERS",
            color = Color(0xFF00B7FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        VoiceCharacterCard(
            name = "SAMEER",
            role = "MALE CHARACTER",
            detail = "Your side of the partnership. Voice profile planned.",
            accent = Color(0xFF58B9FF)
        )

        VoiceCharacterCard(
            name = "IDRAAK",
            role = "FEMALE AI CHARACTER",
            detail = "AI companion voice. Female voice profile planned.",
            accent = Color(0xFFE3A0FF)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Custom voice profiles and voice cloning are not configured yet.",
            color = Color(0xFF7B91B5),
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "IDRAAK QUANTUM CORE • SETTINGS",
            color = Color(0xFF526987),
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

@Composable
fun VoiceCharacterCard(
    name: String,
    role: String,
    detail: String,
    accent: Color
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        color = Color(0xFF18243A),
        shape = RoundedCornerShape(16.dp)
    ) {

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(accent, CircleShape)
            )

            Spacer(modifier = Modifier.size(12.dp))

            Column {

                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = role,
                    color = accent,
                    fontSize = 11.sp,
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
}
