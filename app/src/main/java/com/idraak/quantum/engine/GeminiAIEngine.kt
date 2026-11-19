package com.idraak.quantum.engine

import android.content.Context
import com.idraak.quantum.security.GeminiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GeminiAIEngine {

    private const val MODEL = "gemini-3.8-flash"

    private const val API_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    suspend fun generateResponse(
        context: Context,
        userMessage: String
    ): String = withContext(Dispatchers.IO) {

        require(userMessage.isNotBlank()) {
            "Please enter a message."
        }

        val apiKey = GeminiKeyStore.get(context)
            ?: throw IllegalStateException(
                "Gemini API Key is not configured. Please add your key in Settings."
            )

        var connection: HttpURLConnection? = null

        try {
            connection = (URL(API_URL).openConnection()
                as HttpURLConnection).apply {

                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 60000
                doOutput = true

                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                setRequestProperty(
                    "x-goog-api-key",
                    apiKey
                )
            }

            val systemInstruction = JSONObject()
                .put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            """
                            You are Idraak, a helpful personal AI companion.
                            Address the user respectfully as Boss.
                            Understand Hindi, English, Hinglish and Urdu.
                            Reply naturally in the language used by the user.
                            Be friendly, clear, accurate and concise.
                            Never claim to have performed phone actions
                            unless they were actually performed.
                            """.trimIndent()
                        )
                    )
                )

            val userContent = JSONObject()
                .put("role", "user")
                .put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put("text", userMessage)
                    )
                )

            val requestBody = JSONObject()
                .put("systemInstruction", systemInstruction)
                .put(
                    "contents",
                    JSONArray().put(userContent)
                )
                .put(
                    "generationConfig",
                    JSONObject().put("maxOutputTokens", 2048)
                )

            connection.outputStream.use { output ->
                output.write(
                    requestBody.toString().toByteArray(Charsets.UTF_8)
                )
            }

            val responseCode = connection.responseCode

            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use {
                    it.readText()
                }
            } else {
                connection.errorStream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: "Gemini API request failed."
            }

            val responseJson = JSONObject(responseText)

            if (responseCode !in 200..299) {
                val errorMessage = responseJson
                    .optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Gemini API error: HTTP $responseCode"

                throw IllegalStateException(errorMessage)
            }

            val candidates = responseJson.optJSONArray("candidates")
                ?: throw IllegalStateException(
                    "Gemini returned no response candidates."
                )

            if (candidates.length() == 0) {
                throw IllegalStateException(
                    "Gemini returned an empty response."
                )
            }

            val content = candidates
                .getJSONObject(0)
                .optJSONObject("content")
                ?: throw IllegalStateException(
                    "Gemini response content is missing."
                )

            val parts = content.optJSONArray("parts")
                ?: throw IllegalStateException(
                    "Gemini response text is missing."
                )

            val answer = buildString {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i)
                    val text = part?.optString("text")
                    if (!text.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n")
                        append(text)
                    }
                }
            }.trim()

            if (answer.isBlank()) {
                throw IllegalStateException(
                    "Gemini returned an empty text response."
                )
            }

            answer

        } finally {
            connection?.disconnect()
        }
    }
}
