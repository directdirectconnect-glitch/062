package com.example.english1000

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineModelLayoutTest {
    @Test fun missingModelIsRejected() {
        val root = Files.createTempDirectory("vosk-missing").toFile()
        try { assertFalse(OfflineModelLayout.hasRequiredFiles(File(root, "missing"))) }
        finally { root.deleteRecursively() }
    }

    @Test fun emptyDirectorySkeletonIsRejected() {
        val root = Files.createTempDirectory("vosk-empty").toFile()
        try {
            listOf("am", "conf", "graph").forEach { File(root, it).mkdirs() }
            assertFalse(OfflineModelLayout.hasRequiredFiles(root))
        } finally { root.deleteRecursively() }
    }

    @Test fun completeRequiredFileLayoutIsAccepted() {
        val root = Files.createTempDirectory("vosk-layout").toFile()
        try {
            listOf("am/final.mdl", "conf/model.conf", "graph/HCLG.fst", "graph/words.txt").forEach {
                val file = File(root, it)
                file.parentFile!!.mkdirs()
                file.writeText("fixture")
            }
            assertTrue(OfflineModelLayout.hasRequiredFiles(root))
            File(root, "graph/words.txt").delete()
            assertFalse(OfflineModelLayout.hasRequiredFiles(root))
        } finally { root.deleteRecursively() }
    }
}
