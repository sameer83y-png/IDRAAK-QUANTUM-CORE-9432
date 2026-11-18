package com.idraak.quantum.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AIEngine {

    fun getResponse(userMessage: String): String {
        val message = userMessage.trim().lowercase(Locale.ROOT)

        if (message.isBlank()) {
            return "Boss, main sun raha hoon. Aap kuch boliye!"
        }

        return when {
            message.contains("hello") ||
            message.contains("hi") ||
            message.contains("hey") ||
            message.contains("नमस्ते") -> {
                "Hello Boss! Main Idraak hoon. Aapki kya help kar sakta hoon?"
            }

            message.contains("who are you") ||
            message.contains("tum kaun") ||
            message.contains("तुम कौन") -> {
                "Main IDRAAK QUANTUM CORE hoon, aapka personal AI companion."
            }

            message.contains("your name") ||
            message.contains("naam kya") ||
            message.contains("नाम क्या") -> {
                "Mera naam Idraak hai, Boss!"
            }

            message.contains("time") ||
            message.contains("समय") -> {
                val time = SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(Date())

                "Boss, abhi time hai $time."
            }

            message.contains("date") ||
            message.contains("tarikh") ||
            message.contains("तारीख") -> {
                val date = SimpleDateFormat(
                    "dd MMMM yyyy",
                    Locale.getDefault()
                ).format(Date())

                "Aaj ki date hai $date."
            }

            message.contains("how are you") ||
            message.contains("kaise ho") ||
            message.contains("कैसे हो") -> {
                "Main bilkul ready hoon Boss! Aap bataiye kaise hain?"
            }

            message.contains("thank") ||
            message.contains("शुक्रिया") ||
            message.contains("धन्यवाद") -> {
                "You're welcome Boss! Main hamesha help ke liye ready hoon."
            }

            message.contains("help") ||
            message.contains("मदद") -> {
                "Boss, main abhi Demo Mode mein hoon. Aap mujhse greeting, time aur date pooch sakte hain."
            }

            else -> {
                "Boss, maine aapka message receive kar liya hai. Abhi mera Demo Engine limited hai. Full AI intelligence agle development stages mein add karenge."
            }
        }
    }
}
