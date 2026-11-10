package com.example.english1000

import android.content.Context
import java.io.File

/**
 * Explicit readiness gate for bundled, offline speech models.
 * A platform SpeechRecognizer with EXTRA_PREFER_OFFLINE is NOT equivalent
 * to an app-bundled recognizer and must never satisfy this check.
 */
internal object OfflineAsrReadiness {
    const val EN_MODEL = "vosk-model-small-en-us"
    const val RU_MODEL = "vosk-model-small-ru"

    data class Status(val englishReady: Boolean, val russianReady: Boolean) {
        val fullyOffline: Boolean get() = englishReady && russianReady
    }

    fun inspect(context: Context): Status {
        val root = File(context.filesDir, "asr-models")
        return Status(modelReady(File(root, EN_MODEL)), modelReady(File(root, RU_MODEL)))
    }

    private fun modelReady(directory: File): Boolean =
        OfflineModelLayout.hasRequiredFiles(directory)
