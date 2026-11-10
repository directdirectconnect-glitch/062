package com.example.english1000

import java.io.File

/**
 * Structural preflight only. A valid layout does not prove that Vosk can load
 * the model or that speech recognition works on a physical device.
 */
internal object OfflineModelLayout {
    fun hasRequiredFiles(directory: File): Boolean =
        directory.isDirectory &&
            File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/model.conf").isFile &&
            File(directory, "graph/HCLG.fst").isFile &&
            File(directory, "graph/words.txt").isFile
}
