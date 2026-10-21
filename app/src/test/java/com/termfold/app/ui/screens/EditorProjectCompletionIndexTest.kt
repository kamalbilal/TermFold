package com.termfold.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorProjectCompletionIndexTest {
    @Test
    fun collectsMemberUsagesAcrossFiles() {
        val root = java.nio.file.Files.createTempDirectory("tf-proj").toFile()
        val a = File(root, "a.kt")
        val b = File(root, "b.kt")
        a.writeText("fun demo() { params.showTyping(); params.other() }")
        b.writeText("val x = params.showTyping")
        val json = EditorProjectCompletionIndex.jsonFor(root, maxFiles = 10)
        assertTrue(json.contains("showTyping"))
        assertTrue(json.contains("other"))
        assertTrue(json.contains("params"))
        a.delete()
        b.delete()
    }
}
