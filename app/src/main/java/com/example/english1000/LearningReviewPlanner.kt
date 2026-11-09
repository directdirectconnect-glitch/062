package com.example.english1000

/**
 * Deterministic, offline review scheduling. Every recommendation is based on a learner's
 * own unassisted manual checks: Android speech recognition errors are NOT evidence that
 * a word was forgotten. "Знаю" remains a self-rating, not a certified correct answer.
 */
object LearningReviewPlanner {
    private const val DAY_MS = 86_400_000L
    private val DAY_INTERVALS = longArrayOf(1, 3, 7, 14, 30)

    data class Attempt(
        val wordId: Int,
        val direction: Int,
        val occurredAtMs: Long,
        val responseMethod: String,
        val outcome: String,
        val assisted: Boolean
    )

    data class DueWord(
        val wordId: Int,
        val direction: Int,
        val attempts: Int,
        val mistakes: Int,
        val selfRatedStreak: Int,
        val dueAtMs: Long,
        val unresolvedMistake: Boolean
    )

    fun dueWords(events: List<Attempt>, nowMs: Long): List<DueWord> {
        val usable = events.asSequence().filter {
            it.wordId > 0 && it.direction in 0..1 &&
                !it.assisted && it.responseMethod == "manual" &&
                it.outcome in setOf("correct", "incorrect", "skipped")
        }.groupBy { it.wordId to it.direction }

        return usable.mapNotNull { (key, group) ->
            val chronological = group.sortedBy { it.occurredAtMs }
            val last = chronological.lastOrNull() ?: return@mapNotNull null
            val mistakes = chronological.count { it.outcome != "correct" }
            var streak = 0
            chronological.forEach { attempt ->
                if (attempt.outcome == "correct") streak++ else streak = 0
            }
            val unresolved = last.outcome != "correct"
            val waitDays = if (unresolved) 0L
                else DAY_INTERVALS[(streak - 1).coerceIn(0, DAY_INTERVALS.lastIndex)]
            val dueAt = last.occurredAtMs + waitDays * DAY_MS
            if (dueAt > nowMs) null else DueWord(
                key.first, key.second, chronological.size, mistakes, streak,
                dueAt, unresolved
            )
        }.sortedWith(
            compareByDescending<DueWord> { it.unresolvedMistake }
                .thenByDescending { it.mistakes }
                .thenBy { it.dueAtMs }
                .thenBy { it.wordId }
                .thenBy { it.direction }
        )
    }
}
