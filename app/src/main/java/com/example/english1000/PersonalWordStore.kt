package com.example.english1000

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Private, device-local dictionary of words the learner personally requested.
 * Asking for a translation is exposure, never a verified correct answer.
 */
class PersonalWordStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "personal_words_v1.db", null, 1) {

    data class Entry(
        val wordId: Int,
        val english: String,
        val russian: String,
        val requests: Int,
        val lastAskedUtcMs: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE personal_words (" +
                "word_id INTEGER PRIMARY KEY, " +
                "english TEXT NOT NULL, " +
                "russian TEXT NOT NULL, " +
                "request_count INTEGER NOT NULL DEFAULT 0, " +
                "first_asked_utc_ms INTEGER NOT NULL, " +
                "last_asked_utc_ms INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE lookup_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "word_id INTEGER NOT NULL, " +
                "russian_query TEXT NOT NULL, " +
                "asked_utc_ms INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX idx_lookups_time ON lookup_events(asked_utc_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations must retain personal words and event history.
    }

    fun addRequestedWord(wordId: Int, english: String, russian: String, russianQuery: String) {
        require(wordId > 0 && english.isNotBlank() && russianQuery.isNotBlank())
        val now = System.currentTimeMillis()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = db.rawQuery(
                "SELECT request_count FROM personal_words WHERE word_id = ?",
                arrayOf(wordId.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else null
            }
            val values = ContentValues().apply {
                put("english", english)
                put("russian", russian)
                put("request_count", (current ?: 0) + 1)
                put("last_asked_utc_ms", now)
                if (current == null) put("first_asked_utc_ms", now)
            }
            if (current == null) {
                values.put("word_id", wordId)
                db.insertOrThrow("personal_words", null, values)
            } else {
                db.update("personal_words", values, "word_id = ?", arrayOf(wordId.toString()))
            }
            db.insertOrThrow("lookup_events", null, ContentValues().apply {
                put("word_id", wordId)
                put("russian_query", russianQuery.take(200))
                put("asked_utc_ms", now)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun recent(limit: Int = 100): List<Entry> {
        val items = mutableListOf<Entry>()
        readableDatabase.rawQuery(
            "SELECT word_id, english, russian, request_count, last_asked_utc_ms " +
                "FROM personal_words ORDER BY last_asked_utc_ms DESC LIMIT ?",
            arrayOf(limit.coerceIn(1, 1000).toString())
        ).use { c ->
            while (c.moveToNext()) {
                items.add(Entry(c.getInt(0), c.getString(1), c.getString(2), c.getInt(3),
                    c.getLong(4)))
            }
        }
        return items
    }
}
