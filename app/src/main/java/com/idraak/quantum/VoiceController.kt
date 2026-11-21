package com.idraak.quantum

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VoiceController(
    context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onStatusChanged: (String) -> Unit,
    private val onSpeechFinished: () -> Unit = {}
) {

    private val appContext = context.applicationContext
    private val modelStore = VoskModelStore(appContext)

    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    @Volatile
    private var isShutdown = false

    @Volatile
    private var isLoadingModel = false

    @Volatile
    private var selectedLanguage =
        VoskModelStore.VoskLanguage.HINDI

    private var currentModel: Model? = null
    private var currentRecognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    private val recognitionListener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            if (!isShutdown) {
                onStatusChanged("Recognizing your voice...")
            }
        }

        override fun onResult(hypothesis: String?) {
            handleRecognitionResult(hypothesis, false)
        }

        override fun onFinalResult(hypothesis: String?) {
            if (isShutdown) return

            handleRecognitionResult(hypothesis, true)

            onStatusChanged("Speech recognized")
            onSpeechFinished()
        }

        override fun onError(exception: Exception?) {
            if (!isShutdown) {
                onStatusChanged(
                    "Offline voice error: " +
                        (exception?.localizedMessage ?: "Unknown error")
                )
            }
        }

        override fun onTimeout() {
            if (!isShutdown) {
                onStatusChanged("Listening timeout")
                onSpeechFinished()
            }
        }
    }

    init {
        initializeTextToSpeech()
    }

    /**
     * Select Hindi or Indian English recognition model.
     * Selection applies to the next listening session.
     */
    @Synchronized
    fun setRecognitionLanguage(
        language: VoskModelStore.VoskLanguage
    ) {
        if (isShutdown) return

        if (selectedLanguage == language) return

        selectedLanguage = language

        stopRecognitionResources()

        onStatusChanged(
            if (language == VoskModelStore.VoskLanguage.HINDI) {
                "Hindi offline voice selected"
            } else {
                "Indian English offline voice selected"
            }
        )
    }

    fun getRecognitionLanguage(): VoskModelStore.VoskLanguage {
        return selectedLanguage
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(appContext) { status ->

            if (isShutdown) return@TextToSpeech

            if (status == TextToSpeech.SUCCESS) {

                val result = textToSpeech?.setLanguage(
                    Locale.getDefault()
                )

                if (
                    result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
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

        textToSpeech?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {

                override fun onStart(utteranceId: String?) {
                    if (!isShutdown) {
                        onStatusChanged("Speaking...")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    if (!isShutdown) {
                        onStatusChanged("Speech finished")
                        onSpeechFinished()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (!isShutdown) {
                        onStatusChanged("Voice output error")
                    }
                }

                override fun onError(
                    utteranceId: String?,
                    errorCode: Int
                ) {
                    if (!isShutdown) {
                        onStatusChanged(
                            "Voice output error: $errorCode"
                        )
                    }
                }
            }
        )
    }

    /**
     * Starts Vosk offline recognition.
     * Model loading and preparation happen in the background.
     */
    @Synchronized
    fun startListening(): Boolean {

        if (isShutdown) return false

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            onStatusChanged("Microphone permission is required")
            return false
        }

        val existingService = speechService

        if (existingService != null) {
            val started = existingService.startListening(
                recognitionListener
            )

            if (started) {
                onStatusChanged("Listening offline...")
            }

            return started
        }

        if (isLoadingModel) {
            onStatusChanged("Offline voice model is loading...")
            return true
        }

        isLoadingModel = true
        onStatusChanged("Preparing offline voice model...")

        val languageToLoad = selectedLanguage

        try {
            executor.execute {

                try {
                    if (isShutdown) return@execute

                    val modelDirectory =
                        modelStore.prepareModel(languageToLoad)

                    if (isShutdown) return@execute

                    onStatusChanged("Loading offline recognition engine...")

                    val loadedModel = Model(
                        modelDirectory.absolutePath
                    )

                    if (isShutdown) {
                        loadedModel.close()
                        return@execute
                    }

                    val recognizer = Recognizer(
                        loadedModel,
                        16000.0f
                    )

                    if (isShutdown) {
                        recognizer.close()
                        loadedModel.close()
                        return@execute
                    }

                    val service = SpeechService(
                        recognizer,
                        16000.0f
                    )

                    synchronized(this) {

                        if (isShutdown) {
                            service.shutdown()
                            loadedModel.close()
                            return@synchronized
                        }

                        currentModel = loadedModel
                        currentRecognizer = recognizer
                        speechService = service
                        isLoadingModel = false

                        val started = service.startListening(
                            recognitionListener
                        )

                        if (started) {
                            onStatusChanged(
                                if (
                                    languageToLoad ==
                                    VoskModelStore.VoskLanguage.HINDI
                                ) {
                                    "Hindi offline listening..."
                                } else {
                                    "Indian English offline listening..."
                                }
                            )
                        } else {
                            onStatusChanged(
                                "Could not start offline listening"
                            )
                        }
                    }

                } catch (exception: Exception) {

                    isLoadingModel = false

                    if (!isShutdown) {
                        onStatusChanged(
                            "Offline model error: " +
                                (exception.localizedMessage
                                    ?: "Unknown error")
                        )
                    }
                }
            }

        } catch (exception: Exception) {
            isLoadingModel = false
            onStatusChanged(
                "Could not prepare offline model: " +
                    (exception.localizedMessage ?: "Unknown error")
            )
            return false
        }

        return true
    }

    private fun handleRecognitionResult(
        hypothesis: String?,
        isFinal: Boolean
    ) {
        if (isShutdown || hypothesis.isNullOrBlank()) return

        try {
            val json = JSONObject(hypothesis)
            val recognizedText = json.optString("text", "").trim()

            if (recognizedText.isNotBlank()) {
                onSpeechResult(recognizedText)

                if (isFinal) {
                    onStatusChanged("Speech recognized")
                }
            }
        } catch (exception: Exception) {
            if (isFinal && !isShutdown) {
                onStatusChanged("Could not read recognition result")
            }
        }
    }

    @Synchronized
    fun stopListening() {
        if (isShutdown) return

        speechService?.stop()
        onStatusChanged("Listening stopped")
    }

    @Synchronized
    fun cancelListening() {
        if (isShutdown) return

        speechService?.cancel()
        onStatusChanged("Listening cancelled")
    }

    fun speak(text: String) {
        if (isShutdown || text.isBlank()) return

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
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()

        if (!isShutdown) {
            onStatusChanged("Voice output stopped")
        }
    }

    @Synchronized
    private fun stopRecognitionResources() {

        try {
            speechService?.cancel()
            speechService?.shutdown()
        } catch (_: Exception) {
        }

        speechService = null

        try {
            currentRecognizer?.close()
        } catch (_: Exception) {
        }

        currentRecognizer = null

        try {
            currentModel?.close()
        } catch (_: Exception) {
        }

        currentModel = null
    }

    @Synchronized
    fun shutdown() {

        if (isShutdown) return

        isShutdown = true
        isLoadingModel = false

        try {
            speechService?.cancel()
            speechService?.shutdown()
        } catch (_: Exception) {
        }

        speechService = null

        try {
            currentRecognizer?.close()
        } catch (_: Exception) {
        }

        currentRecognizer = null

        try {
            currentModel?.close()
        } catch (_: Exception) {
        }

        currentModel = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        isTtsReady = false

        executor.shutdownNow()
    }
}
