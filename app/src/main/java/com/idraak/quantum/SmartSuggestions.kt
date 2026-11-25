package com.idraak.quantum

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.idraak.quantum.engine.OfflineBrain
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
    val command: String,
    val category: String = "عام مدد"
)

/**
 * Builds suggestions from local device state, recent command outcomes and the
 * category of the latest command. It does not inspect private content or use
 * the network.
 */
object SmartSuggestionEngine {

    internal fun recoverySuggestion(recentExperiences: List<String>): IdraakSuggestion? {
        val latestStatus = recentExperiences.lastOrNull()
            ?.substringAfterLast(':')
            ?.trim()
            ?.uppercase()
            ?: return null

        return when (latestStatus) {
            "FAILED" -> IdraakSuggestion(
                title = "پچھلا کام مکمل نہیں ہوا",
                detail = "پچھلے حکم میں مسئلہ آیا تھا۔ مدد دیکھ کر حکم دوبارہ واضح کریں۔",
                command = "مدد کرو",
                category = "توجہ طلب"
            )
            "NEEDS_CLARIFICATION" -> IdraakSuggestion(
                title = "حکم مزید واضح کریں",
                detail = "پچھلی بات پوری طرح واضح نہیں ہوئی۔ ایک وقت میں ایک کام بتائیں۔",
                command = "مدد کرو",
                category = "توجہ طلب"
            )
            else -> null
        }
    }

    internal fun classifyRecentCommandCategory(command: String): String? {
        val value = command.trim().lowercase()
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (value.isBlank()) return null

        fun has(vararg phrases: String) = phrases.any { value.contains(it) }

        return when {
            has("کیمرہ", "فوٹو", "تصویر", "camera", "photo", "picture",
                "कैमरा", "फोटो", "तस्वीर", "camera kholo", "tasveer", "photo lo") -> "CAMERA"
            has("یوٹیوب", "youtube", "यूट्यूब") -> "YOUTUBE"
            has("کال", "فون کرو", "فون ملاؤ", "call", "phone karo",
                "कॉल", "फोन करो", "फोन मिलाओ") -> "CALL"
            has("پیغام", "میسج", "message", "sms", "text message",
                "संदेश", "मैसेज", "संदेश भेजो") -> "MESSAGE"
            has("آواز", "روشنی", "والیوم", "ٹارچ", "volume", "brightness",
                "flashlight", "torch", "sound", "awaz", "roshni",
                "आवाज", "रोशनी", "वॉल्यूम", "टॉर्च") -> "HARDWARE"
            has("اسکرول", "سوائپ", "کلک", "اسکرین", "دباؤ", "scroll",
                "swipe", "click", "tap", "screen", "स्क्रॉल", "स्वाइप",
                "क्लिक", "स्क्रीन", "दबाओ") -> "SCREEN"
            has("یاد رکھنا", "یاد رکھو", "یادداشت", "memory", "remember",
                "याद रखो", "याद रखना", "याददाश्त") -> "MEMORY"
            has("کھولو", "چالو کرو", "open", "launch", "start app",
                "खोलो", "खोल दो", "चालू करो", "app kholo") -> "APP"
            else -> null
        }
    }

