package com.example.english1000

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.Executors

/**
 * English/Russian speech synthesizer using embedded Sherpa-ONNX VITS assets.
 * Both packages must include model.onnx, tokens.txt and espeak-ng-data.
 * The UI must never perform model initialization or inference on its main thread.
 */
internal class SherpaOfflineSpeech(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    @Volatile private var closed = false
    @Volatile var lastDiagnostic: String = "NOT_TESTED"
        private set

    // Preparation is intentionally lightweight. Never instantiate both VITS models
    // together: Android's native heap may not have room for both ONNX sessions.
    // Full asset SHA-256 validation is performed by CI before packaging.
    fun prepare(done: (Boolean, String) -> Unit) {
        worker.execute {
            if (closed) { done(false, "ENGINE_CLOSED"); return@execute }
            val ready = listOf("en", "ru").all { language ->
                try {
                    val base = "tts/$language"
                    context.assets.open("$base/model.onnx").use { it.read() >= 0 } &&
                        context.assets.open("$base/tokens.txt").use { it.read() >= 0 }
                } catch (e: Exception) {
                    lastDiagnostic = "MISSING_VOICE_ASSETS_$language: ${e.message}"
                    false
                }
            }
            lastDiagnostic = if (ready) "ASSETS_READY_LAZY" else "VOICE_ASSETS_MISSING"
            done(ready, lastDiagnostic)
        }
    }

    private fun create(language: String): OfflineTts {
        val base = "tts/$language"
        return OfflineTts(
            assetManager = context.assets,
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = "$base/model.onnx",
                        tokens = "$base/tokens.txt",
                        dataDir = "$base/espeak-ng-data"
                    ),
                    numThreads = 2,
                    debug = false
                )
            )
        )
    }

    /** Generate EN and RU sequentially; release each native ONNX session before the next. */
    fun speakPair(english: String, russian: String, finished: (Boolean) -> Unit) {
        val id = ++generation
        worker.execute {
            if (closed || id != generation) { finished(false); return@execute }
            try {
                for ((language, text) in listOf("en" to english, "ru" to russian)) {
                    if (id != generation) { finished(false); return@execute }
                    android.util.Log.i("EnglishWordsVoice", "VITS_CREATE_BEGIN language=$language")
                    val tts = create(language)
                    try {
                        android.util.Log.i("EnglishWordsVoice", "VITS_CREATE_OK language=$language")
                        if (!playWithRetry(tts, text, id, language)) {
                            finished(false)
                            return@execute
                        }
                    } finally {
                        tts.release()
                        android.util.Log.i("EnglishWordsVoice", "VITS_RELEASE language=$language")
                    }
                }
                lastDiagnostic = "EN_RU_PLAYBACK_COMPLETED"
                finished(true)
            } catch (e: Exception) {
                lastDiagnostic = "NATIVE_VOICE_ERROR: ${e.javaClass.simpleName}: ${e.message}"
                android.util.Log.e("EnglishWordsVoice", lastDiagnostic, e)
                finished(false)
            }
        }
    }

    private fun playWithRetry(tts: OfflineTts, text: String, id: Int, language: String): Boolean {
        if (id != generation) return false
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (manager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            lastDiagnostic = "MUTED_MEDIA_VOLUME: $language"
            return false // Never override user volume or silent preferences.
        }
        repeat(2) { attempt ->
            if (id != generation) return false
            try {
                if (play(tts, text, id)) return true
            } catch (e: Exception) {
                lastDiagnostic = "PLAYBACK_RETRY_${attempt + 1}: $language: ${e.message}"
            }
        }
        lastDiagnostic = "PLAYBACK_FAILED_AFTER_RETRY: $language"
        return false
    }

    private fun play(tts: OfflineTts, text: String, id: Int): Boolean {
        val audio = tts.generate(text)
        if (audio.samples.isEmpty() || id != generation) {
            lastDiagnostic = "EMPTY_OR_CANCELLED_AUDIO"
            return false
        }
        if (audio.samples.none { kotlin.math.abs(it) > 0.0005f }) {
            lastDiagnostic = "SILENT_SYNTHESIS"
            return false
        }
        val bytes = ByteArray(audio.samples.size * 2)
        audio.samples.forEachIndexed { index, sample ->
            val pcm = (sample.coerceIn(-1f, 1f) * 32767).toInt().toShort().toInt()
            bytes[index * 2] = (pcm and 255).toByte()
            bytes[index * 2 + 1] = ((pcm ushr 8) and 255).toByte()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes.size)
            .build()
        return try {
            if (id != generation) false else {
                val written = track.write(bytes, 0, bytes.size)
                if (written != bytes.size) {
                    lastDiagnostic = "AUDIOTRACK_SHORT_WRITE: $written/${bytes.size}"
                    return false
                }
                track.play()
                while (track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                    track.playbackHeadPosition < audio.samples.size && id == generation) {
                    Thread.sleep(20)
                }
                id == generation
            }
        } finally {
            track.stop()
            track.release()
        }
    }

    fun stop() { generation++ }
    fun close() {
        closed = true
        stop()
        worker.shutdown()
    }
}
