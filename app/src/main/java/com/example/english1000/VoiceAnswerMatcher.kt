package com.example.english1000

import java.text.Normalizer
import java.util.Locale

/**
 * Pure offline matching of an ASR transcript to a vocabulary answer.
 *
 * Keep recognition (Vosk) separate from answer grading, so vocabulary-answer
 * acceptance can be covered by JVM tests without network or microphone access.
 *
 * 0 = answer in Russian; 1 = answer in English.
 */
object VoiceAnswerMatcher {
    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).replace('ё', 'е'), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun matches(transcript: String, expected: String, questionDirection: Int): Boolean {
        val heard = normalize(transcript)
        if (heard.isBlank()) return false
        val variants = expected.split(',', ';', '/')
            .map { normalize(it) }
            .filter { it.isNotBlank() }
        return variants.any { answer ->
            heard == answer ||
            heard == "to $answer" ||
            (questionDirection == 0 && heard == answer.removePrefix("быть "))
        }
    }
}
