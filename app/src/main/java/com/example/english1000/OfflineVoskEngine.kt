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
    private var activeRecognizer: Recognizer? = null

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

    private var cachedLanguage: String? = null

    /**
     * Returns candidates with reasonably similar Vosk acoustic confidence.
     * Vosk 0.3.75 changes "text" to "alternatives" when N-best is enabled.
     * Never treat partial words as completed answers.
     */
    private fun hypotheses(payload: String): List<String> {
        return try {
            val json = JSONObject(payload)
            val alternatives = json.optJSONArray("alternatives")
            if (alternatives == null || alternatives.length() == 0) {
                listOf(json.optString("text").trim()).filter { it.isNotBlank() }
            } else {
                val top = alternatives.optJSONObject(0)?.optDouble("confidence", 0.0) ?: 0.0
                (0 until minOf(alternatives.length(), 3)).mapNotNull { index ->
                    val item = alternatives.optJSONObject(index) ?: return@mapNotNull null
                    val confidence = item.optDouble("confidence", 0.0)
                    val phrase = item.optString("text").trim()
                    if (phrase.isNotBlank() &&
                        (index == 0 || (top > 0.0 && confidence >= top * 0.82))) {
                        phrase
                    } else null
                }.distinct()
            }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Local RU/EN recognizer: cache the native acoustic model between short
     * answers (Fold and phones otherwise reload a full Vosk model per word).
     * The model stays available only while this Activity is alive. No internet.
     */
    fun listen(
        language: String,
        onStatus: (String) -> Unit,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit,
        onCandidates: ((List<String>) -> Unit)? = null
    ) {
        stop()
        val requested = generation
        if (!packaged(activity, language)) {
            onError("Local $language speech model not packaged")
            return
        }
        val reusable = if (cachedLanguage == language) model else null
        onStatus(if (reusable != null) "🎤 Готовлю микрофон ($language)…"
                 else "Подготавливаю офлайн-распознавание ($language)…")
        Thread({
            var newModel: Model? = null
            var opened: SpeechService? = null
            var ownedRecognizer: Recognizer? = null
            try {
                if (requested != generation) return@Thread
                val selectedModel = reusable ?: run {
                    val directory = ensureExtracted(activity, language)
                    if (requested != generation) return@Thread
                    Model(directory.absolutePath).also { newModel = it }
                }
                if (requested != generation) return@Thread
                val localRecognizer = Recognizer(selectedModel, 16000.0f)
                ownedRecognizer = localRecognizer
                localRecognizer.setMaxAlternatives(3)
                val localService = SpeechService(localRecognizer, 16000.0f)
                opened = localService
                ui.post {
                    if (requested != generation || activity.isDestroyed || activity.isFinishing) {
                        localService.shutdown()
                        localRecognizer.close()
                        if (newModel === selectedModel) selectedModel.close()
                        return@post
                    }
                    if (model !== selectedModel) {
                        val previousModel = model
                        model = selectedModel
                        cachedLanguage = language
                        if (previousModel !== selectedModel) previousModel?.close()
                    }
                    activeRecognizer = localRecognizer
                    service = localService
                    onStatus("🎤 Офлайн Vosk: слушаю…")
                    var accepted = false
                    val listener = object : RecognitionListener {
                        private fun accept(hypothesis: String) {
                            val candidates = hypotheses(hypothesis)
                            if (candidates.isEmpty()) return
                            if (requested == generation && !accepted) {
                                accepted = true
                                stop()
                                if (onCandidates == null) onTranscript(candidates.first())
                                else onCandidates(candidates)
                            }
                        }

                        override fun onResult(hypothesis: String) = accept(hypothesis)
                        override fun onFinalResult(hypothesis: String) = accept(hypothesis)
                        override fun onPartialResult(hypothesis: String) {}

                        override fun onError(exception: Exception) {
                            if (requested == generation && !accepted) {
                                accepted = true
                                stop()
                                onError(exception.message ?: "Speech recognition failed")
                            }
                        }

                        override fun onTimeout() {
                            if (requested == generation && !accepted) {
                                accepted = true
                                stop()
                                onError("Speech recognition timeout")
                            }
                        }
                    }
                    try {
                        // Time-bounded capture, with silence-triggered early result.
                        if (!localService.startListening(listener, 12000)) {
                            stop()
                            onError("Could not start local microphone")
                        }
                    } catch (e: Exception) {
                        stop()
                        onError(e.message ?: "Cannot start local microphone")
                    }
                }
                // Ownership transferred to the main-thread callback.
                newModel = null
                opened = null
                ownedRecognizer = null
            } catch (e: Exception) {
                ui.post {
                    if (requested == generation) {
                        stop()
                        onError(e.message ?: "Could not load offline model")
                    }
                }
            } finally {
                try { opened?.shutdown() } catch (_: Exception) {}
                try { ownedRecognizer?.close() } catch (_: Exception) {}
                try { newModel?.close() } catch (_: Exception) {}
            }
        }, "EnglishWords-Vosk-$language").start()
    }

    /** End an utterance, but keep the model for the next short word. */
    fun stop() {
        generation++
        val running = service
        service = null
        try { running?.cancel() } catch (_: Exception) {}
        try { running?.shutdown() } catch (_: Exception) {}
        try { activeRecognizer?.close() } catch (_: Exception) {}
        activeRecognizer = null
    }

    /** Release native model memory when the owning Activity is destroyed. */
    fun release() {
        stop()
        try { model?.close() } catch (_: Exception) {}
        model = null
        cachedLanguage = null
    }
}
