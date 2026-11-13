package com.example.english1000

import java.io.File

/** Structural preflight for a local Vosk model, not an acoustic accuracy test. */
internal object OfflineModelLayout {
    private val required = listOf(
        "am/final.mdl", "conf/model.conf", "graph/HCLG.fst", "graph/words.txt"
    )

    fun hasRequiredFiles(directory: File): Boolean =
        directory.isDirectory && required.all { relative ->
            File(directory, relative).let { it.isFile && it.length() > 0L }
        }
}