    fun build(
        context: Context,
        voiceActive: Boolean,
        lastUserMessage: String
    ): List<IdraakSuggestion> {
        val suggestions = mutableListOf<IdraakSuggestion>()
        val battery = readBattery(context)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val recentCategory = classifyRecentCommandCategory(lastUserMessage)
        val recentExperiences = try {
            OfflineBrain.getRecentExperiences(context.applicationContext, limit = 8)
        } catch (_: Exception) {
            emptyList()
        }
        recoverySuggestion(recentExperiences)?.let { suggestions += it }

        if (battery != null && battery.first <= 25 && !battery.second) {
            suggestions += IdraakSuggestion(
                title = "بیٹری کم ہے",
                detail = "بیٹری کی حالت دیکھ لیں۔",
                command = "بیٹری کتنی ہے؟",
                category = "فون کی حالت"
            )
        } else if (battery != null && battery.second) {
            suggestions += IdraakSuggestion(
                title = "فون چارج ہو رہا ہے",
                detail = "چاہیں تو چارج ہونے کی حالت معلوم کریں۔",
                command = "چارج ہونے کی حالت بتاؤ",
                category = "فون کی حالت"
            )
        }

        if (hour in 5..11) {
            suggestions += IdraakSuggestion(
                title = "صبح کی ابتدا",
                detail = "آج کی تاریخ اور وقت معلوم کریں۔",
                command = "آج کی تاریخ بتاؤ",
                category = "وقت اور تاریخ"
            )
        } else if (hour in 12..17) {
            suggestions += IdraakSuggestion(
                title = "دن کا وقت",
                detail = "موجودہ وقت معلوم کریں۔",
                command = "ابھی وقت بتاؤ",
                category = "وقت اور تاریخ"
            )
        } else {
            suggestions += IdraakSuggestion(
                title = "آج کا دن",
                detail = "آج کی تاریخ معلوم کر سکتے ہیں۔",
                command = "آج کی تاریخ بتاؤ",
                category = "وقت اور تاریخ"
            )
        }

        // Use the latest command's category across Urdu, Hindi, English and Roman Urdu.
        when (recentCategory) {
            "CAMERA" -> suggestions += IdraakSuggestion(
                title = "کیمرے کی مدد",
                detail = "کیمرہ کھولنے یا تصویر لینے کے لیے واضح حکم بولیں۔",
                command = "مدد کرو",
                category = "حالیہ حکم"
            )
            "YOUTUBE" -> suggestions += IdraakSuggestion(
                title = "ایپ تبدیل کریں",
                detail = "کسی دوسری نصب شدہ ایپ کو کھولنے کے لیے کہیں۔",
                command = "مدد کرو",
                category = "حالیہ حکم"
            )
            "CALL" -> suggestions += IdraakSuggestion(
                title = "کال کی مدد",
                detail = "کال کے لیے رابطہ منتخب کرنا اور فون ایپ میں تصدیق کرنا ضروری ہے۔",
                command = "مدد کرو",
                category = "حالیہ حکم"
            )
            "MESSAGE" -> suggestions += IdraakSuggestion(
                title = "پیغام کی مدد",
                detail = "بھیجنے سے پہلے پیغام اور وصول کنندہ کی تصدیق کریں۔",
                command = "مدد کرو",
                category = "حالیہ حکم"
            )
            "HARDWARE" -> suggestions += IdraakSuggestion(
                title = "فون کنٹرول کی مدد",
                detail = "آواز، روشنی اور ٹارچ کنٹرول کرنے کے لیے واضح حکم بولیں۔",
                command = "مدد کرو",
                category = "فون کنٹرول"
            )
            "SCREEN" -> suggestions += IdraakSuggestion(
                title = "اسکرین کنٹرول کی مدد",
                detail = "واپس جانے، ہوم کھولنے، سوائپ، اسکرول اور کلک کے لیے واضح حکم دیں۔",
                command = "مدد کرو",
                category = "اسکرین کنٹرول"
            )
            "MEMORY" -> suggestions += IdraakSuggestion(
                title = "مقامی یادداشت کی مدد",
                detail = "نئی معلومات تصدیق تک زیرِ جانچ رہتی ہیں؛ اسے درست ثابت کرنے کے لیے یادداشت میں تصدیق کریں۔",
                command = "مدد کرو",
                category = "یادداشت"
            )
            "APP" -> suggestions += IdraakSuggestion(
                title = "ایپ کنٹرول",
                detail = "فون میں نصب کسی ایپ کا نام لے کر اسے کھولنے کو کہیں۔",
                command = "مدد کرو",
                category = "ایپ کنٹرول"
            )
            null -> if (!voiceActive) {
                suggestions += IdraakSuggestion(
                    title = "آواز کے احکام",
                    detail = "مائیکروفون کے بٹن سے ادراک کو بول کر حکم دیں۔",
                    command = "آپ کیا کیا کر سکتے ہیں؟",
                    category = "آواز کی مدد"
                )
            }
        }

        if (suggestions.isEmpty()) {
            suggestions += IdraakSuggestion(
                title = "ادراک کی مدد",
                detail = "دستیاب آف لائن احکام معلوم کریں۔",
                command = "مدد کرو",
                category = "عام مدد"
            )
        }

        return suggestions.distinctBy { it.title }.take(4)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "سمجھ دار تجاویز • موجودہ صورتِ حال کے مطابق",
                color = Color(0xFF65C8FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            suggestions.groupBy { it.category }.forEach { (category, items) ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = category.uppercase(),
                        color = Color(0xFF65C8FF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    items.forEach { suggestion ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 4.dp)
                            ) {
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
                                Text("منتخب کریں", color = Color(0xFF65C8FF))
                            }
                        }
                    }
                }
            }
        }
    }
}
