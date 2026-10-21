package com.termfold.app.gitworkspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TermfoldGitignoreTest {

    @Test
    fun createsGitignoreWhenMissing() {
        val dir = File.createTempFile("termfold-gitignore-", null).parentFile!!
        val sub = File(dir, "proj-${System.nanoTime()}").apply { mkdirs() }
        assertTrue(TermfoldGitignore.ensureTermfoldIgnored(sub))
        assertEquals(".termfold/\n", File(sub, ".gitignore").readText())
    }

    @Test
    fun appendsTermfoldWhenMissing() {
        val dir = File.createTempFile("termfold-gitignore-", null).parentFile!!
        val sub = File(dir, "proj-${System.nanoTime()}").apply { mkdirs() }
        File(sub, ".gitignore").writeText("node_modules/\n")
        assertTrue(TermfoldGitignore.ensureTermfoldIgnored(sub))
        val text = File(sub, ".gitignore").readText()
        assertTrue(text.contains(".termfold/"))
        assertEquals(1, text.lines().count { it.trim() == ".termfold/" })
    }

    @Test
    fun idempotentWhenAlreadyPresent() {
        val dir = File.createTempFile("termfold-gitignore-", null).parentFile!!
        val sub = File(dir, "proj-${System.nanoTime()}").apply { mkdirs() }
        File(sub, ".gitignore").writeText(".termfold/\n")
        assertFalse(TermfoldGitignore.ensureTermfoldIgnored(sub))
    }

    @Test
    fun recognizesExistingTermfoldVariants() {
        assertTrue(TermfoldGitignore.isTermfoldIgnoreLine(".termfold"))
        assertTrue(TermfoldGitignore.isTermfoldIgnoreLine("**/.termfold/**"))
        assertFalse(TermfoldGitignore.isTermfoldIgnoreLine("node_modules/"))
    }
}
