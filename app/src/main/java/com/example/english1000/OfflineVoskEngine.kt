package com.example.english1000

import android.annotation.SuppressLint
import android.app.Activity
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * Offline-only voice recognizer. The EN/RU ZIPs are packaged inside the APK
 * by CI; there are no network calls and no Google/cloud speech fallback here.
 *
 * Only one acoustic model is loaded into native memory at a time.
 * A new session invalidates every callback from the previous recording.
 */
class OfflineVoskEngine(private val activity: Activity) {
    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    // The worker owns the microphone, recognizer and cached model for their entire lifetimes.
    private var model: Model? = null
    private var modelLanguage: String? = null

    companion object {
        private val extractionLock = Any()
        private val worker = Executors.newSingleThreadExecutor()
        private const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 320
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val LOG_TAG = "OfflineVosk"
        private const val MAX_EXPANDED_BYTES = 420L * 1024L * 1024L
        private const val MAX_FILES = 10_000
        private val modelRoots = mapOf(
            "en" to "vosk-model-small-en-us-0.15",
            "ru" to "vosk-model-small-ru-0.22"
        )

        fun packaged(activity: Activity, language: String): Boolean {
            if (!modelRoots.containsKey(language)) return false
            return try {
                activity.assets.open("speech/$language.zip").use { true }
            } catch (_: Exception) {
                false
            }
        }

        private fun ensureExtracted(activity: Activity, language: String): File {
            val expectedRoot = modelRoots[language]
                ?: throw IllegalArgumentException("Unsupported language")
            val storage = File(activity.filesDir, "vosk-models")
            val finalDir = File(storage, expectedRoot)
            synchronized(extractionLock) {
                if (File(finalDir, ".ready").isFile) return finalDir
                val temporary = File(storage, "$expectedRoot.partial")
                temporary.deleteRecursively()
                if (!temporary.mkdirs()) throw IllegalStateException("Cannot create speech model directory")
                var expanded = 0L
                var count = 0
                try {
                    ZipInputStream(activity.assets.open("speech/$language.zip")).use { input ->
                        while (true) {
                            val entry = input.nextEntry ?: break
                            val prefix = "$expectedRoot/"
                            require(entry.name.startsWith(prefix)) { "Invalid model path" }
                            val relative = entry.name.removePrefix(prefix)
                            if (relative.isNotEmpty()) {
                                val file = File(temporary, relative)
                                require(file.canonicalPath.startsWith(temporary.canonicalPath + File.separator)) {
                                    "Unsafe ASR model entry"
                                }
                                if (entry.isDirectory) {
                                    if (!file.mkdirs() && !file.isDirectory) {
                                        throw IllegalStateException("Could not prepare speech model directory")
                                    }
                                } else {
                                    require(++count <= MAX_FILES) { "Too many model files" }
                                    if (!file.parentFile!!.mkdirs() && !file.parentFile!!.isDirectory) {
                                        throw IllegalStateException("Cannot create model parent folder")
                                    }
                                    FileOutputStream(file).use { output ->
                                        val buffer = ByteArray(32 * 1024)
                                        while (true) {
                                            val bytes = input.read(buffer)
                                            if (bytes < 0) break
                                            expanded += bytes
                                            require(expanded <= MAX_EXPANDED_BYTES) { "Model too large" }
                                            output.write(buffer, 0, bytes)
                                        }
                                    }
                                }
                            }
                            input.closeEntry()
                        }
                    }
                    require(File(temporary, "am/final.mdl").isFile) { "Missing acoustic model" }
                    require(File(temporary, "conf/model.conf").isFile) { "Missing speech configuration" }
                    finalDir.deleteRecursively()
                    require(temporary.renameTo(finalDir)) { "Failed to activate speech model" }
                    File(finalDir, ".ready").writeText("1")
                    return finalDir
                } catch (e: Exception) {
                    temporary.deleteRecursively()
                    throw e
                }
            }
        }
    }

    /** Starts one utterance. Call from the main thread. */
    fun listen(
        language: String,
        timeoutMs: Long,
        includeAlternatives: Boolean = false,
        onStatus: (String) -> Unit,
        onResult: (VoiceRecognitionResult) -> Unit
    ) {
        stop()
        val request = generation
        onStatus("Подготавливаю локальное распознавание Vosk…")
        worker.execute {
            if (request != generation) return@execute
            val result = try {
                val readyModel = loadModel(language, request) ?: return@execute
                val utterance = if (includeAlternatives) {
                    ShortArray((timeoutMs * SAMPLE_RATE / 1_000L).toInt())
                } else null
                var recordedSamples = 0
                Log.i(LOG_TAG, "ASR_MODEL_READY language=$language")
                val transcript = Recognizer(readyModel, SAMPLE_RATE.toFloat()).use { recognizer ->
                    record(recognizer, request, timeoutMs, onSamples = { samples, count ->
                        if (utterance != null) {
                            val copied = minOf(count, utterance.size - recordedSamples)
                            samples.copyInto(utterance, recordedSamples, startIndex = 0, endIndex = copied)
                            recordedSamples += copied
                        }
                    }) {
                        ui.post {
                            if (request == generation) onStatus("🎤 Vosk: слушаю…")
                        }
                    }
                }
                if (transcript is VoiceRecognitionResult.Transcript && utterance != null) {
                    transcript.copy(otherHypotheses = alternatives(
                        readyModel, utterance, recordedSamples, transcript.text, request
                    ))
                } else transcript
            } catch (error: MicrophoneException) {
                Log.w(LOG_TAG, "Microphone unavailable", error)
                VoiceRecognitionResult.MicrophoneError
            } catch (error: Exception) {
                Log.w(LOG_TAG, "Local recognition failed", error)
                VoiceRecognitionResult.RecognitionError
            } catch (error: LinkageError) {
                Log.w(LOG_TAG, "Native recognition unavailable", error)
                VoiceRecognitionResult.RecognitionError
            }
            Log.i(LOG_TAG, "ASR_RESULT language=$language result=${result?.javaClass?.simpleName ?: "CANCELLED"}")
            if (result != null) {
                ui.post {
                    if (request == generation && !activity.isFinishing && !activity.isDestroyed) {
                        onResult(result)
                    }
                }
            }
        }
    }

