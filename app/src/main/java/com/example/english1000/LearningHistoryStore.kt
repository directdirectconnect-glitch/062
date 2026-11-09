package com.example.english1000

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local-only, append-only log of completed checks. Listening/learning playback is NOT a
 * successful recall attempt. No recordings, transcripts, or network data are stored.
 */
class LearningHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "learning_history_v1.db", null, 1) {

    data class Totals(
        val attempts: Int, val correct: Int, val incorrect: Int, val skipped: Int,
        val assisted: Int
    )

    data class RepeatedDifficulty(
        val wordId: Int, val direction: Int, val attempts: Int, val mistakes: Int
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE learning_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at_ms INTEGER NOT NULL,
                session_id TEXT NOT NULL,
                word_id INTEGER NOT NULL,
                direction INTEGER NOT NULL,
                response_method TEXT NOT NULL,
                outcome TEXT NOT NULL,
                assisted INTEGER NOT NULL,
                scene_id TEXT NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            "CREATE INDEX history_word_direction ON learning_events(word_id, direction, occurred_at_ms)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the initial schema. Future versions must preserve event history.
        if (oldVersion > newVersion) error("Unsupported history database downgrade")
    }

    @Synchronized
    fun record(
        sessionId: String, wordId: Int, direction: Int, method: String,
        outcome: String, assisted: Boolean, sceneId: String
    ) {
        require(sessionId.isNotBlank() && wordId > 0 && direction in 0..1)
        require(method == "manual" || method == "asr")
        require(outcome in setOf("correct", "incorrect", "skipped"))
        val db = writableDatabase
        db.execSQL(
            """INSERT INTO learning_events
                (occurred_at_ms, session_id, word_id, direction, response_method,
                 outcome, assisted, scene_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            arrayOf<Any>(
                System.currentTimeMillis(), sessionId, wordId, direction,
                method, outcome, if (assisted) 1 else 0, sceneId
            )
        )
    }

    fun totals(): Totals {
        readableDatabase.rawQuery(
            """SELECT COUNT(*),
                COALESCE(SUM(CASE WHEN outcome = 'correct' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN outcome = 'incorrect' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(CASE WHEN outcome = 'skipped' THEN 1 ELSE 0 END), 0),
                COALESCE(SUM(assisted), 0)
               FROM learning_events""".trimIndent(), null
        ).use { cursor ->
            cursor.moveToFirst()
            return Totals(
                cursor.getInt(0), cursor.getInt(1), cursor.getInt(2),
                cursor.getInt(3), cursor.getInt(4)
            )
        }
    }

    /** Require at least three independent attempts; assisted responses are excluded. */
    fun repeatedDifficulties(limit: Int = 10): List<RepeatedDifficulty> {
        val result = mutableListOf<RepeatedDifficulty>()
        readableDatabase.rawQuery(
            """SELECT word_id, direction, COUNT(*) AS attempts,
                SUM(CASE WHEN outcome != 'correct' THEN 1 ELSE 0 END) AS mistakes
               FROM learning_events WHERE assisted = 0
               GROUP BY word_id, direction
               HAVING attempts >= 3 AND mistakes >= 2
               ORDER BY mistakes DESC, attempts DESC, word_id ASC
               LIMIT ?""".trimIndent(), arrayOf(limit.coerceIn(1, 100).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(RepeatedDifficulty(
                    cursor.getInt(0), cursor.getInt(1), cursor.getInt(2),
                    cursor.getInt(3)
                ))
            }
        }
        return result
    }
}
