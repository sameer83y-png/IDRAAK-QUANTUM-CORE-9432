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
    private const val MAX_BACKUP_CHARS = 8 * 1024 * 1024
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val AAD_PREFIX = "IDRAAK-MEMORY-BACKUP:"

    fun createBackup(context: Context, passphrase: CharArray): String? {
        var plainBytes: ByteArray? = null
        try {
            if (passphrase.size < MIN_PASSPHRASE_LENGTH) return null

            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                deriveKey(passphrase, salt),
                GCMParameterSpec(128, iv)
            )
            cipher.updateAAD((AAD_PREFIX + FORMAT_VERSION).toByteArray(StandardCharsets.UTF_8))

            val clearBytes = LocalMemoryStore.exportForBackup(context)
                .toByteArray(StandardCharsets.UTF_8)
            plainBytes = clearBytes
            val encrypted = cipher.doFinal(clearBytes)

            val result = JSONObject().apply {
                put("format", "IDRAAK_MEMORY_BACKUP")
                put("version", FORMAT_VERSION)
                put("kdf", KDF)
                put("iterations", ITERATIONS)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }.toString()

            return result.takeIf { it.length <= MAX_BACKUP_CHARS }
        } catch (_: Exception) {
            return null
        } finally {
            plainBytes?.fill(0)
            passphrase.fill('\u0000')
        }
    }

    fun restoreBackup(
        context: Context,
        backupText: String,
        passphrase: CharArray
    ): Boolean {
        var decryptedBytes: ByteArray? = null
        try {
            if (
                passphrase.size < MIN_PASSPHRASE_LENGTH ||
                backupText.isBlank() ||
                backupText.length > MAX_BACKUP_CHARS
            ) {
                return false
            }

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
            if (
                salt.size != SALT_BYTES ||
                iv.size != IV_BYTES ||
                encrypted.size < 16 ||
                encrypted.size > MAX_BACKUP_CHARS
            ) {
                return false
            }

            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt),
                GCMParameterSpec(128, iv)
            )
            cipher.updateAAD((AAD_PREFIX + FORMAT_VERSION).toByteArray(StandardCharsets.UTF_8))
            val clearBytes = cipher.doFinal(encrypted)
            decryptedBytes = clearBytes
            val plainText = String(clearBytes, StandardCharsets.UTF_8)

            // LocalMemoryStore validates every record before replacing existing data.
            return LocalMemoryStore.restoreFromBackup(context, plainText)
        } catch (_: Exception) {
            return false
        } finally {
            decryptedBytes?.fill(0)
            passphrase.fill('\u0000')
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            val keyBytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            try {
                SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }
}
