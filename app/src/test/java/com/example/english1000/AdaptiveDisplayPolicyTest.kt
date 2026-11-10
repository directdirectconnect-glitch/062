package com.example.english1000

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDisplayPolicyTest {
    @Test fun narrowCoverDisplayUsesSingleColumn() {
        assertFalse(AdaptiveDisplayPolicy.useTwoPanes(360f))
        assertFalse(AdaptiveDisplayPolicy.useTwoPanes(599f))
    }

    @Test fun unfoldedScreenUsesTwoPanesWhenRoomAllows() {
        assertTrue(AdaptiveDisplayPolicy.useTwoPanes(620f))
        assertTrue(AdaptiveDisplayPolicy.useTwoPanes(820f))
    }

    @Test fun resizedWindowFallsBackToCompactLayout() {
        val widths = listOf(760f, 480f, 760f)
        val layouts = widths.map(AdaptiveDisplayPolicy::useTwoPanes)
        assertTrue(layouts[0])
        assertFalse(layouts[1])
        assertTrue(layouts[2])
    }

    @Test fun nonFiniteAndInvalidWidthsAreCompact() {
        assertFalse(AdaptiveDisplayPolicy.useTwoPanes(Float.NaN))
        assertFalse(AdaptiveDisplayPolicy.useTwoPanes(Float.POSITIVE_INFINITY))
        assertFalse(AdaptiveDisplayPolicy.useTwoPanes(-100f))
    }
}
