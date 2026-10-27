package com.termfold.app.pm

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PmWriteToolPolicyTest {
    @Test
    fun blocksEditKind() {
        val params = JSONObject().put(
            "toolCall",
            JSONObject().put("kind", "edit").put("title", "Edit README"),
        )
        assertTrue(PmWriteToolPolicy.isWriteLike(params))
    }

    @Test
    fun allowsRead() {
        val params = JSONObject().put(
            "toolCall",
            JSONObject().put("kind", "read").put("title", "Read README"),
        )
        assertFalse(PmWriteToolPolicy.isWriteLike(params))
    }
}
