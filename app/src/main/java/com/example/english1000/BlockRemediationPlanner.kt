package com.example.english1000

/**
 * Current-block error recovery, independent of Android, audio, and history settings.
 *
 * A word is not considered resolved when the learner saw its answer or examples before
 * grading. A clean, unassisted correct answer in the same translation direction resolves it.
 * This is a block-completion rule, not a scientific measure of long-term mastery.
 */
object BlockRemediationPlanner {
    data class Answer(
        val wordId: Int,
        val direction: Int,
        val correct: Boolean,
        val assisted: Boolean
    )

    fun afterAnswer(pending: Map<Int, Int>, answer: Answer): Map<Int, Int> {
        require(answer.wordId > 0 && answer.direction in 0..1)
        val updated = pending.toMutableMap()
        if (answer.correct && !answer.assisted) {
            // Only the same direction may resolve an existing error.
            if (updated[answer.wordId] == answer.direction) updated.remove(answer.wordId)
        } else {
            updated[answer.wordId] = answer.direction
        }
        return updated
    }

    /** Retry each unresolved word once per round, in its initial block order. */
    fun nextRound(originalWordIds: List<Int>, pending: Map<Int, Int>): List<Int> =
        originalWordIds.distinct().filter { pending[it] in 0..1 }

    fun isComplete(pending: Map<Int, Int>): Boolean = pending.isEmpty()
}
