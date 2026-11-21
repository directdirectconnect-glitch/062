package com.example.english1000

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Integrity gate for self-contained offline speech packages.
 *
 * This does not synthesize speech. The release must not claim bundled voices
 * until both verified packages are connected to the audio renderer.
 */
internal object BundledVoiceAssets {
    data class VoicePackage(val locale: String, val modelId: String)
    data class Result(val english: VoicePackage?, val russian: VoicePackage?, val errors: List<String>) {
        val ready: Boolean get() = english != null && russian != null && errors.isEmpty()
    }

    fun inspect(context: Context): Result {
        val errors = mutableListOf<String>()
        val en = inspectOne(context, "en", errors)
        val ru = inspectOne(context, "ru", errors)
        return Result(en, ru, errors)
    }

    private fun inspectOne(context: Context, language: String, errors: MutableList<String>): VoicePackage? {
        val base = "tts/$language"
        val manifest = try {
            JSONObject(context.assets.open("$base/manifest.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            errors += "$language: missing or invalid bundled manifest"
            return null
        }
        val locale = manifest.optString("locale")
        val modelId = manifest.optString("model_id")
        val files = manifest.optJSONObject("files")
        if (locale.substringBefore('-') != language || modelId.isBlank() || files == null || files.length() == 0) {
            errors += "$language: incomplete voice manifest"
            return null
        }
        val names = files.keys()
        while (names.hasNext()) {
            val name = names.next()
            if (name.startsWith("/") || name.split('/').any { it == ".." || it == "." } || name.isBlank()) {
                errors += "$language: unsafe asset path"
                continue
            }
            val expected = files.optString(name)
            if (!Regex("[a-fA-F0-9]{64}").matches(expected)) {
                errors += "$language: invalid SHA-256 for $name"
                continue
            }
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                context.assets.open("$base/$name").use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) errors += "$language: checksum mismatch $name"
            } catch (e: Exception) {
                errors += "$language: missing asset $name"
            }
        }
        return if (errors.none { it.startsWith("$language:") }) VoicePackage(locale, modelId) else null
    }
}
