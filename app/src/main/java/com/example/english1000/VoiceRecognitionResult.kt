package com.example.english1000

sealed interface VoiceRecognitionResult {
    data class Transcript(val text: String, val otherHypotheses: List<String>) : VoiceRecognitionResult {
        fun matchesFirstHypothesis(matches: (String) -> Boolean): Boolean = matches(text)
    }

    data object Silence : VoiceRecognitionResult
    data object Unrecognized : VoiceRecognitionResult
    data object MicrophoneError : VoiceRecognitionResult
    data object RecognitionError : VoiceRecognitionResult
}

/** Distinguishes sustained input from quiet samples. This measures audio level, not speech. */
internal class VoiceAudioActivity {
    private var consecutiveLoudFrames = 0
    private var frameSamples = 0
    private var sumOfSquares = 0L
    var hasActivity = false
        private set

    fun accept(samples: ShortArray, count: Int) {
        if (hasActivity) return
        for (index in 0 until count) {
            val sample = samples[index].toInt()
            sumOfSquares += sample.toLong() * sample
            frameSamples++
            if (frameSamples == FRAME_SAMPLES) {
                consecutiveLoudFrames = if (sumOfSquares >= MIN_FRAME_ENERGY) {
                    consecutiveLoudFrames + 1
                } else {
                    0
                }
                hasActivity = consecutiveLoudFrames >= MIN_LOUD_FRAMES
                frameSamples = 0
                sumOfSquares = 0L
                if (hasActivity) return
            }
        }
    }

    private companion object {
        const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
        const val MIN_LOUD_FRAMES = 3
        const val MIN_FRAME_ENERGY = 180L * 180L * FRAME_SAMPLES // about -45 dBFS RMS
    }
}
