package com.example.english1000

import android.content.Context
import org.json.JSONObject

/**
 * Offline-only visual vocabulary index. Images must be packaged in app assets.
 * Stable word IDs keep image mappings independent from translation changes.
 */
internal class VisualVocabularyCatalog(private val context: Context) {
    data class Entry(val wordId: Int, val assetPath: String, val altEn: String, val altRu: String)

    private val entries: Map<Int, Entry> by lazy {
        val raw = runCatching {
            context.assets.open("visual_vocabulary.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@lazy emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return@lazy emptyMap()
        val items = root.optJSONArray("images") ?: return@lazy emptyMap()
        buildMap {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optInt("wordId", -1)
                val file = item.optString("asset", "")
                if (id < 0 || !file.matches(Regex("images/[a-zA-Z0-9_/-]+\\.(png|webp|jpg)"))) continue
                if (".." in file) continue
                put(id, Entry(id, file, item.optString("altEn"), item.optString("altRu")))
            }
        }
    }

    fun entryFor(wordId: Int): Entry? = entries[wordId]

    fun loadImage(wordId: Int): android.graphics.Bitmap? {
        val entry = entryFor(wordId) ?: return null
        return runCatching {
            context.assets.open(entry.assetPath).use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }
}
