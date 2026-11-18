package com.idraak.quantum

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceController(
    context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onStatusChanged: (String) -> Unit
) : RecognitionListener {

    private val appContext = context.applicationContext

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        initializeSpeechRecognition()
        initializeTextToSpeech()
    }

    private fun initializeSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onStatusChanged("Speech recognition is not available")
            return
        }

        speechRecognizer = SpeechRecognizer
            .createSpeechRecognizer(appContext)
            .also {
                it.setRecognitionListener(this)
            }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val defaultLocale = Locale.getDefault()
                val languageResult =
                    textToSpeech?.setLanguage(defaultLocale)

                if (
                    languageResult == TextToSpeech.LANG_MISSING_DATA ||
                    languageResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    textToSpeech?.setLanguage(Locale.US)
                }

                isTtsReady = true
                onStatusChanged("Voice output ready")
            } else {
                isTtsReady = false
                onStatusChanged("Voice output initialization failed")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening(): Boolean {
        val recognizer = speechRecognizer

        if (recognizer == null) {
            onStatusChanged("Speech recognition unavailable")
            return false
        }

        return try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault().toLanguageTag()
                )
                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    false
                )
            }

            onStatusChanged("Listening...")
            recognizer.startListening(intent)
            true
        } catch (exception: Exception) {
            onStatusChanged(
                "Could not start listening: ${exception.localizedMessage}"
            )
            false
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        onStatusChanged("Listening stopped")
    }

    fun cancelListening() {
        speechRecognizer?.cancel()
        onStatusChanged("Listening cancelled")
    }

    fun speak(text: String) {
        if (text.isBlank()) return

        val tts = textToSpeech

        if (tts == null || !isTtsReady) {
            onStatusChanged("Voice output is not ready")
            return
        }

        val result = tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            "idraak_voice_output"
        )

        if (result == TextToSpeech.ERROR) {
            onStatusChanged("Could not speak the response")
        } else {
            onStatusChanged("Speaking...")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        onStatusChanged("Voice output stopped")
    }

    fun shutdown() {
        speechRecognizer?.destroy()
        speechRecognizer = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        isTtsReady = false
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onStatusChanged("Ready. Speak now.")
    }

    override fun onBeginningOfSpeech() {
        onStatusChanged("Recognizing your voice...")
    }

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() {
        onStatusChanged("Processing your speech...")
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Microphone audio error"

            SpeechRecognizer.ERROR_CLIENT ->
                "Speech recognition client error"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required"

            SpeechRecognizer.ERROR_NETWORK ->
                "Network error during recognition"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Speech recognition network timeout"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "No speech detected. Please try again."

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech recognizer is busy"

            SpeechRecognizer.ERROR_SERVER ->
                "Speech recognition service error"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "No speech input detected"

            else ->
                "Speech recognition failed. Error: $error"
        }

        onStatusChanged(message)
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )

        val recognizedText = matches?.firstOrNull()?.trim()

        if (!recognizedText.isNullOrBlank()) {
            onSpeechResult(recognizedText)
            onStatusChanged("Speech recognized")
        } else {
            onStatusChanged("No speech result received")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
