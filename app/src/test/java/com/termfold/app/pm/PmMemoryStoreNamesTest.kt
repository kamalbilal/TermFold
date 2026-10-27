package com.termfold.app.pm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PmMemoryStoreNamesTest {
    @Test
    fun isolatedStoreNamesPerSession() {
        assertEquals("pm_memory_a", PmPaths.memoryStoreName("a"))
        assertNotEquals(PmPaths.memoryStoreName("a"), PmPaths.memoryStoreName("b"))
    }
}
