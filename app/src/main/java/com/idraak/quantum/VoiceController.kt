package com.idraak.quantum

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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
import kotlin.math.abs

/**
 * Urdu-only offline voice controller.
 * Captures 16 kHz mono PCM, writes a WAV file, and transcribes it locally
 * with whisper.cpp. No Android online speech-recognition service is used.
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
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var textToSpeech: TextToSpeech? = null
    @Volatile private var isTtsReady = false
    @Volatile private var pendingSpeech: String? = null
    @Volatile private var isShutdown = false
    @Volatile private var isListening = false
    @Volatile private var activeRecorder: AudioRecord? = null
    @Volatile private var loadedModel: WhisperModel? = null
    @Volatile private var lastDeliveredText = ""
    @Volatile private var lastDeliveredTime = 0L

    init { initializeTextToSpeech() }

    fun getRecognitionLanguage(): VoskModelStore.VoskLanguage =
        VoskModelStore.VoskLanguage.URDU

    fun setRecognitionLanguage(
        language: VoskModelStore.VoskLanguage,
        resumeListening: Boolean = false
    ) {
        preferences.edit().putString(LANGUAGE_KEY, VoskModelStore.VoskLanguage.URDU.name).apply()
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
                onStatusChanged("اردو آواز کا نظام شروع نہیں ہو سکا")
                onSpeechFinished()
                return@TextToSpeech
            }
            val urduVoice = textToSpeech?.voices.orEmpty()
                .filter { it.locale.language.equals("ur", true) && !it.isNetworkConnectionRequired }
                .maxByOrNull { if (it.locale.country.equals("PK", true)) 1 else 0 }
            if (urduVoice == null || textToSpeech?.setVoice(urduVoice) != TextToSpeech.SUCCESS) {
                isTtsReady = false
                onStatusChanged("اردو کی آف لائن آواز دستیاب نہیں۔ فون میں اردو TTS voice data نصب کریں۔")
                onSpeechFinished()
                return@TextToSpeech
            }
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
        onStatusChanged("اردو میں سن رہا ہوں")
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
        var wav: File? = null
        try {
            if (loadedModel == null) {
                onStatusChanged("اردو آف لائن ماڈل لوڈ ہو رہا ہے")
                loadedModel = Whisper.loadModelFromAsset(appContext, MODEL_ASSET)
            }
            if (isShutdown || !isListening) return

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) throw IllegalStateException("Audio input is not supported")
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE * 2)
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("Microphone could not be initialized")
            }
            activeRecorder = recorder
            recorder.startRecording()

            val pcm = java.io.ByteArrayOutputStream()
            val buffer = ShortArray(2048)
            var speechStarted = false
            var silentSamples = 0
            var totalSamples = 0
            val silenceLimit = (SAMPLE_RATE * 1.1f).toInt()
            val minimumSpeech = (SAMPLE_RATE * 0.45f).toInt()
            val maxSamples = SAMPLE_RATE * 12

            while (isListening && !isShutdown && totalSamples < maxSamples) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
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
                }
            }
            recorder.stop()
            activeRecorder = null
            if (!isListening || isShutdown) return
            if (!speechStarted || totalSamples < minimumSpeech) {
                onStatusChanged("آواز واضح نہیں آئی، دوبارہ بولیں")
                onSpeechFinished()
                return
            }

            wav = File(appContext.cacheDir, "idraak_urdu_${SystemClock.elapsedRealtime()}.wav")
            writeWav(wav, pcm.toByteArray())
            onStatusChanged("اردو آواز سمجھ رہا ہوں")
            val result = Whisper.transcribe(
                loadedModel ?: throw IllegalStateException("Urdu model not loaded"),
                wav.absolutePath,
                WhisperConfig(language = "ur")
            ).text.trim()

            if (isShutdown || !isListening) return
            if (result.isNotBlank()) {
                val now = SystemClock.elapsedRealtime()
                if (result != lastDeliveredText || now - lastDeliveredTime > 1200L) {
                    lastDeliveredText = result
                    lastDeliveredTime = now
                    isListening = false
                    onStatusChanged("آپ کی بات سمجھ لی")
                    onSpeechResult(result)
                } else {
                    onSpeechFinished()
                }
            } else {
                onStatusChanged("بات سمجھ نہیں آئی، ذرا دوبارہ کہیں")
                onSpeechFinished()
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
        if (!isShutdown) onStatusChanged("سننا روک دیا گیا")
    }

    fun speak(text: String) {
        if (isShutdown || text.isBlank()) return
        val tts = textToSpeech
        if (tts == null || !isTtsReady) {
            pendingSpeech = text
            onStatusChanged("اردو آواز تیار ہو رہی ہے")
            return
        }
        tts.setSpeechRate(getSpeechRate())
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "idraak_urdu_output")
            == TextToSpeech.ERROR) {
            onStatusChanged("جواب بولنا ممکن نہیں ہوا")
            onSpeechFinished()
        }
    }

    fun stopSpeaking() { textToSpeech?.stop() }

    @Synchronized
    fun shutdown() {
        if (isShutdown) return
        isShutdown = true
        isListening = false
        try { activeRecorder?.stop() } catch (_: Exception) {}
        activeRecorder = null
        try { loadedModel?.let { Whisper.releaseModel(it) } } catch (_: Exception) {}
        loadedModel = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        pendingSpeech = null
        isTtsReady = false
        executor.shutdownNow()
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
