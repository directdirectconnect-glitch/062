package com.example.english1000

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
    private var en: OfflineTts? = null
    private var ru: OfflineTts? = null
    @Volatile private var generation = 0

    fun prepare(done: (Boolean, String) -> Unit) {
        worker.execute {
            val audit = BundledVoiceAssets.inspect(context)
            if (!audit.ready) {
                done(false, audit.errors.joinToString("; "))
                return@execute
            }
            try {
                en = create("en")
                ru = create("ru")
                done(true, "Bundled EN/RU voices initialized")
            } catch (e: Exception) {
                en?.release(); ru?.release()
                en = null; ru = null
                done(false, "Sherpa-ONNX initialization failed: ${e.message}")
            }
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

    /** Speak English first and then Russian; callback runs on worker thread. */
    fun speakPair(english: String, russian: String, finished: (Boolean) -> Unit) {
        val id = ++generation
        worker.execute {
            val first = en
            val second = ru
            if (first == null || second == null) {
                finished(false)
                return@execute
            }
            try {
                if (!play(first, english, id)) { finished(false); return@execute }
                if (!play(second, russian, id)) { finished(false); return@execute }
                finished(true)
            } catch (e: Exception) {
                finished(false)
            }
        }
    }

    private fun play(tts: OfflineTts, text: String, id: Int): Boolean {
        val audio = tts.generate(text)
        if (audio.samples.isEmpty() || id != generation) return false
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
                track.write(bytes, 0, bytes.size)
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
        stop()
        worker.execute { en?.release(); ru?.release(); en = null; ru = null }
        worker.shutdown()
    }
}
