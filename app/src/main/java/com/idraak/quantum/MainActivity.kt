package com.idraak.quantum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idraak.quantum.engine.AIEngine

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
    val backgroundColor = Color(0xFF050B18)
    val blue = Color(0xFF087DFF)

    var inputText by remember { mutableStateOf("") }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Hello Boss! Main Idraak hoon. Aap mujhse baat kar sakte hain.",
                false
            )
        )
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

    fun sendMessage() {
        val message = inputText.trim()

        if (message.isNotEmpty()) {
            messages.add(ChatMessage(message, true))
            messages.add(
                ChatMessage(
                    AIEngine.getResponse(message),
                    false
                )
            )
            inputText = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
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
            Spacer(modifier = Modifier.height(12.dp))

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
                text = "SYSTEM ONLINE",
                color = Color(0xFF43E8B2),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.isUser) {
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
                        Text("Message Idraak...", color = Color.LightGray)
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

            Text(
                text = "LOCAL DEMO ENGINE",
                color = Color(0xFF7B91B5),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
