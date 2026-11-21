package com.example.english1000

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognitionResultTest {
    @Test fun secondHypothesisDoesNotAutomaticallyPass() {
        val result = VoiceRecognitionResult.Transcript("camp", listOf("cap"))
        assertFalse(result.matchesFirstHypothesis { VoiceAnswerMatcher.matches(it, "cap", 1) })
        assertTrue(result.matchesFirstHypothesis { VoiceAnswerMatcher.matches(it, "camp", 1) })
    }

    @Test fun russianHomophoneInAlternativesDoesNotAutomaticallyPass() {
        val result = VoiceRecognitionResult.Transcript("том", listOf("дом"))
        assertFalse(result.matchesFirstHypothesis { VoiceAnswerMatcher.matches(it, "дом", 0) })
    }

    @Test fun silenceAndAnIsolatedClickDoNotCountAsActivity() {
        val activity = VoiceAudioActivity()
        activity.accept(ShortArray(640), 640)
        val click = ShortArray(960)
        click[50] = Short.MAX_VALUE
        activity.accept(click, click.size)
        assertFalse(activity.hasActivity)
    }

    @Test fun audioActivityCanSpanMultipleReadsAndIgnoresUnusedBufferSamples() {
        val activity = VoiceAudioActivity()
        val buffer = ShortArray(320) { 400 }
        repeat(5) { activity.accept(buffer, 160) }
        assertFalse(activity.hasActivity)
        activity.accept(buffer, 160)
        assertTrue(activity.hasActivity)
    }
}
