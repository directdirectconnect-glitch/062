package com.example.english1000

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishedLessonSelectionTest {
    @Test fun defaultsToNextLessonAndCanBrowseEitherWay() {
        val selection = FinishedLessonSelection(103, finished = 0)
        assertEquals(1, selection.index)
        selection.move(-1)
        assertEquals(0, selection.index)
        assertFalse(selection.canMove(-1))
        repeat(5) { selection.move(1) }
        assertEquals(5, selection.index)
    }

    @Test fun supportsHighLessonNumbersAndRestoringTheChoice() {
        val selection = FinishedLessonSelection(103, finished = 48)
        assertEquals(49, selection.index)
        assertTrue(selection.canMove(1))
        val restored = FinishedLessonSelection(103, finished = 48, selected = 47)
        assertEquals(47, restored.index)
    }

    @Test fun staysOnTheLastAvailableLesson() {
        val selection = FinishedLessonSelection(5, finished = 4)
        assertEquals(4, selection.index)
        assertFalse(selection.canMove(1))
        selection.move(1)
        assertEquals(4, selection.index)
    }
}
