
package com.idraak.quantum.engine

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * IDRAAK QUANTUM CORE
 *
 * Offline-first local memory store.
 *
 * Features:
 * - Categorized memory records
 * - Pending / verified / rejected states
 * - Local backup and recovery
 * - Confirmed SharedPreferences writes
 * - Safe record parsing
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
     * New records should normally remain PENDING.
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
     * Corrections remain pending until reviewed.
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
     * Proposed learning is never automatically trusted.
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
     * Update a memory status.
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
     * Search by title, content or category.
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
                .take(limit)
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
                .filter { it.category == category.name }
                .takeLast(limit)
                .reversed()
        }
    }

    /**
     * Only explicitly verified learning is returned.
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
     * The UI must ask for user confirmation first.
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
    private fun readAll(context: Context): List<MemoryRecord> {

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
     * If primary JSON is damaged, try the backup.
     */
    private fun readArray(context: Context): JSONArray {

        val prefs = preferences(context)

        val primary = prefs.getString(MEMORY_KEY, null)

        if (!primary.isNullOrBlank()) {
            try {
                return JSONArray(primary)
            } catch (_: Exception) {
                // Try backup below.
            }
        }

        val backup = prefs.getString(BACKUP_KEY, null)

        if (!backup.isNullOrBlank()) {
            try {
                return JSONArray(backup)
            } catch (_: Exception) {
                // Both copies are damaged.
            }
        }

        return JSONArray()
    }

    /**
     * Write memory with backup protection.
     *
     * First save the previous valid primary as backup.
     * Then write the new primary.
     * Return true only if the primary write succeeds.
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

            // Validate the new JSON before saving.
            JSONArray(newData)

            val oldPrimary = prefs.getString(MEMORY_KEY, null)

            if (!oldPrimary.isNullOrBlank()) {
                try {
                    JSONArray(oldPrimary)

                    val backupSaved = prefs.edit()
                        .putString(BACKUP_KEY, oldPrimary)
                        .commit()

                    if (!backupSaved) {
                        return false
                    }

                } catch (_: Exception) {
                    // Do not overwrite a valid backup with corrupt data.
                }
            }

            prefs.edit()
                .putString(MEMORY_KEY, newData)
                .commit()

        } catch (_: Exception) {
            false
        }
    }

    private fun preferences(context: Context): SharedPreferences {
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
    private fun containsSensitivePattern(value: String): Boolean {

        val text = value.lowercase(Locale.ROOT)

        val patterns = listOf(
            Regex("""\b\d{4,8}\b"""),
            Regex("""\bpassword\b"""),
            Regex("""\bpasscode\b"""),
            Regex("""\botp\b"""),
            Regex("""\bpin code\b"""),
            Regex("""\bsecret key\b""")
        )

        return patterns.any { it.containsMatchIn(text) }
    }
}
