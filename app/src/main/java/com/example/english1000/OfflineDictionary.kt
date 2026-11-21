package com.example.english1000

import android.content.res.AssetManager
import java.io.FileNotFoundException
import org.json.JSONObject

internal data class OfflineDictionarySettings(
    val defaultBlockSize: Int = 50,
    val defaultPronunciation: String = "original",
) {
    companion object {
        val blockSizes = listOf(10, 20, 30, 50)
        val pronunciationModes = listOf("ipa_us", "ipa_uk", "original")

        fun load(assets: AssetManager): OfflineDictionarySettings {
            val text = try {
                assets.open("dictionary_settings.json").bufferedReader().use { it.readText() }
            } catch (_: FileNotFoundException) {
                return OfflineDictionarySettings()
            }
            val json = JSONObject(text)
            require(json.getInt("schemaVersion") == 1) { "Unsupported vocabulary schema" }
            val blockSize = json.getInt("defaultBlockSize")
            val pronunciation = json.getString("defaultPronunciation")
            require(blockSize in blockSizes && pronunciation in pronunciationModes)
            return OfflineDictionarySettings(blockSize, pronunciation)
        }
    }
}

internal fun JSONObject.dictionaryList(field: String): List<String> {
    val items = optJSONArray(field) ?: return emptyList()
    return List(items.length()) { i -> items.getString(i) }
}
