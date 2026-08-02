package com.handy.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A completed local transcription shown in the history screen. */
data class HistoryEntry(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val durationMs: Long,
    val modelName: String?,
    val sourceType: String,
    val audioFilePath: String? = null,
)

object HistorySource {
    const val FLOATING_BUTTON = "floating_button"
    const val INPUT_METHOD = "input_method"
    const val VOICE_RECOGNITION = "voice_recognition"
    const val VOICE_INPUT = "voice_input"
    const val AUDIO_FILE = "audio_file"
    const val LIVE_SUBTITLE = "live_subtitle"
    const val UNKNOWN = "unknown"

    @androidx.annotation.StringRes
    fun labelRes(sourceType: String): Int = when (sourceType) {
        FLOATING_BUTTON -> R.string.history_source_floating_button
        INPUT_METHOD -> R.string.history_source_input_method
        VOICE_RECOGNITION -> R.string.history_source_voice_recognition
        VOICE_INPUT -> R.string.history_source_voice_input
        AUDIO_FILE -> R.string.history_source_audio_file
        LIVE_SUBTITLE -> R.string.live_subtitle_title
        else -> R.string.history_source_other
    }
}

/** Synchronous SQLite access; callers performing UI work should use the suspend helpers. */
class HistoryRepository(
    context: Context,
    private val database: HistoryDatabase = HistoryDatabase(context.applicationContext),
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : AutoCloseable {
    init { require(maxEntries > 0) { "History capacity must be positive" } }

    @Synchronized
    fun addEntry(
        text: String,
        timestamp: Long = System.currentTimeMillis(),
        durationMs: Long = 0L,
        modelName: String? = null,
        sourceType: String = HistorySource.UNKNOWN,
        audioFilePath: String? = null,
    ): Long {
        val normalizedText = text.trim()
        require(normalizedText.isNotEmpty()) { "History entries cannot be blank" }
        require(timestamp >= 0L) { "History timestamp cannot be negative" }
        require(durationMs >= 0L) { "History duration cannot be negative" }
        val values = ContentValues().apply {
            put(HistoryDatabase.COLUMN_TEXT, normalizedText)
            put(HistoryDatabase.COLUMN_TIMESTAMP, timestamp)
            put(HistoryDatabase.COLUMN_DURATION_MS, durationMs)
            put(HistoryDatabase.COLUMN_MODEL_NAME, modelName?.trim()?.ifBlank { null })
            put(HistoryDatabase.COLUMN_SOURCE_TYPE, sourceType.trim().ifBlank { HistorySource.UNKNOWN })
            put(HistoryDatabase.COLUMN_AUDIO_FILE_PATH, audioFilePath?.trim()?.ifBlank { null })
        }
        val writableDatabase = database.writableDatabase
        writableDatabase.beginTransaction()
        return try {
            val id = writableDatabase.insertOrThrow(HistoryDatabase.TABLE_NAME, null, values)
            pruneToCapacity(writableDatabase)
            writableDatabase.setTransactionSuccessful()
            id
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun listEntries(query: String = "", sourceType: String? = null): List<HistoryEntry> {
        val normalizedQuery = query.trim()
        val clauses = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        if (normalizedQuery.isNotEmpty()) {
            clauses += "$COLUMN_TEXT LIKE ? ESCAPE '\\'"
            arguments += "%${escapeLike(normalizedQuery)}%"
        }
        if (!sourceType.isNullOrBlank()) {
            clauses += "$COLUMN_SOURCE_TYPE = ?"
            arguments += sourceType
        }
        return database.readableDatabase.query(
            HistoryDatabase.TABLE_NAME,
            COLUMNS,
            clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            arguments.toTypedArray(),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            buildList(cursor.count) { while (cursor.moveToNext()) add(cursor.toHistoryEntry()) }
        }
    }

    @Synchronized
    fun deleteEntry(id: Long): Int {
        val path = listEntries().firstOrNull { it.id == id }?.audioFilePath
        val deleted = database.writableDatabase.delete(
            HistoryDatabase.TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()),
        )
        if (deleted > 0 && path != null && listEntries().none { it.audioFilePath == path }) {
            deleteAudioFile(path)
        }
        return deleted
    }

    @Synchronized
    fun clear(): Int {
        val paths = listEntries().mapNotNull { it.audioFilePath }
        val deleted = database.writableDatabase.delete(HistoryDatabase.TABLE_NAME, null, null)
        if (deleted > 0) paths.forEach(::deleteAudioFile)
        return deleted
    }

    @Synchronized
    fun count(): Int = database.readableDatabase.query(
        HistoryDatabase.TABLE_NAME, arrayOf("COUNT(*)"), null, null, null, null, null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    @Synchronized fun closeDatabase() = database.close()
    override fun close() = closeDatabase()

    private fun pruneToCapacity(writableDatabase: SQLiteDatabase) {
        val retainedIds = writableDatabase.query(
            HistoryDatabase.TABLE_NAME,
            arrayOf(COLUMN_ID),
            null, null, null, null,
            "$COLUMN_TIMESTAMP DESC, $COLUMN_ID DESC",
            maxEntries.toString(),
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }
        val stalePaths = writableDatabase.query(
            HistoryDatabase.TABLE_NAME,
            arrayOf(COLUMN_ID, HistoryDatabase.COLUMN_AUDIO_FILE_PATH),
            null, null, null, null, null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getLong(0) !in retainedIds && !cursor.isNull(1)) add(cursor.getString(1))
                }
            }
        }
        writableDatabase.delete(
            HistoryDatabase.TABLE_NAME,
            "$COLUMN_ID NOT IN (SELECT $COLUMN_ID FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC, $COLUMN_ID DESC LIMIT ?)",
            arrayOf(maxEntries.toString()),
        )
        stalePaths.distinct().filter { path ->
            listEntriesFromDatabase(writableDatabase).none { it == path }
        }.forEach(::deleteAudioFile)
    }

    private fun listEntriesFromDatabase(database: SQLiteDatabase): List<String> = database.query(
        HistoryDatabase.TABLE_NAME,
        arrayOf(HistoryDatabase.COLUMN_AUDIO_FILE_PATH),
        "${HistoryDatabase.COLUMN_AUDIO_FILE_PATH} IS NOT NULL",
        null, null, null, null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) if (!cursor.isNull(0)) add(cursor.getString(0))
        }
    }

    private fun deleteAudioFile(path: String) {
        runCatching { java.io.File(path).delete() }
    }

    private fun escapeLike(value: String): String = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun android.database.Cursor.toHistoryEntry(): HistoryEntry = HistoryEntry(
        id = getLong(getColumnIndexOrThrow(HistoryDatabase.COLUMN_ID)),
        text = getString(getColumnIndexOrThrow(HistoryDatabase.COLUMN_TEXT)),
        timestamp = getLong(getColumnIndexOrThrow(HistoryDatabase.COLUMN_TIMESTAMP)),
        durationMs = getLong(getColumnIndexOrThrow(HistoryDatabase.COLUMN_DURATION_MS)),
        modelName = getStringOrNull(HistoryDatabase.COLUMN_MODEL_NAME),
        sourceType = getString(getColumnIndexOrThrow(HistoryDatabase.COLUMN_SOURCE_TYPE)),
        audioFilePath = getStringOrNull(HistoryDatabase.COLUMN_AUDIO_FILE_PATH),
    )

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 500
        private const val COLUMN_ID = HistoryDatabase.COLUMN_ID
        private const val COLUMN_TEXT = HistoryDatabase.COLUMN_TEXT
        private const val COLUMN_TIMESTAMP = HistoryDatabase.COLUMN_TIMESTAMP
        private const val COLUMN_SOURCE_TYPE = HistoryDatabase.COLUMN_SOURCE_TYPE
        private const val TABLE_NAME = HistoryDatabase.TABLE_NAME
        private val COLUMNS = arrayOf(
            HistoryDatabase.COLUMN_ID,
            HistoryDatabase.COLUMN_TEXT,
            HistoryDatabase.COLUMN_TIMESTAMP,
            HistoryDatabase.COLUMN_DURATION_MS,
            HistoryDatabase.COLUMN_MODEL_NAME,
            HistoryDatabase.COLUMN_SOURCE_TYPE,
            HistoryDatabase.COLUMN_AUDIO_FILE_PATH,
        )

        /** Records an entry without allowing history failure to break transcription output. */
        suspend fun record(
            context: Context,
            text: String,
            sourceType: String,
            durationMs: Long = 0L,
            audioFilePath: String? = null,
        ) {
            if (text.isBlank()) return
            try {
                withContext(Dispatchers.IO) {
                    HistoryRepository(context).use { repository ->
                        repository.addEntry(
                            text = text,
                            durationMs = durationMs,
                            modelName = SettingsManager.activeModelName(context),
                            sourceType = sourceType,
                            audioFilePath = audioFilePath,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // History is best-effort and must never prevent transcription delivery.
            }
        }
    }
}
