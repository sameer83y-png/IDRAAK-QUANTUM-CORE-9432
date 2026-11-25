package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

data class IdraakSuggestion(
    val title: String,
    val detail: String,
    val command: String
)

/**
 * Builds a small, context-aware suggestion list using only local device state.
 * It does not request permissions, read personal content, or use the network.
 */
object SmartSuggestionEngine {

    fun build(
        context: Context,
        voiceActive: Boolean,
        lastUserMessage: String
    ): List<IdraakSuggestion> {
        val suggestions = mutableListOf<IdraakSuggestion>()
        val battery = readBattery(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val last = lastUserMessage.trim().lowercase()

        if (battery != null && battery.first <= 25 && !battery.second) {
            suggestions += IdraakSuggestion(
                title = "बैटरी कम है",
                detail = "बैटरी की स्थिति जाँच लें।",
                command = "बैटरी कितनी है"
            )
        } else if (battery != null && battery.second) {
            suggestions += IdraakSuggestion(
                title = "फोन चार्ज हो रहा है",
                detail = "चाहें तो चार्जिंग की स्थिति जाँचें।",
                command = "चार्जिंग स्टेटस बताओ"
            )
        }

        if (hour in 5..11) {
            suggestions += IdraakSuggestion(
                title = "सुबह की शुरुआत",
                detail = "आज की तारीख और समय जानें।",
                command = "आज की तारीख बताओ"
            )
        } else if (hour in 12..17) {
            suggestions += IdraakSuggestion(
                title = "दिन का समय",
                detail = "अभी का समय जानें।",
                command = "अभी समय बताओ"
            )
        } else {
            suggestions += IdraakSuggestion(
                title = "आज का दिन",
                detail = "आज की तारीख देख सकते हैं।",
                command = "आज की तारीख बताओ"
            )
        }

        // Use only the category of the most recent user command. Do not
        // inspect contacts, message contents, app usage history, or screen text.
        when {
            last.contains("camera") || last.contains("कैमरा") ||
                last.contains("فوٹو") || last.contains("تصویر") -> {
                suggestions += IdraakSuggestion(
                    title = "कैमरा सहायता",
                    detail = "चाहें तो Idraak से कैमरा खोलने को कहें।",
                    command = "कैमरा खोलो"
                )
            }

            last.contains("youtube") || last.contains("यूट्यूब") -> {
                suggestions += IdraakSuggestion(
                    title = "ऐप बदलें",
                    detail = "किसी दूसरे इंस्टॉल किए ऐप को खोलने के लिए कहें।",
                    command = "मदद करो"
                )
            }

            last.contains("call") || last.contains("कॉल") ||
                last.contains("फोन") || last.contains("کال") -> {
                suggestions += IdraakSuggestion(
                    title = "कॉल सहायता",
                    detail = "कॉल के लिए संपर्क चुनना और डायलर में पुष्टि करना जरूरी है।",
                    command = "मदद करो"
                )
            }

            last.contains("message") || last.contains("मैसेज") ||
                last.contains("संदेश") || last.contains("پیغام") ||
                last.contains("میسج") -> {
                suggestions += IdraakSuggestion(
                    title = "मैसेज सहायता",
                    detail = "भेजने से पहले संदेश और प्राप्तकर्ता की पुष्टि करें।",
                    command = "मदद करो"
                )
            }

            !voiceActive -> {
                suggestions += IdraakSuggestion(
                    title = "वॉइस कमांड",
                    detail = "माइक बटन से Idraak को बोलकर कमांड दें।",
                    command = "तुम क्या-क्या कर सकते हो"
                )
            }
        }

        if (suggestions.isEmpty()) {
            suggestions += IdraakSuggestion(
                title = "Idraak सहायता",
                detail = "उपलब्ध ऑफलाइन कमांड जानें।",
                command = "मदद करो"
            )
        }

        return suggestions.distinctBy { it.title }.take(3)
    }

    private fun readBattery(context: Context): Pair<Int, Boolean>? {
        return try {
            val batteryIntent = context.applicationContext.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return null

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null

            val percent = (level * 100 / scale).coerceIn(0, 100)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            percent to charging
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun SmartSuggestionsPanel(
    suggestions: List<IdraakSuggestion>,
    onSuggestionSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF101F36),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "SMART SUGGESTIONS • परिस्थिति के अनुसार",
                color = Color(0xFF65C8FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            suggestions.forEach { suggestion ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = suggestion.detail,
                            color = Color(0xFF9DB2D1),
                            fontSize = 11.sp
                        )
                    }
                    TextButton(
                        onClick = { onSuggestionSelected(suggestion.command) }
                    ) {
                        Text("चुनें", color = Color(0xFF65C8FF))
                    }
                }
            }
        }
    }
}
