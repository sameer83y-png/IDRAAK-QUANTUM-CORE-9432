package com.idraak.quantum.engine

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Creates portable, passphrase-protected memory backups.
 *
 * The Android Keystore key is intentionally not used here: a portable backup
 * must remain restorable after reinstalling the app on the same or a new phone.
 */
object MemoryBackupManager {

    private const val FORMAT_VERSION = 1
    private const val ITERATIONS = 180_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MIN_PASSPHRASE_LENGTH = 10
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"

    fun createBackup(context: Context, passphrase: CharArray): String? {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) return null

        return try {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD("IDRAAK-MEMORY-BACKUP:$FORMAT_VERSION".toByteArray(StandardCharsets.UTF_8))

            val plainText = LocalMemoryStore.exportForBackup(context)
            val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            JSONObject().apply {
                put("format", "IDRAAK_MEMORY_BACKUP")
                put("version", FORMAT_VERSION)
                put("kdf", KDF)
                put("iterations", ITERATIONS)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }.toString()
        } catch (_: Exception) {
            null
        } finally {
            passphrase.fill('\u0000')
        }
    }

    fun restoreBackup(
        context: Context,
        backupText: String,
        passphrase: CharArray
    ): Boolean {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) {
            passphrase.fill('\u0000')
            return false
        }

        return try {
            val envelope = JSONObject(backupText)
            if (
                envelope.optString("format") != "IDRAAK_MEMORY_BACKUP" ||
                envelope.optInt("version", -1) != FORMAT_VERSION ||
                envelope.optString("kdf") != KDF ||
                envelope.optInt("iterations", -1) != ITERATIONS
            ) {
                return false
            }

            val salt = Base64.decode(envelope.getString("salt"), Base64.NO_WRAP)
            val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
            val encrypted = Base64.decode(envelope.getString("data"), Base64.NO_WRAP)
            if (salt.size != SALT_BYTES || iv.size != IV_BYTES || encrypted.size < 16) {
                return false
            }

            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt),
                GCMParameterSpec(128, iv)
            )
            cipher.updateAAD("IDRAAK-MEMORY-BACKUP:$FORMAT_VERSION".toByteArray(StandardCharsets.UTF_8))
            val plainText = String(
                cipher.doFinal(encrypted),
                StandardCharsets.UTF_8
            )

            LocalMemoryStore.restoreFromBackup(context, plainText)
        } catch (_: Exception) {
            false
        } finally {
            passphrase.fill('\u0000')
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
