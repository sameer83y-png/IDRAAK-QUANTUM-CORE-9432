package com.idraak.quantum.engine

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * IDRAAK QUANTUM CORE
 *
 * Offline-first memory storage.
 *
 * Memory rules:
 * - New information remains PENDING by default.
 * - Only VERIFIED knowledge is returned as trusted knowledge.
 * - Failed experiences are not treated as successful learning.
 * - Existing records remain compatible with the current dashboard.
 *
 * IMPORTANT:
 * SharedPreferences is not encrypted.
 * Do not store passwords, OTPs or private credentials.
 */
object LocalMemoryStore {

    private const val PREFS_NAME = "idraak_local_memory"
    private const val MEMORY_KEY = "memory_records"
    private const val BACKUP_KEY = "memory_records_backup"
    private const val MAX_RECORDS = 500
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val KEY_ALIAS = "idraak_local_memory_aes"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

    private val lock = Any()

    enum class Category {
        USER_PREFERENCE,
        GENERAL_FACT,
        COMMAND,
        EXPERIENCE,
        CORRECTION,
        LEARNING
    }

    enum class Status {
        PENDING,
        SUCCESS,
        FAILED,
        VERIFIED,
        REJECTED
    }

    data class MemoryRecord(
        val id: String,
        val category: String,
        val title: String,
        val content: String,
        val status: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    /**
     * Save a new memory.
     * New information should normally remain PENDING.
     */
    fun save(
        context: Context,
        category: Category,
        title: String,
        content: String,
        status: Status = Status.PENDING
    ): Boolean {

        val safeTitle = title.trim()
        val safeContent = content.trim()

        if (safeTitle.isBlank() || safeContent.isBlank()) {
            return false
        }

        if (
            containsSensitivePattern(safeTitle) ||
            containsSensitivePattern(safeContent)
        ) {
            return false
        }

        return synchronized(lock) {
            try {
                val memories = readArray(context)
                val now = System.currentTimeMillis()

                val record = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("category", category.name)
                    put("title", safeTitle)
                    put("content", safeContent)
                    put("status", status.name)
                    put("createdAt", now)
                    put("updatedAt", now)
                }

                memories.put(record)

                while (memories.length() > MAX_RECORDS) {
                    memories.remove(0)
                }

                writeArray(context, memories)

            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Save an experience without storing the full command.
     */
    fun saveExperience(
        context: Context,
        commandType: String,
        outcome: Status,
        lesson: String = ""
    ): Boolean {

        val safeCommand = commandType.trim()
        val safeLesson = lesson.trim()

        if (safeCommand.isBlank()) {
            return false
        }

        if (
            containsSensitivePattern(safeCommand) ||
            (
                safeLesson.isNotEmpty() &&
                containsSensitivePattern(safeLesson)
            )
        ) {
            return false
        }

        return save(
            context = context,
            category = Category.EXPERIENCE,
            title = safeCommand,
            content = if (safeLesson.isBlank()) {
                "Outcome recorded: ${outcome.name}"
            } else {
                safeLesson
            },
            status = outcome
        )
    }

    /**
     * Save a correction.
     * A correction is not trusted until explicitly verified.
     */
    fun saveCorrection(
        context: Context,
        topic: String,
        correctedInformation: String
    ): Boolean {
        return save(
            context = context,
            category = Category.CORRECTION,
            title = topic,
            content = correctedInformation,
            status = Status.PENDING
        )
    }

    /**
     * Propose new learning.
     * Proposed learning always starts as PENDING.
     */
    fun proposeLearning(
        context: Context,
        topic: String,
        lesson: String
    ): Boolean {
        return save(
            context = context,
            category = Category.LEARNING,
            title = topic,
            content = lesson,
            status = Status.PENDING
        )
    }

    /**
     * Update the review status of a memory.
     * The caller must obtain user approval before verification.
     */
    fun updateStatus(
        context: Context,
        memoryId: String,
        newStatus: Status
    ): Boolean {

        if (memoryId.isBlank()) return false

        return synchronized(lock) {
            try {
                val memories = readArray(context)
                var found = false

                for (index in 0 until memories.length()) {
                    val item = memories.optJSONObject(index)
                        ?: continue

                    if (item.optString("id") == memoryId) {
                        item.put("status", newStatus.name)
                        item.put(
                            "updatedAt",
                            System.currentTimeMillis()
                        )
                        found = true
                        break
                    }
                }

                if (!found) {
                    false
                } else {
                    writeArray(context, memories)
                }

            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Find one memory by its unique ID.
     */
    fun findById(
        context: Context,
        memoryId: String
    ): MemoryRecord? {

        if (memoryId.isBlank()) return null

        return synchronized(lock) {
            readAll(context).firstOrNull {
                it.id == memoryId
            }
        }
    }

    /**
     * Return memories waiting for review.
     */
    fun getPendingReview(
        context: Context,
        limit: Int = 100
    ): List<MemoryRecord> {

        if (limit <= 0) return emptyList()

        return synchronized(lock) {
            readAll(context)
                .filter {
                    it.status == Status.PENDING.name
                }
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Return only explicitly VERIFIED knowledge.
     *
     * PENDING, SUCCESS, FAILED and REJECTED records
     * are never returned as trusted knowledge.
     */
    fun getTrustedKnowledge(
        context: Context,
        limit: Int = 100
    ): List<MemoryRecord> {

        if (limit <= 0) return emptyList()

        val trustedCategories = setOf(
            Category.USER_PREFERENCE.name,
            Category.GENERAL_FACT.name,
            Category.CORRECTION.name,
            Category.LEARNING.name
        )

        return synchronized(lock) {
            readAll(context)
                .filter {
                    it.status == Status.VERIFIED.name &&
                    it.category in trustedCategories
                }
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Return verified learning only.
     */
    fun getVerifiedLearning(
        context: Context,
        limit: Int = 50
    ): List<MemoryRecord> {

        if (limit <= 0) return emptyList()

        return synchronized(lock) {
            readAll(context)
                .filter {
                    it.category == Category.LEARNING.name &&
                    it.status == Status.VERIFIED.name
                }
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Get memories by category.
     */
    fun getByCategory(
        context: Context,
        category: Category,
        limit: Int = 50
    ): List<MemoryRecord> {

        if (limit <= 0) return emptyList()

        return synchronized(lock) {
            readAll(context)
                .filter {
                    it.category == category.name
                }
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Search title, content, category or status.
     * Newest matching records appear first.
     */
    fun search(
        context: Context,
        query: String,
        limit: Int = 30
    ): List<MemoryRecord> {

        if (query.isBlank() || limit <= 0) {
            return emptyList()
        }

        val keyword = normalize(query)

        return synchronized(lock) {
            readAll(context)
                .filter {
                    normalize(it.title).contains(keyword) ||
                    normalize(it.content).contains(keyword) ||
                    normalize(it.category).contains(keyword) ||
                    normalize(it.status).contains(keyword)
                }
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Get all memories, newest first.
     */
    fun getAll(
        context: Context,
        limit: Int = 100
    ): List<MemoryRecord> {

        if (limit <= 0) return emptyList()

        return synchronized(lock) {
            readAll(context)
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Count stored memories.
     */
    fun getCount(context: Context): Int {
        return synchronized(lock) {
            readArray(context).length()
        }
    }

    /**
     * Count trusted knowledge only.
     */
    fun getTrustedKnowledgeCount(context: Context): Int {
        return getTrustedKnowledge(
            context = context,
            limit = MAX_RECORDS
        ).size
    }

    /**
     * Edit an existing memory.
     *
     * Edited information is always returned to PENDING so a previously
     * verified fact cannot silently remain trusted after being changed.
     */
    fun update(
        context: Context,
        memoryId: String,
        title: String,
        content: String
    ): Boolean {
        val safeTitle = title.trim()
        val safeContent = content.trim()

        if (memoryId.isBlank() || safeTitle.isBlank() || safeContent.isBlank()) {
            return false
        }

        if (containsSensitivePattern(safeTitle) || containsSensitivePattern(safeContent)) {
            return false
        }

        return synchronized(lock) {
            try {
                val memories = readArray(context)
                var found = false

                for (index in 0 until memories.length()) {
                    val item = memories.optJSONObject(index) ?: continue
                    if (item.optString("id") == memoryId) {
                        item.put("title", safeTitle)
                        item.put("content", safeContent)
                        item.put("status", Status.PENDING.name)
                        item.put("updatedAt", System.currentTimeMillis())
                        found = true
                        break
                    }
                }

                if (found) writeArray(context, memories) else false
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Delete one memory.
     */
    fun delete(
        context: Context,
        memoryId: String
    ): Boolean {

        if (memoryId.isBlank()) return false

        return synchronized(lock) {
            try {
                val memories = readArray(context)
                val updated = JSONArray()
                var found = false

                for (index in 0 until memories.length()) {
                    val item = memories.optJSONObject(index)
                        ?: continue

                    if (item.optString("id") == memoryId) {
                        found = true
                    } else {
                        updated.put(item)
                    }
                }

                if (!found) {
                    false
                } else {
                    writeArray(context, updated)
                }

            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Clear all memories.
     * UI must ask for confirmation first.
     */
    fun clearAll(context: Context): Boolean {
        return synchronized(lock) {
            try {
                writeArray(context, JSONArray())
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Read and validate stored records.
     */
    private fun readAll(
        context: Context
    ): List<MemoryRecord> {

        val memories = readArray(context)
        val result = mutableListOf<MemoryRecord>()

        for (index in 0 until memories.length()) {
            val item = memories.optJSONObject(index)
                ?: continue

            val id = item.optString("id").trim()
            val category = item.optString("category").trim()
            val title = item.optString("title").trim()
            val content = item.optString("content").trim()
            val status = item.optString("status").trim()

            if (
                id.isBlank() ||
                category.isBlank() ||
                title.isBlank() ||
                content.isBlank() ||
                status.isBlank()
            ) {
                continue
            }

            result.add(
                MemoryRecord(
                    id = id,
                    category = category,
                    title = title,
                    content = content,
                    status = status,
                    createdAt = item.optLong("createdAt", 0L),
                    updatedAt = item.optLong("updatedAt", 0L)
                )
            )
        }

        return result
    }

    /**
     * Read primary memory.
     * If primary JSON is damaged, try backup.
     */
    private fun readArray(context: Context): JSONArray {

        val prefs = preferences(context)
        val primary = prefs.getString(MEMORY_KEY, null)

        if (!primary.isNullOrBlank()) {
            val decoded = decodeStoredArray(primary)

            if (decoded != null) {
                // Migrate older plaintext records in place. If encryption is
                // temporarily unavailable, keep the original data readable.
                if (!primary.startsWith(ENCRYPTED_PREFIX)) {
                    val encrypted = encryptStoredValue(primary)
                    if (encrypted != null) {
                        prefs.edit()
                            .putString(MEMORY_KEY, encrypted)
                            .putString(BACKUP_KEY, encrypted)
                            .commit()
                    }
                }
                return decoded
            }
        }

        val backup = prefs.getString(BACKUP_KEY, null)

        if (!backup.isNullOrBlank()) {
            val decodedBackup = decodeStoredArray(backup)
            if (decodedBackup != null) {
                return decodedBackup
            }
        }

        return JSONArray()
    }

    private fun decodeStoredArray(storedValue: String): JSONArray? {
        return try {
            val json = if (storedValue.startsWith(ENCRYPTED_PREFIX)) {
                decryptStoredValue(storedValue) ?: return null
            } else {
                storedValue
            }
            JSONArray(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun encryptStoredValue(plainText: String): String? {
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, getMemoryKey())
            val encrypted = cipher.doFinal(
                plainText.toByteArray(StandardCharsets.UTF_8)
            )
            val combined = cipher.iv + encrypted
            ENCRYPTED_PREFIX + Base64.encodeToString(
                combined,
                Base64.NO_WRAP
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decryptStoredValue(storedValue: String): String? {
        return try {
            if (!storedValue.startsWith(ENCRYPTED_PREFIX)) {
                return null
            }

            val encoded = storedValue.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= IV_LENGTH) return null

            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getMemoryKey(),
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

    private fun getMemoryKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE)
        keyStore.load(null)

        val existingKey = keyStore.getKey(KEY_ALIAS, null)
        if (existingKey is SecretKey) return existingKey

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE
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

    /**
     * Write memory with backup protection.
     */
    private fun writeArray(
        context: Context,
        memories: JSONArray
    ): Boolean {

        return try {
            if (memories.length() > MAX_RECORDS) {
                return false
            }

            val prefs = preferences(context)
            val newData = memories.toString()

            // Validate new JSON before encrypting it.
            JSONArray(newData)

            val encryptedNewData = encryptStoredValue(newData)
                ?: return false

            // Keep the recovery copy synchronized with the exact current state.
            // Retaining the previous version here could resurrect a memory after
            // the user explicitly deletes or clears it and the primary is damaged.
            prefs.edit()
                .putString(MEMORY_KEY, encryptedNewData)
                .putString(BACKUP_KEY, encryptedNewData)
                .commit()

        } catch (_: Exception) {
            false
        }
    }

    private fun preferences(
        context: Context
    ): SharedPreferences {
        return context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    private fun normalize(value: String): String {
        return value.trim().lowercase(Locale.ROOT)
    }

    /**
     * Basic accidental-secret protection.
     * This is not a complete privacy or security filter.
     */
    private fun containsSensitivePattern(
        value: String
    ): Boolean {

        val text = value.lowercase(Locale.ROOT)

        val patterns = listOf(
            Regex("""\b\d{4,8}\b"""),
            Regex("""\bpassword\b"""),
            Regex("""\bpasscode\b"""),
            Regex("""\botp\b"""),
            Regex("""\bpin code\b"""),
            Regex("""\bsecret key\b""")
        )

        return patterns.any {
            it.containsMatchIn(text)
        }
    }
}
