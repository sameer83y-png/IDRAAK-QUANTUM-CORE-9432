package com.idraak.quantum.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * IDRAAK QUANTUM CORE
 * Local Memory Store
 *
 * Offline-first local memory foundation.
 * Stores categorized memories, experience outcomes,
 * corrections and verified learning records.
 *
 * Does not store complete private conversations,
 * passwords, OTPs or message contents.
 */
object LocalMemoryStore {

    private const val PREFS_NAME = "idraak_local_memory"
    private const val MEMORY_KEY = "memory_records"
    private const val MAX_RECORDS = 500

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
     * Save a memory.
     *
     * Sensitive information must not be passed here.
     */
    fun save(
        context: Context,
        category: Category,
        title: String,
        content: String,
        status: Status = Status.PENDING
    ): Boolean {
        if (title.isBlank() || content.isBlank()) {
            return false
        }

        if (containsSensitivePattern(title) ||
            containsSensitivePattern(content)
        ) {
            return false
        }

        return try {
            val memories = readArray(context)
            val now = System.currentTimeMillis()

            val record = JSONObject().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("category", category.name)
                put("title", title.trim())
                put("content", content.trim())
                put("status", status.name)
                put("createdAt", now)
                put("updatedAt", now)
            }

            memories.put(record)

            while (memories.length() > MAX_RECORDS) {
                memories.remove(0)
            }

            writeArray(context, memories)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Save an experience without storing the full command text.
     */
    fun saveExperience(
        context: Context,
        commandType: String,
        outcome: Status,
        lesson: String = ""
    ): Boolean {
        if (commandType.isBlank()) return false

        val safeLesson = lesson.trim()

        if (safeLesson.isNotEmpty() &&
            containsSensitivePattern(safeLesson)
        ) {
            return false
        }

        return save(
            context = context,
            category = Category.EXPERIENCE,
            title = commandType,
            content = if (safeLesson.isEmpty()) {
                "Outcome recorded: ${outcome.name}"
            } else {
                safeLesson
            },
            status = outcome
        )
    }

    /**
     * Record a correction.
     * Corrections remain unverified until explicitly reviewed.
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
     * Mark a memory as verified or rejected.
     */
    fun updateStatus(
        context: Context,
        memoryId: String,
        newStatus: Status
    ): Boolean {
        return try {
            val memories = readArray(context)
            var found = false

            for (index in 0 until memories.length()) {
                val item = memories.optJSONObject(index) ?: continue

                if (item.optString("id") == memoryId) {
                    item.put("status", newStatus.name)
                    item.put("updatedAt", System.currentTimeMillis())
                    found = true
                    break
                }
            }

            if (found) writeArray(context, memories)
            found
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Search memories by title or content.
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

        return readAll(context)
            .filter {
                normalize(it.title).contains(keyword) ||
                normalize(it.content).contains(keyword) ||
                normalize(it.category).contains(keyword)
            }
            .takeLast(limit)
            .reversed()
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

        return readAll(context)
            .filter { it.category == category.name }
            .takeLast(limit)
            .reversed()
    }

    /**
     * Only verified learning records are returned.
     * Pending guesses are never treated as confirmed knowledge.
     */
    fun getVerifiedLearning(
        context: Context,
        limit: Int = 50
    ): List<MemoryRecord> {
        if (limit <= 0) return emptyList()

        return readAll(context)
            .filter {
                it.category == Category.LEARNING.name &&
                it.status == Status.VERIFIED.name
            }
            .takeLast(limit)
            .reversed()
    }

    /**
     * Store a learning candidate.
     * It is NOT trusted until verified.
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

    fun getAll(
        context: Context,
        limit: Int = 100
    ): List<MemoryRecord> {
        if (limit <= 0) return emptyList()

        return readAll(context)
            .takeLast(limit)
            .reversed()
    }

    fun getCount(context: Context): Int {
        return readArray(context).length()
    }

    fun delete(
        context: Context,
        memoryId: String
    ): Boolean {
        return try {
            val memories = readArray(context)
            var found = false
            val updated = JSONArray()

            for (index in 0 until memories.length()) {
                val item = memories.optJSONObject(index) ?: continue

                if (item.optString("id") == memoryId) {
                    found = true
                } else {
                    updated.put(item)
                }
            }

            if (found) writeArray(context, updated)
            found
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Clear all local memories.
     * Call only after the user confirms this action.
     */
    fun clearAll(context: Context): Boolean {
        return try {
            writeArray(context, JSONArray())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readAll(context: Context): List<MemoryRecord> {
        val memories = readArray(context)
        val result = mutableListOf<MemoryRecord>()

        for (index in 0 until memories.length()) {
            val item = memories.optJSONObject(index) ?: continue

            result.add(
                MemoryRecord(
                    id = item.optString("id"),
                    category = item.optString("category"),
                    title = item.optString("title"),
                    content = item.optString("content"),
                    status = item.optString("status"),
                    createdAt = item.optLong("createdAt"),
                    updatedAt = item.optLong("updatedAt")
                )
            )
        }

        return result
    }

    private fun readArray(context: Context): JSONArray {
        return try {
            val saved = context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(MEMORY_KEY, null)

            if (saved.isNullOrBlank()) JSONArray()
            else JSONArray(saved)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun writeArray(
        context: Context,
        memories: JSONArray
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(MEMORY_KEY, memories.toString())
            .commit()
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
