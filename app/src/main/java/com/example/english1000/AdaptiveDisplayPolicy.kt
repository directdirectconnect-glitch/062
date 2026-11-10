package com.example.english1000

/**
 * Breakpoint based on the content window, not the physical display.
 *
 * Foldable devices can be resized by multi-window and split-screen. A 620 dp
 * content region leaves room for a useful navigation pane and a reading pane;
 * the narrow layout is kept on the cover display and compact windows.
 */
internal object AdaptiveDisplayPolicy {
    const val TWO_PANE_MIN_WIDTH_DP = 620f

    fun useTwoPanes(contentWidthDp: Float): Boolean =
        contentWidthDp.isFinite() && contentWidthDp >= TWO_PANE_MIN_WIDTH_DP
}
