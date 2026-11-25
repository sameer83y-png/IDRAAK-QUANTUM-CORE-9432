package com.idraak.quantum

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.runBlocking

/**
 * Multilingual offline voice controller.
 * Captures 16 kHz mono PCM, writes a WAV file, and lets the multilingual
 * whisper.cpp model detect the spoken language locally. Idraak's responses
 * remain Urdu. No Android online speech-recognition service is used.
 */
class VoiceController(
    context: Context,
    private val onSpeechResult: (String) -> Unit,
    private val onStatusChanged: (String) -> Unit,
    private val onSpeechFinished: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        LANGUAGE_PREFERENCES, Context.MODE_PRIVATE
    )
    private val modelLifecycleLock = Any()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val transcriptionExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var textToSpeech: TextToSpeech? = null
    @Volatile private var isTtsReady = false
    @Volatile private var isTtsInitializationComplete = false
    @Volatile private var pendingSpeech: String? = null
    @Volatile private var isShutdown = false
    @Volatile private var isListening = false
    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var activeEchoCanceler: AcousticEchoCanceler? = null
    @Volatile private var loadedModel: WhisperModel? = null
    @Volatile private var lastDeliveredText = ""
    @Volatile private var lastDeliveredTime = 0L

    init { initializeTextToSpeech() }

    fun getRecognitionLanguage(): UrduVoiceLanguage =
        UrduVoiceLanguage.URDU

    fun setRecognitionLanguage(
        @Suppress("UNUSED_PARAMETER") language: UrduVoiceLanguage,
        resumeListening: Boolean = false
    ) {
        preferences.edit().putString(LANGUAGE_KEY, UrduVoiceLanguage.URDU.name).apply()
        onStatusChanged("اردو کی آواز پہچاننے کا نظام منتخب ہے")
        if (resumeListening) {
            cancelListening()
            startListening()
        }
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (isShutdown) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                isTtsInitializationComplete = true
                isTtsReady = false
                pendingSpeech = null
                onStatusChanged("اردو آواز کا نظام شروع نہیں ہو سکا")
                onSpeechFinished()
                return@TextToSpeech
            }
            val urduVoice = textToSpeech?.voices.orEmpty()
                .filter { it.locale.language.equals("ur", true) && !it.isNetworkConnectionRequired }
                .maxByOrNull { if (it.locale.country.equals("PK", true)) 1 else 0 }
            if (urduVoice == null || textToSpeech?.setVoice(urduVoice) != TextToSpeech.SUCCESS) {
                isTtsInitializationComplete = true
                isTtsReady = false
                pendingSpeech = null
                onStatusChanged("اردو کی آف لائن آواز دستیاب نہیں۔ فون میں اردو TTS voice data نصب کریں۔")
                onSpeechFinished()
                return@TextToSpeech
            }
            isTtsInitializationComplete = true
            isTtsReady = true
            textToSpeech?.setSpeechRate(getSpeechRate())
            onStatusChanged("اردو آواز تیار ہے")
            val queued = pendingSpeech
            pendingSpeech = null
            if (!queued.isNullOrBlank()) speak(queued)
        }
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!isShutdown) onStatusChanged("آواز میں جواب دیا جا رہا ہے")
            }
            override fun onDone(utteranceId: String?) {
                if (!isShutdown) {
                    onStatusChanged("جواب مکمل ہو گیا")
                    onSpeechFinished()
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!isShutdown) {
                    onStatusChanged("آواز میں جواب نہیں دیا جا سکا")
                    onSpeechFinished()
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
        })
    }

    @Synchronized
    fun startListening(): Boolean {
        if (isShutdown || isListening) return !isShutdown
        if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            onStatusChanged("مائیکروفون کی اجازت درکار ہے")
            return false
        }
        isListening = true
        onStatusChanged("آپ کی آواز سن رہا ہوں")
        try {
            executor.execute { captureAndTranscribe() }
        } catch (_: Exception) {
            isListening = false
            onStatusChanged("آواز سننے کا عمل شروع نہیں ہو سکا")
            return false
        }
        return true
    }

    private fun captureAndTranscribe() {
        var recorder: AudioRecord? = null
        var echoCanceler: AcousticEchoCanceler? = null
        var wav: File? = null
        try {
            if (loadedModel == null) {
                onStatusChanged("آف لائن آواز کا ماڈل لوڈ ہو رہا ہے")
                val newlyLoadedModel = runBlocking {
                    Whisper.loadModelFromAsset(appContext, MODEL_ASSET)
                }
                val modelAccepted = synchronized(modelLifecycleLock) {
                    if (isShutdown) {
                        false
                    } else {
                        loadedModel = newlyLoadedModel
                        true
                    }
                }
                if (!modelAccepted) {
                    Whisper.releaseModel(newlyLoadedModel)
                    return
                }
            }
            if (isShutdown || !isListening) return

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) throw IllegalStateException("Audio input is not supported")
            if (ContextCompat.checkSelfPermission(
                    appContext,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("مائیکروفون کی اجازت دستیاب نہیں ہے")
            }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE * 2)
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Microphone could not be initialized")
            }
            activeRecorder = recorder
            echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(recorder.audioSessionId)?.also { effect ->
                    if (!effect.enabled) effect.enabled = true
                }
            } else null
            activeEchoCanceler = echoCanceler
            recorder.startRecording()

            val pcm = java.io.ByteArrayOutputStream()
            val buffer = ShortArray(2048)
            var speechStarted = false
            var silentSamples = 0
            var totalSamples = 0
            val silenceLimit = (SAMPLE_RATE * 1.1f).toInt()
            // Do not hold the microphone open for the full 12-second capture
            // window when nobody starts speaking.
            val initialSilenceLimit = SAMPLE_RATE * 4
            val minimumSpeech = (SAMPLE_RATE * 0.45f).toInt()
            val maxSamples = SAMPLE_RATE * 12

            while (isListening && !isShutdown && totalSamples < maxSamples) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read < 0) {
                    throw IllegalStateException("Microphone read failed (code $read)")
                }
                if (read == 0) continue
                var peak = 0
                for (i in 0 until read) {
                    val sample = buffer[i]
                    peak = maxOf(peak, abs(sample.toInt()))
                    pcm.write(sample.toInt() and 0xff)
                    pcm.write((sample.toInt() shr 8) and 0xff)
                }
                totalSamples += read
                if (peak > VOICE_THRESHOLD) {
                    speechStarted = true
                    silentSamples = 0
                } else if (speechStarted) {
                    silentSamples += read
                    if (silentSamples >= silenceLimit && totalSamples >= minimumSpeech) break
                } else if (totalSamples >= initialSilenceLimit) {
                    // Empty/noisy room: release the mic promptly and resume the
                    // continuous voice loop instead of waiting for 12 seconds.
                    break
                }
            }
            recorder.stop()
            activeRecorder = null
            if (!isListening || isShutdown) return
            if (!speechStarted || totalSamples < minimumSpeech) {
                // Release the listening state before notifying the UI. Otherwise
                // continuous voice mode cannot start the next capture.
                isListening = false
                onStatusChanged("آواز واضح نہیں آئی، دوبارہ بولیں")
                onSpeechFinished()
                return
            }

            wav = File(appContext.cacheDir, "idraak_urdu_${SystemClock.elapsedRealtime()}.wav")
            writeWav(wav, pcm.toByteArray())
            onStatusChanged("آپ کی بات سمجھ رہا ہوں")

            // Resume microphone capture immediately while Whisper works on the
            // previous segment. This enables listening during command processing
            // and, when AEC is active, while Urdu TTS is speaking.
            isListening = false
            onSpeechFinished()
            val transcriptionFile = wav
            wav = null
            synchronized(modelLifecycleLock) {
                if (isShutdown) {
                    transcriptionFile.delete()
                    return
                }
                transcriptionExecutor.execute {
                try {
                    val result = runBlocking {
                        Whisper.transcribe(
                            loadedModel ?: throw IllegalStateException("Urdu model not loaded"),
                            transcriptionFile.absolutePath,
                            WhisperConfig(language = "auto")
                        ).text.trim()
                    }
                    if (isShutdown) return@execute
                    if (result.isNotBlank()) {
                        val now = SystemClock.elapsedRealtime()
                        if (result != lastDeliveredText || now - lastDeliveredTime > 1200L) {
                            lastDeliveredText = result
                            lastDeliveredTime = now
                            onStatusChanged("آپ کی بات سمجھ لی")
                            onSpeechResult(result)
                        }
                    } else {
                        onStatusChanged("بات سمجھ نہیں آئی، ذرا دوبارہ کہیں")
                    }
                } catch (error: Exception) {
                    if (!isShutdown) {
                        onStatusChanged("اردو آف لائن آواز میں مسئلہ: ${error.localizedMessage ?: "نامعلوم خرابی"}")
                    }
                } finally {
                    transcriptionFile.delete()
                }
                }
            }
        } catch (error: Exception) {
            if (!isShutdown) {
                isListening = false
                onStatusChanged("اردو آف لائن آواز میں مسئلہ: ${error.localizedMessage ?: "نامعلوم خرابی"}")
                onSpeechFinished()
            }
        } finally {
            try { recorder?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            activeRecorder = null
            try { echoCanceler?.release() } catch (_: Exception) {}
            if (activeEchoCanceler === echoCanceler) activeEchoCanceler = null
            wav?.delete()
        }
    }

    private fun writeWav(file: File, pcm: ByteArray) {
        FileOutputStream(file).use { out ->
            val dataLength = pcm.size
            val header = ByteArray(44)
            fun putInt(offset: Int, value: Int) {
                for (i in 0..3) header[offset + i] = (value shr (8 * i)).toByte()
            }
            fun putShort(offset: Int, value: Int) {
                header[offset] = value.toByte()
                header[offset + 1] = (value shr 8).toByte()
            }
            "RIFF".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
            putInt(4, 36 + dataLength)
            "WAVE".toByteArray(Charsets.US_ASCII).copyInto(header, 8)
            "fmt ".toByteArray(Charsets.US_ASCII).copyInto(header, 12)
            putInt(16, 16)
            putShort(20, 1)
            putShort(22, 1)
            putInt(24, SAMPLE_RATE)
            putInt(28, SAMPLE_RATE * 2)
            putShort(32, 2)
            putShort(34, 16)
            "data".toByteArray(Charsets.US_ASCII).copyInto(header, 36)
            putInt(40, dataLength)
            out.write(header)
            out.write(pcm)
        }
    }

    @Synchronized fun stopListening() = cancelListening()

    @Synchronized
    fun cancelListening() {
        isListening = false
        try { activeRecorder?.stop() } catch (_: Exception) {}
        try { activeEchoCanceler?.release() } catch (_: Exception) {}
        activeEchoCanceler = null
        if (!isShutdown) onStatusChanged("سننا روک دیا گیا")
    }

    fun speak(text: String) = speakInternal(text, TextToSpeech.QUEUE_FLUSH)

    /** Adds a final reply after a spoken progress update instead of interrupting it. */
    fun speakQueued(text: String) = speakInternal(text, TextToSpeech.QUEUE_ADD)

    private fun speakInternal(text: String, queueMode: Int) {
        if (isShutdown || text.isBlank()) return
        // Never keep an unfiltered microphone open during speaker output.
        // Devices without working AEC retain the safe listen-then-speak fallback.
        if (isListening && activeEchoCanceler?.enabled != true) cancelListening()
        val tts = textToSpeech
        if (tts == null || !isTtsReady) {
            if (isTtsInitializationComplete) {
                pendingSpeech = null
                onStatusChanged("اردو کی آف لائن آواز دستیاب نہیں۔ فون میں اردو TTS voice data نصب کریں۔")
                onSpeechFinished()
            } else {
                pendingSpeech = if (queueMode == TextToSpeech.QUEUE_ADD && !pendingSpeech.isNullOrBlank()) {
                    pendingSpeech + "۔ " + text
                } else {
                    text
                }
                onStatusChanged("اردو آواز تیار ہو رہی ہے")
            }
            return
        }
        tts.setSpeechRate(getSpeechRate())
        val utteranceId = "idraak_urdu_output_${SystemClock.elapsedRealtime()}"
        if (tts.speak(text, queueMode, Bundle(), utteranceId) == TextToSpeech.ERROR) {
            onStatusChanged("جواب بولنا ممکن نہیں ہوا")
            onSpeechFinished()
        }
    }

    fun stopSpeaking() { textToSpeech?.stop() }

    @Synchronized
    fun shutdown() {
        val modelToRelease = synchronized(modelLifecycleLock) {
            if (isShutdown) return
            isShutdown = true
            val model = loadedModel
            loadedModel = null
            transcriptionExecutor.shutdown()
            model
        }
        isListening = false
        try { activeRecorder?.stop() } catch (_: Exception) {}
        try { activeEchoCanceler?.release() } catch (_: Exception) {}
        activeEchoCanceler = null
        activeRecorder = null
        executor.shutdownNow()

        // Whisper's native model must outlive every in-flight transcription.
        // Wait off the UI/service thread, then release it exactly once.
        if (modelToRelease != null) {
            Thread({
                var interrupted = false
                while (!executor.isTerminated) {
                    try {
                        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                while (!transcriptionExecutor.isTerminated) {
                    try {
                        transcriptionExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                try {
                    Whisper.releaseModel(modelToRelease)
                } catch (_: Exception) {
                }
                if (interrupted) Thread.currentThread().interrupt()
            }, "IdraakWhisperCleanup").apply {
                isDaemon = true
                start()
            }
        }

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        pendingSpeech = null
        isTtsReady = false
    }

    fun getSpeechRate(): Float = preferences.getFloat(SPEECH_RATE_KEY, 1.0f).coerceIn(0.75f, 1.5f)

    fun setSpeechRate(rate: Float) {
        val safe = rate.coerceIn(0.75f, 1.5f)
        preferences.edit().putFloat(SPEECH_RATE_KEY, safe).apply()
        textToSpeech?.setSpeechRate(safe)
        onStatusChanged("رفتار محفوظ ہو گئی")
    }

    companion object {
        private const val LANGUAGE_PREFERENCES = "idraak_voice_preferences"
        private const val LANGUAGE_KEY = "selected_recognition_language"
        private const val SPEECH_RATE_KEY = "speech_output_rate"
        private const val MODEL_ASSET = "models/ggml-small.bin"
        private const val SAMPLE_RATE = 16000
        private const val VOICE_THRESHOLD = 650
    }
}
