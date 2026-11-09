package com.example.english1000

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockRemediationPlannerTest {
    private fun answer(
        id: Int, direction: Int = 0, correct: Boolean, assisted: Boolean = false
    ) = BlockRemediationPlanner.Answer(id, direction, correct, assisted)

    @Test fun allIndependentCorrectAnswersFinishTheBlock() {
        var pending = emptyMap<Int, Int>()
        for (id in 1..50) {
            pending = BlockRemediationPlanner.afterAnswer(
                pending, answer(id, correct = true)
            )
        }
        assertTrue(BlockRemediationPlanner.isComplete(pending))
        assertTrue(BlockRemediationPlanner.nextRound((1..50).toList(), pending).isEmpty())
    }

    @Test fun missedAndSkippedWordsStayForRetryInTheirOriginalOrder() {
        var pending = emptyMap<Int, Int>()
        pending = BlockRemediationPlanner.afterAnswer(pending, answer(4, correct = false))
        pending = BlockRemediationPlanner.afterAnswer(pending, answer(2, correct = false))
        assertEquals(mapOf(4 to 0, 2 to 0), pending)
        assertEquals(listOf(2, 4), BlockRemediationPlanner.nextRound((1..5).toList(), pending))
    }

    @Test fun aCleanCorrectAnswerClearsTheMatchingError() {
        val missed = BlockRemediationPlanner.afterAnswer(emptyMap(), answer(8, correct = false))
        val corrected = BlockRemediationPlanner.afterAnswer(missed, answer(8, correct = true))
        assertTrue(BlockRemediationPlanner.isComplete(corrected))
    }

    @Test fun assistedCorrectAnswerDoesNotResolveError() {
        val missed = BlockRemediationPlanner.afterAnswer(emptyMap(), answer(8, correct = false))
        val hinted = BlockRemediationPlanner.afterAnswer(
            missed, answer(8, correct = true, assisted = true)
        )
        assertEquals(mapOf(8 to 0), hinted)
    }

    @Test fun firstAnswerWithHintsRequiresIndependentRetest() {
        val assisted = BlockRemediationPlanner.afterAnswer(
            emptyMap(), answer(8, correct = true, assisted = true)
        )
        assertEquals(mapOf(8 to 0), assisted)
        assertFalse(BlockRemediationPlanner.isComplete(assisted))
    }

    @Test fun correctionInOtherTranslationDirectionCannotClearError() {
        val pending = BlockRemediationPlanner.afterAnswer(
            emptyMap(), answer(8, direction = 1, correct = false)
        )
        val wrongDirection = BlockRemediationPlanner.afterAnswer(
            pending, answer(8, direction = 0, correct = true)
        )
        assertEquals(mapOf(8 to 1), wrongDirection)
        val corrected = BlockRemediationPlanner.afterAnswer(
            wrongDirection, answer(8, direction = 1, correct = true)
        )
        assertTrue(BlockRemediationPlanner.isComplete(corrected))
    }

    @Test fun repeatedErrorsRemainInTheNextRoundWithoutDuplicates() {
        var pending = mapOf(4 to 1)
        repeat(3) {
            pending = BlockRemediationPlanner.afterAnswer(
                pending, answer(4, direction = 1, correct = false)
            )
        }
        assertEquals(listOf(4), BlockRemediationPlanner.nextRound(listOf(1, 4, 4), pending))
    }

    @Test fun independentCorrectRemovesOnlyThatWord() {
        val pending = mapOf(3 to 0, 7 to 1, 10 to 0)
        val remaining = BlockRemediationPlanner.afterAnswer(
            pending, answer(7, direction = 1, correct = true)
        )
        assertEquals(mapOf(3 to 0, 10 to 0), remaining)
        assertEquals(listOf(3, 10), BlockRemediationPlanner.nextRound(listOf(10, 7, 3), remaining).reversed())
    }
}
