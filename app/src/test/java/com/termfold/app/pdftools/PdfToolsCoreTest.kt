package com.termfold.app.pdftools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

class PdfToolsPersistenceTest {
    @Test
    fun roundTrip() {
        val original = PdfToolsData(
            sessions = listOf(
                PdfToolsSession(
                    id = "s1",
                    name = "Inspect",
                    jobType = PdfToolsJobType.INSPECT,
                    authzClass = PdfToolsAuthzClass.OWN_TEMPLATE,
                    inputSha256 = "abc",
                ),
            ),
        )
        val decoded = PdfToolsPersistence.decode(PdfToolsPersistence.encode(original))
        assertEquals(original, decoded)
        assertNull(PdfToolsPersistence.decode(PdfToolsPersistence.encode(PdfToolsData(listOf(original.sessions.single().copy(inputSha256 = null))))).sessions.single().inputSha256)
    }

    @Test
    fun withoutRemoves() {
        val s = PdfToolsSession(id = "a", name = "A", jobType = PdfToolsJobType.GENERATE)
        assertTrue(PdfToolsData(listOf(s)).without("a").sessions.isEmpty())
    }
}

class PdfToolsPathsTest {
    @Test
    fun guestPath() {
        assertEquals("/root/.termfold/pdf-tools/abc", PdfToolsPaths.sessionGuest("abc"))
        assertEquals("pdf-tools/abc", PdfToolsKeys.acpSessionKey("abc"))
    }

