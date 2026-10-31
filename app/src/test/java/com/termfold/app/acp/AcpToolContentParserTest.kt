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

    @Test
    fun rawOutputShellStdout() {
        val raw = JSONObject()
            .put("stdout", "listening on 3777")
            .put("stderr", "")
            .put("exitCode", 0)
        val text = AcpToolContentParser.parseRawOutput(raw)
        assertTrue(text.contains("listening on 3777"))
        assertTrue(text.contains("[exit 0]"))
    }

    @Test
    fun rawOutputReadContentString() {
        val raw = JSONObject().put("content", "---\npid: 1\ncwd: /proj")
        assertEquals("---\npid: 1\ncwd: /proj", AcpToolContentParser.parseRawOutput(raw))
    }

    @Test
    fun rawOutputGlobFiles() {
        val raw = JSONObject()
            .put("files", JSONArray().put("/tmp/a.txt").put("/tmp/b.txt"))
            .put("totalMatches", 2)
        val text = AcpToolContentParser.parseRawOutput(raw)
        assertTrue(text.contains("/tmp/a.txt"))
        assertTrue(text.contains("/tmp/b.txt"))
    }

    @Test
    fun rawOutputCountOnlyNotMetadataDump() {
        val raw = JSONObject()
            .put("totalMatches", 3707)
            .put("success", true)
        assertEquals("3707 matches", AcpToolContentParser.parseRawOutput(raw))
    }

    @Test
    fun rawOutputNestedSearchMetadata() {
        val raw = JSONObject().put(
            "content",
            JSONObject()
                .put("totalMatches", 12)
                .put("truncated", true),
        )
        assertEquals("12 matches", AcpToolContentParser.parseRawOutput(raw))
    }

    @Test
    fun rawOutputFormattedOutputPreferred() {
        val raw = JSONObject()
            .put("formatted_output", "pretty")
            .put("stdout", "ignored")
        assertEquals("pretty", AcpToolContentParser.parseRawOutput(raw))
    }

    @Test
    fun detailFromUpdateFallsBackToRawOutput() {
        val update = JSONObject()
            .put("sessionUpdate", "tool_call_update")
            .put("toolCallId", "c1")
            .put("status", "completed")
            .put("rawOutput", JSONObject().put("content", "file body"))
        assertEquals("file body", AcpToolContentParser.detailFromUpdate(update))
    }

    @Test
    fun resourceContentText() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "content")
                .put(
                    "content",
                    JSONObject()
                        .put("type", "resource")
                        .put("resource", JSONObject().put("text", "from resource")),
                ),
        )
        assertEquals("from resource", AcpToolContentParser.parseContent(content).detail)
    }

    @Test
    fun rawOutputSuccessOnlyIsEmptyForDisplay() {
        val raw = JSONObject().put("success", true)
        assertEquals("", AcpToolContentParser.parseRawOutput(raw))
        assertTrue(AcpToolContentParser.isMetadataOnlyToolOutput("{\"success\":true}"))
    }

    @Test
    fun detailFromUpdateKeepsExploreTextWhenFinalRawOutputIsSuccessOnly() {
        val exploreText = "src/foo.ts\n  42\texport function register() {}"
        val update = JSONObject()
            .put("sessionUpdate", "tool_call_update")
            .put("toolCallId", "c1")
            .put("status", "completed")
            .put("rawOutput", JSONObject().put("success", true))
        assertEquals(exploreText, AcpToolContentParser.detailFromUpdate(update, exploreText))
    }

    @Test
    fun rawOutputMcpTextContentArray() {
        val raw = JSONObject().put(
            "content",
            JSONArray().put(
                JSONObject().put("type", "text").put("text", "Found 3 symbols"),
            ),
        )
        assertEquals("Found 3 symbols", AcpToolContentParser.parseRawOutput(raw))
    }

    @Test
    fun rawOutputCodegraphJsonFallback() {
        val raw = JSONObject()
            .put("nodes", JSONArray().put(JSONObject().put("id", "n1")))
            .put("edges", JSONArray())
        val text = AcpToolContentParser.parseRawOutput(raw)
        assertTrue(text.contains("nodes"))
        assertTrue(text.contains("n1"))
    }

    @Test
    fun rawOutputJsonString() {
        val raw = """{"content":[{"type":"text","text":"ok"}]}"""
        assertEquals("ok", AcpToolContentParser.parseRawOutput(raw))
    }

    @Test
    fun appendStreamedDetailConcatenatesWithoutNewline() {
        assertEquals("abc", AcpToolContentParser.appendStreamedDetail("ab", "c"))
        assertEquals("first", AcpToolContentParser.appendStreamedDetail("", "first"))
    }

    @Test
    fun parseContentCollectsTerminalId() {
        val content = JSONArray().put(
            JSONObject()
                .put("type", "terminal")
                .put("terminalId", "term_abc"),
        )
        val parsed = AcpToolContentParser.parseContent(content)
        assertEquals(listOf("term_abc"), parsed.terminalIds)
    }

    @Test
    fun chunkDetailFromUpdateUsesRawOutputDelta() {
        val update = JSONObject()
            .put("sessionUpdate", "tool_call_content_chunk")
            .put("toolCallId", "c1")
            .put("rawOutput", JSONObject().put("stdout", "line1\n"))
        assertEquals("line1\n", AcpToolContentParser.chunkDetailFromUpdate(update))
    }
}