    private fun loadModel(language: String, request: Int): Model? {
        if (modelLanguage == language && model != null) return model
        closeModel()
        val directory = ensureExtracted(activity, language)
        if (request != generation) return null
        val prepared = Model(directory.absolutePath)
        model = prepared
        modelLanguage = language
        return prepared.takeIf { request == generation }
    }

    @SuppressLint("MissingPermission")
    private fun record(
        recognizer: Recognizer,
        request: Int,
        timeoutMs: Long,
        onSamples: (ShortArray, Int) -> Unit,
        onRecording: () -> Unit
    ): VoiceRecognitionResult? {
        if (request != generation) return null
        val recorder = microphoneCall {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            check(minBuffer > 0) { "Audio format unavailable: $minBuffer" }
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, CHANNEL, ENCODING,
                maxOf(minBuffer, 2 * CHUNK_SAMPLES * Short.SIZE_BYTES)
            )
        }
        try {
            microphoneCall {
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" }
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "AudioRecord did not start"
                }
            }
            if (request != generation) return null
            onRecording()
            val audio = VoiceAudioActivity()
            val samples = ShortArray(CHUNK_SAMPLES)
            var lastSampleAt = SystemClock.elapsedRealtime()
            val deadline = lastSampleAt + timeoutMs
            while (request == generation && SystemClock.elapsedRealtime() < deadline) {
                val count = microphoneCall {
                    recorder.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING).also {
                        check(it >= 0) { "AudioRecord read error: $it" }
                    }
                }
                if (count == 0) {
                    if (SystemClock.elapsedRealtime() - lastSampleAt >= 1_000L) {
                        throw MicrophoneException("AudioRecord stopped supplying samples")
                    }
                    SystemClock.sleep(10)
                    continue
                }
                lastSampleAt = SystemClock.elapsedRealtime()
                audio.accept(samples, count)
                onSamples(samples, count)
                if (recognizer.acceptWaveForm(samples, count)) {
                    transcript(recognizer.result)?.let { return it }
                }
            }
            if (request != generation) return null
            return transcript(recognizer.finalResult)
                ?: if (audio.hasActivity) VoiceRecognitionResult.Unrecognized
                else VoiceRecognitionResult.Silence
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (error: RuntimeException) {
                Log.w(LOG_TAG, "AudioRecord stop failed", error)
            } finally {
                microphoneCall { recorder.release() }
            }
        }
    }

    private fun transcript(json: String): VoiceRecognitionResult.Transcript? {
        val text = JSONObject(json).optString("text").trim()
        return text.takeIf(String::isNotEmpty)?.let {
            VoiceRecognitionResult.Transcript(it, emptyList())
        }
    }

    /**
     * Get suggestions from the short in-memory recording after releasing the microphone.
     * Vosk can rank results differently in alternatives mode, so the primary transcript
     * always comes from the default recognizer.
     */
    private fun alternatives(
        readyModel: Model, samples: ShortArray, size: Int, primary: String, request: Int
    ): List<String> {
        if (request != generation) return emptyList()
        return try {
            Recognizer(readyModel, SAMPLE_RATE.toFloat()).use { recognizer ->
                recognizer.setMaxAlternatives(3)
                val chunk = ShortArray(CHUNK_SAMPLES)
                var offset = 0
                while (offset < size && request == generation) {
                    val count = minOf(chunk.size, size - offset)
                    samples.copyInto(chunk, startIndex = offset, endIndex = offset + count)
                    offset += count
                    if (recognizer.acceptWaveForm(chunk, count)) {
                        val accepted = alternativeTexts(recognizer.result)
                        if (accepted.isNotEmpty()) return accepted.filterNot { it == primary }.take(2)
                    }
                }
                if (request != generation) return emptyList()
                alternativeTexts(recognizer.finalResult).filterNot { it == primary }.take(2)
            }
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Alternative hypotheses unavailable", error)
            emptyList()
        }
    }

    private fun alternativeTexts(json: String): List<String> {
        val values = JSONObject(json).optJSONArray("alternatives") ?: return emptyList()
        return (0 until values.length()).mapNotNull { index ->
            values.optJSONObject(index)?.optString("text")?.trim()?.takeIf(String::isNotEmpty)
        }.distinct()
    }

    private inline fun <T> microphoneCall(action: () -> T): T = try {
        action()
    } catch (error: RuntimeException) {
        throw MicrophoneException("AudioRecord failed", error)
    }

    private class MicrophoneException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun stop() {
        generation++
    }

    fun release() {
        stop()
        worker.execute(::closeModel)
    }

    private fun closeModel() {
        val oldModel = model
        model = null
        modelLanguage = null
        oldModel?.close()
    }
}
