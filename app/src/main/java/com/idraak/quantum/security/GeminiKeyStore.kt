package com.idraak.quantum.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object GeminiKeyStore {

    private const val PREFS = "idraak_secure_settings"
    private const val KEY_ALIAS = "idraak_gemini_api_key"
    private const val KEY_NAME = "encrypted_gemini_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey is SecretKey) return existingKey

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or
                KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    fun save(context: Context, apiKey: String): Boolean {
        return try {
            val cleanKey = apiKey.trim()
            require(cleanKey.isNotEmpty())

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

            val encrypted = cipher.doFinal(
                cleanKey.toByteArray(StandardCharsets.UTF_8)
            )

            val storedValue = Base64.encodeToString(
                cipher.iv + encrypted,
                Base64.NO_WRAP
            )

            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NAME, storedValue)
                .commit()
        } catch (_: Exception) {
            false
        }
    }

    fun get(context: Context): String? {
        return try {
            val storedValue = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_NAME, null) ?: return null

            val combined = Base64.decode(
                storedValue,
                Base64.NO_WRAP
            )

            if (combined.size <= 12) return null

            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(128, iv)
            )

            String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            null
        }
    }

    fun delete(context: Context): Boolean {
        return try {
            val removed = context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_NAME)
                .commit()

            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }

            removed
        } catch (_: Exception) {
            false
        }
    }
}
