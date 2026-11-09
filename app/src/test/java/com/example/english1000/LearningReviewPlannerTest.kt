package com.example.english1000

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningReviewPlannerTest {
    private val day = 86_400_000L
    private fun event(
        word: Int, time: Long, result: String,
        direction: Int = 0, method: String = "manual", assisted: Boolean = false
    ) = LearningReviewPlanner.Attempt(word, direction, time, method, result, assisted)

    @Test fun anUnresolvedErrorIsDueImmediately() {
        val due = LearningReviewPlanner.dueWords(
            listOf(event(8, 1000L, "incorrect")), 1000L
        )
        assertEquals(1, due.size)
        assertTrue(due[0].unresolvedMistake)
        assertEquals(1, due[0].mistakes)
        assertEquals(0, due[0].direction)
    }

    @Test fun oneSelfRatedSuccessSchedulesTomorrowNotImmediately() {
        val history = listOf(event(5, 100L, "correct"))
        assertTrue(LearningReviewPlanner.dueWords(history, day + 99L).isEmpty())
        val due = LearningReviewPlanner.dueWords(history, day + 100L)
        assertEquals(1, due.size)
        assertFalse(due[0].unresolvedMistake)
        assertEquals(1, due[0].selfRatedStreak)
    }

    @Test fun successfulStreakProgressesThroughOneThreeSevenFourteenThirtyDays() {
        val intervals = longArrayOf(1, 3, 7, 14, 30, 30)
        intervals.indices.forEach { index ->
            val history = (0..index).map { i ->
                event(11, i.toLong() * 10L, "correct")
            }
            val last = index.toLong() * 10L
            val expected = last + intervals[index] * day
            assertTrue(LearningReviewPlanner.dueWords(history, expected - 1).isEmpty())
            val due = LearningReviewPlanner.dueWords(history, expected)
            assertEquals(1, due.size)
            assertEquals(index + 1, due[0].selfRatedStreak)
        }
    }

    @Test fun errorResetsTheSuccessStreak() {
        val history = listOf(
            event(9, 0L, "correct"),
            event(9, 1L, "correct"),
            event(9, 2L, "incorrect"),
            event(9, 3L, "correct")
        )
        assertTrue(LearningReviewPlanner.dueWords(history, day + 2L).isEmpty())
        val due = LearningReviewPlanner.dueWords(history, day + 3L)
        assertEquals(1, due[0].selfRatedStreak)
        assertEquals(1, due[0].mistakes)
    }

    @Test fun assistedChecksAndAllSpeechRecognitionResultsAreExcluded() {
        val history = listOf(
            event(1, 1L, "incorrect", assisted = true),
            event(2, 2L, "incorrect", method = "asr"),
            event(3, 3L, "correct", method = "asr"),
            event(4, 4L, "skipped", assisted = true)
        )
        assertTrue(LearningReviewPlanner.dueWords(history, day * 365).isEmpty())
    }

    @Test fun translationDirectionsRemainIndependent() {
        val history = listOf(
            event(7, 100L, "correct", direction = 0),
            event(7, 110L, "incorrect", direction = 1)
        )
        val due = LearningReviewPlanner.dueWords(history, 111L)
        assertEquals(1, due.size)
        assertEquals(1, due[0].direction)
        assertEquals(7, due[0].wordId)
    }

    @Test fun currentUnresolvedMistakesArePrioritizedOverOldScheduledReviews() {
        val history = listOf(
            event(1, 0L, "correct"),
            event(2, day * 2, "incorrect"),
            event(3, day * 3, "skipped")
        )
        val due = LearningReviewPlanner.dueWords(history, day * 4)
        assertEquals(3, due.size)
        assertTrue(due[0].unresolvedMistake)
        assertTrue(due[1].unresolvedMistake)
        assertEquals(1, due[2].wordId)
    }

    @Test fun noAttemptsMeansNoRecommendation() {
        assertTrue(LearningReviewPlanner.dueWords(emptyList(), day).isEmpty())
    }
}
