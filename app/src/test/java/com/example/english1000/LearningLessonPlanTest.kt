package com.example.english1000

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningLessonPlanTest {
    @Test fun pausesAtTheSameWordWhenReturningToALesson() {
        val plan = requireNotNull(LearningLessonPlan.create((1..105).toList(), 50))
        assertFalse(plan.move(-1))
        plan.rememberPosition(36)
        assertTrue(plan.move(1))
        assertEquals(51, plan.words.first())
        assertEquals(0, plan.position)
        plan.rememberPosition(10)
        assertTrue(plan.move(1))
        assertEquals(listOf(101, 102, 103, 104, 105), plan.words)
        assertFalse(plan.move(1))
        assertTrue(plan.move(-1))
        assertEquals(10, plan.position)
        assertTrue(plan.move(-1))
        assertEquals(36, plan.position)
    }

    @Test fun completedLessonsKeepTheirNumbersAndRemainAccessible() {
        val plan = requireNotNull(LearningLessonPlan.create((1..105).toList(), 50,
            (1..50).map(Int::toString).toSet()))
        assertEquals(1, plan.current)
        assertEquals(51, plan.words.first())
        assertTrue(plan.move(-1))
        assertEquals(1, plan.words.first())
    }

    @Test fun restoresTheNumberOfAnExistingRegularLesson() {
        val plan = LearningLessonPlan.restoreLegacy((51..100).toList(), 36, (1..106).toList(), 50)
        assertEquals(1, plan.current)
        assertEquals(36, plan.position)
        assertTrue(plan.move(-1))
        assertEquals(1, plan.words.first())
    }

    @Test fun keepsTheActiveLegacyBlockAndAppliesItsOriginalPosition() {
        val plan = LearningLessonPlan.restoreLegacy(listOf(8, 1, 5), 1, (1..10).toList(), 3)
        assertEquals(listOf(8, 1, 5), plan.words)
        assertEquals(1, plan.position)
        plan.move(1)
        assertEquals(listOf(2, 3, 4), plan.words)
    }
}
