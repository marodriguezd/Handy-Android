package com.handy.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Owns the on-device SQLite schema for completed transcriptions. */
class HistoryDatabase(
    context: Context,
    databaseName: String = DATABASE_NAME,
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TEXT TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_DURATION_MS INTEGER NOT NULL DEFAULT 0,
                $COLUMN_MODEL_NAME TEXT,
                $COLUMN_SOURCE_TYPE TEXT NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX ${TABLE_NAME}_timestamp_index ON $TABLE_NAME ($COLUMN_TIMESTAMP DESC, $COLUMN_ID DESC)",
        )
        database.execSQL(
            "CREATE INDEX ${TABLE_NAME}_source_index ON $TABLE_NAME ($COLUMN_SOURCE_TYPE)",
        )
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_SOURCE_TYPE TEXT NOT NULL DEFAULT '${HistorySource.UNKNOWN}'")
        }
    }

    companion object {
        const val DATABASE_NAME = "handy_history.db"
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "transcription_history"
        const val COLUMN_ID = "id"
        const val COLUMN_TEXT = "text"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_DURATION_MS = "duration_ms"
        const val COLUMN_MODEL_NAME = "model_name"
        const val COLUMN_SOURCE_TYPE = "source_type"
    }
}
