package com.example.english1000

import java.text.Normalizer
import java.util.Locale

internal class RussianWordLookup(entries: Iterable<Label>, private val forms: Map<String, List<Int>>) {
    data class Label(
        val id: Int,
        val translation: String,
        val primary: String,
        val accepted: List<String>
    )

    private val exact = buildMap<String, MutableSet<Int>> {
        entries.forEach { label ->
            val variants = label.translation.split(';', ',', '/') + label.primary + label.accepted
            variants.forEach { variant ->
                val key = normalize(variant)
                if (key.isNotEmpty()) getOrPut(key) { linkedSetOf() }.add(label.id)
            }
        }
    }

    fun find(query: String): List<Int> {
        val key = normalize(query)
        return exact[key]?.toList() ?: forms[key].orEmpty()
    }

    companion object {
        private val stress = Regex("[\\u0300\\u0301]")
        private val separators = Regex("[^\\p{L}\\p{N}]+")

        private fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFKC)
                .lowercase(Locale.ROOT)
                .replace('ё', 'е')
                .replace(stress, "")
                .replace(separators, " ")
                .trim()

        fun readForms(lines: Sequence<String>): Map<String, List<Int>> =
            lines.associate { line ->
                val (form, ids) = line.split('\t', limit = 2)
                form to ids.split(',').map(String::toInt)
            }
    }
}
