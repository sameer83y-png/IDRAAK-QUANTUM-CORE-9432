package com.idraak.quantum

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device Urdu speech transcription powered by whisper.cpp.
 *
 * This first-stage engine accepts recorded audio files. The microphone capture
 * and continuous-listening adapter is integrated separately so the existing
 * voice service is not replaced until the complete pipeline is build-verified.
 */
class UrduWhisperEngine(context: Context) {

    private val appContext = context.applicationContext
    private val modelMutex = Mutex()
    @Volatile
    private var loadedModel: WhisperModel? = null

    suspend fun load(): Boolean = modelMutex.withLock {
        if (loadedModel != null) return@withLock true

        withContext(Dispatchers.IO) {
            loadedModel = Whisper.loadModelFromAsset(
                appContext,
                MODEL_ASSET
            )
        }
        loadedModel != null
    }

    suspend fun transcribe(audioFile: File): String = modelMutex.withLock {
        require(audioFile.isFile && audioFile.length() > 44L) {
            "آواز کی ریکارڈنگ دستیاب نہیں ہے۔"
        }

        val model = loadedModel ?: withContext(Dispatchers.IO) {
            Whisper.loadModelFromAsset(appContext, MODEL_ASSET).also {
                loadedModel = it
            }
        }

        withContext(Dispatchers.IO) {
            Whisper.transcribe(
                model,
                audioFile.absolutePath,
                WhisperConfig(language = URDU_LANGUAGE)
            ).text.trim()
        }
    }

    suspend fun release() = modelMutex.withLock {
        loadedModel?.let { model ->
            withContext(Dispatchers.IO) {
                Whisper.releaseModel(model)
            }
        }
        loadedModel = null
    }

    companion object {
        private const val MODEL_ASSET = "models/ggml-small.bin"
        private const val URDU_LANGUAGE = "ur"
    }
}
