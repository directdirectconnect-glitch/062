package com.example.english1000

internal class LearningLessonPlan(
    val lessons: List<List<Int>>,
    val positions: IntArray = IntArray(lessons.size),
    var current: Int = 0
) {
    init {
        require(lessons.isNotEmpty() && lessons.all { it.isNotEmpty() })
        require(positions.size == lessons.size && current in lessons.indices)
        require(lessons.indices.all { positions[it] in lessons[it].indices })
    }

    val words: List<Int> get() = lessons[current]
    val position: Int get() = positions[current]

    fun canMove(offset: Int): Boolean = current + offset in lessons.indices

    fun move(offset: Int): Boolean {
        if (!canMove(offset)) return false
        current += offset
        return true
    }

    fun rememberPosition(index: Int) {
        positions[current] = index.coerceIn(words.indices)
    }

    companion object {
        fun create(
            candidates: List<Int>,
            blockSize: Int,
            completed: Set<String> = emptySet()
        ): LearningLessonPlan? {
            val lessons = candidates.chunked(blockSize)
            if (lessons.isEmpty()) return null
            val firstIncomplete = lessons.indexOfFirst { ids -> ids.any { it.toString() !in completed } }
            return LearningLessonPlan(lessons, current = firstIncomplete.coerceAtLeast(0))
        }

        fun restoreLegacy(
            activeWords: List<Int>,
            activePosition: Int,
            candidates: List<Int>,
            blockSize: Int
        ): LearningLessonPlan {
            val numbered = candidates.chunked(blockSize)
            val current = numbered.indexOf(activeWords)
            if (current >= 0) {
                return LearningLessonPlan(numbered, current = current)
                    .apply { rememberPosition(activePosition) }
            }
            val activeIds = activeWords.toHashSet()
            val lessons = listOf(activeWords) + candidates.filterNot { it in activeIds }.chunked(blockSize)
            return LearningLessonPlan(lessons).apply { rememberPosition(activePosition) }
        }
    }
}

/** Browsing this selector does not change the open lesson until the user confirms. */
internal class FinishedLessonSelection(
    private val lessonCount: Int,
    finished: Int,
    selected: Int? = null
) {
    init {
        require(finished in 0 until lessonCount)
    }

    var index = selected?.coerceIn(0, lessonCount - 1)
        ?: (finished + 1).coerceAtMost(lessonCount - 1)
        private set

    fun canMove(offset: Int): Boolean = index + offset in 0 until lessonCount

    fun move(offset: Int) {
        if (canMove(offset)) index += offset
    }
}
