package com.idraak.quantum

import android.content.Context
import java.io.File
import java.io.IOException

class VoskModelStore(context: Context) {

    private val appContext = context.applicationContext
    private val assets = appContext.assets

    enum class VoskLanguage(
        val assetPath: String,
        val directoryName: String
    ) {
        HINDI(
            assetPath = "vosk/hi",
            directoryName = "hindi"
        ),
        INDIAN_ENGLISH(
            assetPath = "vosk/en-in",
            directoryName = "indian_english"
        )
    }

    /**
     * Copies the selected bundled Vosk model into internal storage.
     *
     * IMPORTANT:
     * Call this method on a background thread.
     */
    @Synchronized
    @Throws(IOException::class)
    fun prepareModel(language: VoskLanguage): File {

        val modelDirectory = File(
            appContext.filesDir,
            "vosk_models/${language.directoryName}"
        )

        val configFile = File(
            modelDirectory,
            "conf/model.conf"
        )

        // Reuse an already extracted model.
        if (modelDirectory.isDirectory && configFile.isFile) {
            return modelDirectory
        }

        val temporaryDirectory = File(
            modelDirectory.parentFile,
            "${language.directoryName}_temporary"
        )

        // Remove incomplete previous extraction.
        temporaryDirectory.deleteRecursively()
        modelDirectory.deleteRecursively()

        if (!temporaryDirectory.mkdirs()) {
            throw IOException(
                "Could not create model directory: $temporaryDirectory"
            )
        }

        try {
            copyAssetDirectory(
                language.assetPath,
                temporaryDirectory
            )

            val copiedConfig = File(
                temporaryDirectory,
                "conf/model.conf"
            )

            if (!copiedConfig.isFile) {
                throw IOException(
                    "Vosk model configuration is missing: ${language.directoryName}"
                )
            }

            val parent = modelDirectory.parentFile

            if (parent == null || !parent.exists() && !parent.mkdirs()) {
                throw IOException("Could not create model parent directory")
            }

            if (!temporaryDirectory.renameTo(modelDirectory)) {
                throw IOException(
                    "Could not move extracted Vosk model into final location"
                )
            }

            return modelDirectory

        } catch (exception: Exception) {
            temporaryDirectory.deleteRecursively()

            if (exception is IOException) {
                throw exception
            }

            throw IOException(
                "Failed to prepare ${language.directoryName} Vosk model",
                exception
            )
        }
    }

    private fun copyAssetDirectory(
        assetPath: String,
        destination: File
    ) {

        val children = assets.list(assetPath)
            ?: throw IOException("Cannot read APK assets: $assetPath")

        if (children.isEmpty()) {
            destination.parentFile?.let {
                if (!it.exists() && !it.mkdirs()) {
                    throw IOException("Cannot create directory: $it")
                }
            }

            assets.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            return
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw IOException("Cannot create directory: $destination")
        }

        for (child in children) {
            copyAssetDirectory(
                "$assetPath/$child",
                File(destination, child)
            )
        }
    }
}
