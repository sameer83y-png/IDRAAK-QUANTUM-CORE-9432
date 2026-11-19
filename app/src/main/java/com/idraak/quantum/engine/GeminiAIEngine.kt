package com.idraak.quantum.engine

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

object GeminiAIEngine {

    private const val MODEL_NAME = "gemini-3.5-flash"

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(MODEL_NAME)
    }

    suspend fun getResponse(userMessage: String): String {
        val message = userMessage.trim()

        if (message.isBlank()) {
            return "Boss, kuch poochiye."
        }

        return try {
            val prompt = """
                You are Idraak, a friendly AI companion.
                Reply in the same language as the user.
                Support Hindi, Hinglish, Urdu and English.
                Be helpful, honest and conversational.
                Never claim an action was performed unless it was.

                User message:
                $message
            """.trimIndent()

            val response = model.generateContent(prompt)

            response.text
                ?.takeIf { it.isNotBlank() }
                ?: "Gemini returned an empty response."

        } catch (exception: Exception) {
            val reason = exception.localizedMessage
                ?: exception.javaClass.simpleName

            "Gemini connection error: $reason"
        }
    }
}
