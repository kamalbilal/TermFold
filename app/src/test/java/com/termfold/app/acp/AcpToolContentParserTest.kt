package com.termfold.app.acp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcpToolContentParserTest {

    @Test
    fun parsesV1Diff() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "diff")
                .put("path", "/proj/a.kt")
                .put("oldText", "one")
                .put("newText", "two"),
        )
        val parsed = AcpToolContentParser.parseContent(content)
        assertEquals(1, parsed.fileDiffs.size)
        assertEquals("/proj/a.kt", parsed.fileDiffs.single().path)
        assertTrue(parsed.fileDiffs.single().patchText!!.contains("-one"))
        assertTrue(parsed.fileDiffs.single().patchText!!.contains("+two"))
    }

    @Test
    fun parsesV2DiffWithPatch() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "diff")
                .put(
                    "changes",
                    JSONArray().put(
                        JSONObject()
                            .put("operation", "modify")
                            .put("path", "/proj/b.kt"),
                    ),
                )
                .put(
                    "patch",
                    JSONObject()
                        .put("format", "git_patch")
                        .put(
                            "text",
                            """
                            diff --git /proj/b.kt /proj/b.kt
                            --- /proj/b.kt
                            +++ /proj/b.kt
                            @@ -1 +1 @@
                            -x
                            +y
                            """.trimIndent(),
                        ),
                ),
        )
        val parsed = AcpToolContentParser.parseContent(content)
        assertEquals(1, parsed.fileDiffs.size)
        assertTrue(parsed.fileDiffs.single().patchText!!.contains("+y"))
    }

    @Test
    fun pathsFromV2Changes() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "diff")
                .put(
                    "changes",
                    JSONArray().put(
                        JSONObject()
                            .put("operation", "move")
                            .put("oldPath", "/old/p")
                            .put("path", "/new/p"),
                    ),
                ),
        )
        val paths = AcpToolContentParser.pathsFromContent(content)
        assertTrue(paths.contains("/old/p"))
        assertTrue(paths.contains("/new/p"))
    }

    @Test
    fun plainTextDetailSeparateFromDiff() {
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "content")
                    .put(
                        "content",
                        JSONObject().put("type", "text").put("text", "done"),
                    ),
            )
            .put(
                JSONObject()
                    .put("type", "diff")
                    .put("path", "/f")
                    .put("newText", "hi"),
            )
        val parsed = AcpToolContentParser.parseContent(content)
        assertEquals("done", parsed.detail)
        assertEquals(1, parsed.fileDiffs.size)
    }
}