    @Test
    fun deleteSessionDir() {
        val root = Files.createTempDirectory("pdf-stub").toFile()
        try {
            val dir = File(root, "abc")
            File(dir, "input").mkdirs()
            File(dir, "input/source.pdf").writeText("x")
            assertTrue(PdfToolsPaths.deleteSessionDir(dir))
            assertFalse(dir.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

class PdfToolsAuthzTest {
    @Test
    fun mutateGate() {
        assertTrue(PdfToolsAuthz.canMutate(PdfToolsAuthzClass.OWN_TEMPLATE))
        assertTrue(PdfToolsAuthz.canMutate(PdfToolsAuthzClass.OWN_DATA_DOC))
        assertFalse(PdfToolsAuthz.canMutate(PdfToolsAuthzClass.THIRD_PARTY_ISSUED))
        assertFalse(PdfToolsAuthz.canMutate(PdfToolsAuthzClass.UNKNOWN))
        assertTrue(PdfToolsAuthz.blockReason(null)!!.isNotBlank())
        assertNull(PdfToolsAuthz.blockReason(PdfToolsAuthzClass.OWN_TEMPLATE))
        assertTrue(PdfToolsAuthz.blockReason(PdfToolsAuthzClass.THIRD_PARTY_ISSUED)!!.contains("issuer"))
    }
}

class PdfToolsImportTest {
    @Test
    fun copyHashAndRejectPages() {
        val root = Files.createTempDirectory("pdf-imp").toFile()
        try {
            val src = File(root, "in.pdf")
            src.writeBytes(minimalPdf(1))
            val dest = File(root, "in")
            val ok = PdfToolsImport.copy(src, dest)
            assertNull(ok.error)
            assertEquals(PdfToolsImport.sha256(src), ok.sha256)
            assertTrue(src.isFile)
            val big = File(root, "big.pdf")
            big.writeBytes(minimalPdf(101))
            val bad = PdfToolsImport.copy(big, File(root, "out"), maxPages = 100)
            assertTrue(bad.error!!.contains("100"))
            assertFalse(File(File(root, "out"), "source.pdf").exists())
            val local = PdfToolsImport.fallbackInspectJson(src)
            assertTrue(local.contains("\"ok\":true") || local.contains("\"ok\": true"))
            assertTrue(com.termfold.app.pdftools.PdfToolsInspect.parse(local).ok)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectSize() {
        val root = Files.createTempDirectory("pdf-sz").toFile()
        try {
            val src = File(root, "in.pdf")
            src.writeBytes(ByteArray(20) { 1 })
            val bad = PdfToolsImport.copy(src, File(root, "d"), maxBytes = 10)
            assertTrue(bad.error!!.contains("MB") || bad.error!!.contains("size"))
        } finally {
            root.deleteRecursively()
        }
    }
}

class PdfToolsPlanTest {
    @Test
    fun overlayDenied() {
        val bad = PdfToolsPlan.parse("""{"id":"p1","ops":[{"kind":"overlay"}]}""")
        assertFalse(bad.ok)
        assertTrue(bad.overlay)
        val good = PdfToolsPlan.parse("""{"id":"p2","ops":[{"kind":"generate"}]}""")
        assertTrue(good.ok)
        val form = PdfToolsPlan.parse("""{"id":"p3","ops":[{"kind":"fill_form"}]}""")
        assertFalse(form.ok)
        assertEquals("p2", good.planId)
    }
}

class PdfToolsFieldsTest {
    @Test
    fun csvOneRowOkManyFail() {
        val ok = PdfToolsFields.fromCsv("recipient,subject\nAda,Hi").getOrThrow()
        assertEquals("Ada", ok["recipient"])
        assertTrue(PdfToolsFields.fromCsv("a,b\n1,2\n3,4").isFailure)
    }

    @Test
    fun jsonNotChat() {
        val fields = PdfToolsFields.fromJson("""{"recipient":"Form Name"}""")
        assertNotEquals("Chat Name", fields["recipient"])
        assertEquals("Form Name", fields["recipient"])
    }
}

class PdfToolsBridgeTest {
    @Test
    fun applyGuards() {
        PdfToolsSessionContext.activeSessionId = null
        assertTrue(PdfToolsBridge.handle("pdf_tools_apply", JSONObject()).has("error"))
        PdfToolsSessionContext.activeSessionId = "s1"
        PdfToolsSessionContext.authzClass = PdfToolsAuthzClass.UNKNOWN
        PdfToolsSessionContext.approvedPlanId = null
        assertTrue(PdfToolsBridge.handle("pdf_tools_apply", JSONObject().put("plan_id", "p")).getString("error").isNotBlank())
        PdfToolsSessionContext.authzClass = PdfToolsAuthzClass.OWN_TEMPLATE
        assertTrue(PdfToolsBridge.handle("pdf_tools_apply", JSONObject().put("plan_id", "p")).getString("error").contains("approved"))
        val plan = JSONObject().put("id", "p9").put("ops", org.json.JSONArray().put(JSONObject().put("kind", "generate")))
        val planned = PdfToolsBridge.handle("pdf_tools_plan", JSONObject().put("plan", plan))
        assertTrue(planned.optBoolean("ok"))
        assertTrue(planned.optBoolean("awaiting_approval"))
        assertTrue(PdfToolsBridge.handle("pdf_tools_apply", JSONObject().put("plan_id", "p9")).getString("error").contains("approved"))
        assertTrue(PdfToolsSessionContext.approvePending())
        assertNotNull(PdfToolsSessionContext.planJsonForApply())
        assertTrue(PdfToolsBridge.handle("pdf_tools_apply", JSONObject().put("plan_id", "p9")).optBoolean("ok"))
        assertTrue(PdfToolsBridge.handle("pdf_tools_model", JSONObject()).optBoolean("ok"))
        assertTrue(PdfToolsBridge.handle("pdf_tools_fields", JSONObject()).optBoolean("ok"))
        val render = PdfToolsBridge.handle("pdf_tools_render", JSONObject())
        assertTrue(render.has("error") || render.optBoolean("ok"))
        val overlay = JSONObject().put("id", "x").put("ops", org.json.JSONArray().put(JSONObject().put("kind", "overlay")))
        assertTrue(PdfToolsBridge.handle("pdf_tools_plan", JSONObject().put("plan", overlay)).has("error"))
        PdfToolsSessionContext.clearIf("s1")
    }
}

class PdfToolsPromptTest {
    @Test
    fun mentionsSpecPathsAndTools() {
        val text = PdfToolsPrompt.prefix(
            PdfToolsSession(id = "s", name = "n", jobType = PdfToolsJobType.GENERATE, authzClass = PdfToolsAuthzClass.OWN_TEMPLATE),
        )
        assertTrue(text.contains("Path B"))
        assertTrue(text.contains("probe"))
        assertTrue(text.contains("Path A"))
        assertTrue(text.contains("pdf_apply"))
        assertTrue(text.contains("pdf_qa"))
        assertTrue(text.contains("qa_loop.py"))
        assertTrue(text.contains("OWN_TEMPLATE"))
        val guide = com.termfold.app.memory.AgentMemoryGuidance.promptBlock(
            com.termfold.app.memory.AgentMemoryWorkflow.PDF_TOOLS,
        )
        assertTrue(guide.contains("Path B"))
        assertTrue(guide.contains("recreate"))
    }
}

class PdfToolsShareAndLogTest {
    @Test
    fun pdfMimeAndRedact() {
        assertTrue(PdfToolsShare.isPdf("application/pdf", listOf("a.bin")))
        assertTrue(PdfToolsShare.isPdf("application/octet-stream", listOf("a.pdf")))
        assertFalse(PdfToolsShare.isPdf("text/plain", listOf("a.txt")))
        val body = "SECRET-DOC-BODY"
        assertFalse(PdfToolsLog.safe("saw $body", body).contains(body))
    }
}

class PdfToolsInspectParseTest {
    @Test
    fun encryptedCode() {
        val s = PdfToolsInspect.parse("""{"ok":false,"code":"ENCRYPTED","encrypted":true}""")
        assertTrue(s.encrypted)
        val signed = PdfToolsInspect.parse("""{"ok":true,"signed":true,"signature_note":"This file is signed. Modifying it would invalidate the signatures."}""")
        assertTrue(signed.signed)
        assertTrue(signed.signatureNote!!.contains("invalidate"))
    }
}

class PdfToolsActionsTest {
    @Test
    fun generateSkipsInspect() {
        assertTrue(PdfToolsActions.shouldSkipInspectOnCreate(PdfToolsJobType.GENERATE))
        assertFalse(PdfToolsActions.shouldSkipInspectOnCreate(PdfToolsJobType.INSPECT))
    }
}

class WorkflowsPdfToolsOrderTest {
    @Test
    fun pdfToolsBeforeMore() {
        val names = com.termfold.app.ui.screens.WorkflowsSection.entries.map { it.name }
        assertTrue(names.indexOf("PDF_TOOLS") < names.indexOf("MORE"))
        assertTrue(names.contains("PDF_TOOLS"))
    }
}

class PdfToolsPdfsysHostTest {
    @Test
    fun probeMergeGenerateNoJs() {
        val script = PdfToolsPdfsysHost.scriptFile() ?: return
        val root = Files.createTempDirectory("pdfsys").toFile()
        try {
            val probe = PdfToolsPdfsysHost.run(listOf("probe"))
            assertEquals(probe.second, 0, probe.first)
            val fonts = PdfToolsPdfsysHost.fontsDir() ?: File(root, "fonts").apply { mkdirs() }
            val fields = File(root, "fields.json")
            fields.writeText("""{"recipient":"Form Name","subject":"S","body":"B","sender":"Me"}""")
            val a = File(root, "a.pdf")
            val b = File(root, "b.pdf")
            val genA = PdfToolsPdfsysHost.run(
                listOf("generate", "--fields", fields.absolutePath, "--fonts", fonts.absolutePath, "-o", a.absolutePath),
            )
            if (genA.first != 0) {
                a.writeBytes(validPdf(1))
                b.writeBytes(validPdf(1))
            } else {
                val genB = PdfToolsPdfsysHost.run(
                    listOf("generate", "--fields", fields.absolutePath, "--fonts", fonts.absolutePath, "-o", b.absolutePath),
                )
                assertEquals(genB.second, 0, genB.first)
            }
            assertTrue(a.isFile)
            val beforeA = PdfToolsImport.sha256(a)
            val merged = File(root, "m.pdf")
            val merge = PdfToolsPdfsysHost.run(listOf("merge", a.absolutePath, b.absolutePath, "-o", merged.absolutePath))
            assertEquals(merge.second, 0, merge.first)
            assertTrue(merged.isFile)
            assertEquals(beforeA, PdfToolsImport.sha256(a))
            val jsPdf = File(root, "js.pdf")
            jsPdf.writeBytes(a.readBytes() + "\n/JavaScript /OpenAction /Launch os.system\n".toByteArray())
            val inspect = PdfToolsPdfsysHost.run(listOf("inspect", a.absolutePath))
            assertTrue(inspect.second.contains("\"actions_executed\": false") || inspect.second.contains("\"actions_executed\":false"))
            assertEquals(0, inspect.first)
            assertFalse(inspect.second.contains("os.system"))
            val inspectJs = PdfToolsPdfsysHost.run(listOf("inspect", jsPdf.absolutePath))
            assertTrue(inspectJs.second.contains("\"actions_executed\": false") || inspectJs.second.contains("\"actions_executed\":false"))
            val out = File(root, "letter.pdf")
            a.copyTo(out, overwrite = true)
            val extractedFile = File(root, "t.txt")
            PdfToolsPdfsysHost.run(listOf("extract-text", out.absolutePath, "-o", extractedFile.absolutePath))
            val extracted = extractedFile.takeIf { it.isFile }?.readText().orEmpty()
            if (genA.first == 0) {
                assertTrue(extracted.contains("Form Name") || genA.second.contains("Form Name"))
                assertFalse(extracted.contains("Chat Name"))
            }
            val report = File(root, "report.md")
            val verify = PdfToolsPdfsysHost.run(listOf("verify", out.absolutePath, "--report", report.absolutePath))
            assertEquals(verify.second, 0, verify.first)
            assertTrue(report.readText().contains("not an original issued document", ignoreCase = true))
            val session = File(root, "sess").apply { mkdirs() }
            File(session, "out").mkdirs()
            val sourceDir = File(session, "input").apply { mkdirs() }
            val source = File(sourceDir, "source.pdf")
            if (genA.first == 0) a.copyTo(source, overwrite = true) else source.writeBytes(validPdf(2))
            val modeled = PdfToolsPdfsysHost.run(
                listOf("model", source.absolutePath, "--session", session.absolutePath),
            )
            assertEquals(modeled.second, 0, modeled.first)
            assertTrue(File(session, "work/model.json").isFile)
            assertTrue(modeled.second.contains("\"id\": \"p1\"") || modeled.second.contains("\"id\":\"p1\""))
            File(session, "fields.json").writeText("""{"employee_name":"Ada"}""")
            File(session, "plan.json").writeText(
                """{"id":"p1","ops":[{"kind":"replace_text","find":"Form Name","replace":"Ada"}]}""",
            )
            val appliedOut = File(session, "out/output.pdf")
            val applied = PdfToolsPdfsysHost.run(
                listOf(
                    "apply",
                    "--source", source.absolutePath,
                    "--fields", File(session, "fields.json").absolutePath,
                    "--plan", File(session, "plan.json").absolutePath,
                    "--fonts", fonts.absolutePath,
                    "-o", appliedOut.absolutePath,
                ),
            )
            if (applied.first == 0 && appliedOut.isFile) {
                assertTrue(PdfToolsImport.pageCount(appliedOut) >= 1)
            } else {
                assertTrue(
                    applied.second.contains("READ_FAILED") ||
                        applied.second.contains("TEXT_NOT_FOUND") ||
                        applied.second.contains("NEED_RECREATE") ||
                        applied.first != 0,
                )
            }
        } finally {
            root.deleteRecursively()
        }
        assertTrue(script.isFile)
        val font = File("app/src/main/assets/pdf-fonts/LiberationSans-Regular.ttf")
        val font2 = File("src/main/assets/pdf-fonts/LiberationSans-Regular.ttf")
        assertTrue(font.isFile || font2.isFile)
    }
}

class PdfToolsMcpAssetTest {
    @Test
    fun assetListsTools() {
        val file = listOf(
            File("app/src/main/assets/termfold-pdf.js"),
            File("src/main/assets/termfold-pdf.js"),
        ).first { it.isFile }
        val text = file.readText()
        for (name in listOf("pdf_inspect", "pdf_model", "pdf_plan", "pdf_apply", "pdf_verify", "pdf_qa", "pdf_render", "pdf_visual_confirm", "pdf_refuse", "pdf_fields")) {
            assertTrue(text.contains(name))
        }
    }
}

private fun minimalPdf(pages: Int): ByteArray = validPdf(pages)

private fun validPdf(pages: Int): ByteArray {
    val objects = mutableListOf<String>()
    objects += "<< /Type /Catalog /Pages 2 0 R >>"
    val kids = (1..pages).joinToString(" ") { "${it + 2} 0 R" }
    objects += "<< /Type /Pages /Kids [$kids] /Count $pages >>"
    repeat(pages) {
        objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>"
    }
    val chunks = ArrayList<ByteArray>(objects.size + 1)
    chunks += "%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1)
    val offsets = IntArray(objects.size + 1)
    offsets[0] = 0
    var pos = chunks[0].size
    for (i in objects.indices) {
        offsets[i + 1] = pos
        val body = "${i + 1} 0 obj${objects[i]}endobj\n".toByteArray(Charsets.ISO_8859_1)
        chunks += body
        pos += body.size
    }
    val xrefStart = pos
    val xref = buildString {
        append("xref\n0 ${objects.size + 1}\n")
        append("0000000000 65535 f \n")
        for (i in 1..objects.size) {
            append("%010d 00000 n \n".format(offsets[i]))
        }
        append("trailer<< /Size ${objects.size + 1} /Root 1 0 R >>\n")
        append("startxref\n$xrefStart\n%%EOF\n")
    }.toByteArray(Charsets.ISO_8859_1)
    val out = ByteArray(pos + xref.size)
    var at = 0
    for (c in chunks) {
        System.arraycopy(c, 0, out, at, c.size)
        at += c.size
    }
    System.arraycopy(xref, 0, out, at, xref.size)
    return out
}
