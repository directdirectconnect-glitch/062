package com.example.english1000

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAnswerMatcherTest {
    @Test fun englishExactWordCredited() {
        assertTrue(VoiceAnswerMatcher.matches("run", "run", 1))
    }

    @Test fun englishToInfinitiveCredited() {
        assertTrue(VoiceAnswerMatcher.matches("to run", "run", 1))
    }

    @Test fun englishAccentAndPunctuationNormalized() {
        assertTrue(VoiceAnswerMatcher.matches("CAFÉ!", "cafe", 1))
    }

    @Test fun russianYoAndYeEquivalent() {
        assertTrue(VoiceAnswerMatcher.matches("Ещё.", "еще", 0))
    }

    @Test fun russianToBePrefixOptional() {
        assertTrue(VoiceAnswerMatcher.matches("готовым", "быть готовым", 0))
    }

    @Test fun multipleTranslationVariants() {
        assertTrue(VoiceAnswerMatcher.matches("казаться", "казаться; представляться", 0))
    }

    @Test fun wrongEnglishWordNotCredited() {
        assertFalse(VoiceAnswerMatcher.matches("walk", "run", 1))
    }

    @Test fun partialPhraseNotAccidentallyCredited() {
        assertFalse(VoiceAnswerMatcher.matches("I run", "run", 1))
    }

    @Test fun emptyTranscriptNotCredited() {
        assertFalse(VoiceAnswerMatcher.matches("...", "run", 1))
    }
}
