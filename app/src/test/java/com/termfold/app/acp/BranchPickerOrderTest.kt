package com.termfold.app.acp

import org.junit.Assert.assertEquals
import org.junit.Test

class BranchPickerOrderTest {

    @Test
    fun exactBranchBeforeSubstring() {
        val all = listOf(
            "remove-posthog-alorica-prod-kamal-bilal",
            "kamal-bilal",
            "main-qa",
        )
        val ordered = BranchPickerOrder.order(all, recent = emptyList(), query = "main-qa")
        assertEquals("main-qa", ordered.first())
    }

    @Test
    fun prefixBeforeMiddleMatch() {
        val all = listOf(
            "feature/kamal-test",
            "kamal",
        )
        val ordered = BranchPickerOrder.order(all, emptyList(), "kamal")
        assertEquals("kamal", ordered.first())
    }

    @Test
    fun emptyQueryPutsRecentsFirst() {
        val ordered = BranchPickerOrder.order(
            all = listOf("b", "a", "c"),
            recent = listOf("c"),
            query = "",
        )
        assertEquals(listOf("c", "b", "a"), ordered)
    }
}
