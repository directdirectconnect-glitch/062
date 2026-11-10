package com.example.english1000

import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream
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
    private var generation = 0
    private var service: SpeechService? = null
    private var model: Model? = null

    companion object {
        private val extractionLock = Any()
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

    /**
     * Returns control immediately while the packaged model is unpacked and initialized.
     * Recording and callbacks are only activated for the current Activity session.
     */
    fun listen(
        language: String,
        onStatus: (String) -> Unit,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        stop()
        val requested = generation
        if (!packaged(activity, language)) {
            onError("Local $language speech model not packaged")
            return
        }
        onStatus("Подготавливаю офлайн-распознавание ($language)…")
        Thread({
            var loaded: Model? = null
            var opened: SpeechService? = null
            try {
                val directory = ensureExtracted(activity, language)
                if (requested != generation) return@Thread
                val readyModel = Model(directory.absolutePath)
                loaded = readyModel
                val recognizer = Recognizer(readyModel, 16000.0f)
                val readyService = SpeechService(recognizer, 16000.0f)
                opened = readyService
                ui.post {
                    if (requested != generation || activity.isDestroyed || activity.isFinishing) {
                        readyService.shutdown()
                        readyModel.close()
                        return@post
                    }
                    model = readyModel
                    service = readyService
                    onStatus("🎤 Офлайн Vosk: слушаю…")
                    var accepted = false
                    try {
                        readyService.startListening(object : RecognitionListener {
                            private fun accept(hypothesis: String) {
                                val value = try {
                                    JSONObject(hypothesis).optString("text").trim()
                                } catch (_: Exception) {
                                    ""
                                }
                                if (value.isNotBlank()) ui.post {
                                    if (requested == generation && !accepted) {
                                        accepted = true
                                        stop()
                                        onTranscript(value)
                                    }
                                }
                            }
                            override fun onResult(hypothesis: String) = accept(hypothesis)
                            override fun onFinalResult(hypothesis: String) = accept(hypothesis)
                            override fun onPartialResult(hypothesis: String) {}
                            override fun onError(exception: Exception) {
                                ui.post {
                                    if (requested == generation) {
                                        stop()
                                        onError(exception.message ?: "Speech recognition failed")
                                    }
                                }
                            }
                            override fun onTimeout() {
                                ui.post {
                                    if (requested == generation) {
                                        stop()
                                        onError("Speech recognition timeout")
                                    }
                                }
                            }
                        })
                    } catch (e: Exception) {
                        stop()
                        onError(e.message ?: "Cannot start local microphone")
                    }
                }
                loaded = null
                opened = null
            } catch (e: Exception) {
                ui.post {
                    if (requested == generation) {
                        stop()
                        onError(e.message ?: "Could not load offline model")
                    }
                }
            } finally {
                // Loaded objects are owned by UI after successful handoff.
                if (opened != null) opened.shutdown()
                if (loaded != null) loaded.close()
            }
        }, "EnglishWords-Vosk-$language").start()
    }

    fun stop() {
        generation++
        val running = service
        service = null
        try { running?.stop() } catch (_: Exception) {}
        try { running?.shutdown() } catch (_: Exception) {}
        try { model?.close() } catch (_: Exception) {}
        model = null
    }
}
