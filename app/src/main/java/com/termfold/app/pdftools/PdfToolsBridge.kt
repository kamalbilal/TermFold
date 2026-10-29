package com.termfold.app.pdftools

import android.content.Context
import org.json.JSONObject
import java.io.File

object PdfToolsBridge {
    const val PREFIX = "pdf_tools_"

    fun handle(action: String, args: JSONObject, context: Context? = null): JSONObject {
        val sessionId = PdfToolsSessionContext.activeSessionId
            ?: return err("No active PDF Tools session")
        val given = args.optString("session_id")
        if (given.isNotBlank() && given != sessionId) {
            return err("PDF Tools session mismatch")
        }
        return when (action) {
            "pdf_tools_plan" -> acceptPlan(args, context, sessionId)
            "pdf_tools_apply" -> apply(args, context, sessionId)
            "pdf_tools_refuse" -> JSONObject().put("ok", true).put("refused", true)
            "pdf_tools_inspect" -> runGuest(
                context,
                sessionId,
                listOf(
                    "inspect",
                    "${PdfToolsPaths.sessionGuest(sessionId)}/input/source.pdf",
                    "--out",
                    "${PdfToolsPaths.sessionGuest(sessionId)}/inspect.json",
                ),
            )
            "pdf_tools_verify" -> runGuest(
                context,
                sessionId,
                verifyArgs(sessionId, context),
            )
            "pdf_tools_qa" -> {
                if (context == null) {
                    return JSONObject().put("ok", true).put("skipped", true)
                }
                val prep = PdfToolsGuest.ensurePathBLibraries(context)
                if (prep.first != 0) {
                    return err(
                        "Path B libraries (WeasyPrint/PyMuPDF) are not installed. " +
                            "Tap Install on PDF Tools or run guest setup, then retry pdf_qa.",
                    )
                }
                runGuest(
                    context,
                    sessionId,
                    listOf(
                        "qa",
                        "--session",
                        PdfToolsPaths.sessionGuest(sessionId),
                    ),
                    timeoutSeconds = 600,
                )
            }
            "pdf_tools_model" -> runGuest(
                context,
                sessionId,
                listOf(
                    "model",
                    "${PdfToolsPaths.sessionGuest(sessionId)}/input/source.pdf",
                    "--session",
                    PdfToolsPaths.sessionGuest(sessionId),
                ),
                timeoutSeconds = 300,
            )
            "pdf_tools_fields" -> fields(args, context, sessionId)
            else -> err("Unknown PDF Tools action")
        }
    }

    private fun acceptPlan(args: JSONObject, context: Context?, sessionId: String): JSONObject {
        val raw = args.optJSONObject("plan")?.toString()
            ?: args.optString("plan_json").takeIf { it.isNotBlank() }
            ?: return err("plan object required")
        val parsed = PdfToolsPlan.parse(raw)
        if (!parsed.ok) return err(parsed.error ?: "invalid plan")
        val planId = parsed.planId ?: return err("plan id required")
        PdfToolsSessionContext.submitPlan(planId, raw)
        if (context != null) {
            val file = PdfToolsPaths.planFile(PdfToolsPaths.hostSessionDir(context, sessionId))
            file.parentFile?.mkdirs()
            file.writeText(raw)
        }
        return JSONObject()
            .put("ok", true)
            .put("planId", planId)
            .put("awaiting_approval", true)
            .put("plan_path", "${PdfToolsPaths.sessionGuest(sessionId)}/plan.json")
            .put(
                "message",
                "Plan is waiting. In TermFold open PDF document (header ⋮) and tap Approve plan, then call pdf_apply with this planId.",
            )
    }

    private fun apply(args: JSONObject, context: Context?, sessionId: String): JSONObject {
        val reason = PdfToolsAuthz.blockReason(PdfToolsSessionContext.authzClass)
        if (reason != null) return err(reason)
        val planId = args.optString("plan_id").ifBlank { args.optString("planId") }
        val approved = PdfToolsSessionContext.approvedPlanId
        if (approved.isNullOrBlank() || planId != approved) {
            return err(
                "Plan was not user-approved. Open PDF document (header ⋮) and tap Approve plan, then retry pdf_apply with plan_id=$planId",
            )
        }
        val raw = args.optJSONObject("plan")?.toString()
            ?: PdfToolsSessionContext.planJsonForApply()
        if (raw != null) {
            val parsed = PdfToolsPlan.parse(raw)
            if (!parsed.ok) return err(parsed.error ?: "invalid plan")
        }
        if (context == null) return JSONObject().put("ok", true)
        val dir = PdfToolsPaths.ensureStub(context, sessionId)
        PdfToolsFields.ensure(PdfToolsPaths.fieldsFile(dir))
        val planFile = PdfToolsPaths.planFile(dir)
        if (raw != null) planFile.writeText(raw)
        if (!planFile.isFile) planFile.writeText(JSONObject().put("id", planId).put("ops", org.json.JSONArray()).toString())
        val guest = PdfToolsPaths.sessionGuest(sessionId)
        val source = File(PdfToolsPaths.inputDir(dir), "source.pdf")
        val argsList = buildList {
            add("apply")
            if (source.isFile) {
                add("--source")
                add("$guest/input/source.pdf")
            }
            add("--fields")
            add("$guest/fields.json")
            add("--plan")
            add("$guest/plan.json")
            add("--fonts")
            add(PdfToolsPaths.FONTS_GUEST)
            add("-o")
            add("$guest/out/output.pdf")
        }
        return runGuest(context, sessionId, argsList, timeoutSeconds = 300)
    }

    private fun fields(args: JSONObject, context: Context?, sessionId: String): JSONObject {
        if (context == null) {
            return JSONObject().put("ok", true).put("skipped", true)
        }
        val file = PdfToolsFields.ensure(PdfToolsPaths.fieldsFile(PdfToolsPaths.hostSessionDir(context, sessionId)))
        val op = args.optString("op").ifBlank { "get" }
        if (op == "set" || op == "merge") {
            val incoming = args.optJSONObject("fields") ?: return err("fields object required")
            val merged = PdfToolsFields.read(file).toMutableMap()
            incoming.keys().asSequence().forEach { key ->
                merged[key] = incoming.optString(key)
            }
            PdfToolsFields.write(file, merged)
        }
        val current = PdfToolsFields.read(file)
        val obj = JSONObject()
        current.forEach { (k, v) -> obj.put(k, v) }
        return JSONObject()
            .put("ok", true)
            .put("path", "${PdfToolsPaths.sessionGuest(sessionId)}/fields.json")
            .put("keys", org.json.JSONArray(current.keys.toList()))
            .put("fields", obj)
    }

    private fun verifyArgs(sessionId: String, context: Context?): List<String> {
        val guest = PdfToolsPaths.sessionGuest(sessionId)
        val outName = if (context != null) {
            PdfToolsPaths.outputPdf(PdfToolsPaths.hostSessionDir(context, sessionId)).name
        } else {
            "output.pdf"
        }
        val args = mutableListOf(
            "verify",
            "$guest/out/$outName",
            "--report",
            "$guest/report.md",
            "--session",
            guest,
        )
        if (context != null && File(PdfToolsPaths.inputDir(PdfToolsPaths.hostSessionDir(context, sessionId)), "source.pdf").isFile) {
            args.add(1, "--orig")
            args.add(2, "$guest/input/source.pdf")
        }
        return args
    }

    private fun runGuest(
        context: Context?,
        sessionId: String,
        args: List<String>,
        timeoutSeconds: Long = 120,
    ): JSONObject {
        if (context == null) return JSONObject().put("ok", true).put("skipped", true)
        val (code, text) = PdfToolsGuest.run(context, args, timeoutSeconds = timeoutSeconds)
        val parsed = firstJsonObject(text)
        val jsonOk = parsed?.optBoolean("ok") ?: true
        if (code != 0 || (parsed != null && !jsonOk)) {
            val message = parsed?.optString("error")?.ifBlank { null }
                ?: PdfToolsLog.safe(text.take(400).ifBlank { "Toolkit failed" })
            val err = err(message)
            if (parsed != null) err.put("pdfsys", parsed)
            return err
        }
        val result = parsed ?: JSONObject().put("ok", true)
        if (!result.has("ok")) result.put("ok", true)
        result.put("session_id", sessionId)
        return result
    }

    internal fun firstJsonObject(text: String): JSONObject? {
        val start = text.indexOf('{')
        if (start < 0) return null
        return runCatching { JSONObject(text.substring(start)) }.getOrNull()
    }

    private fun err(message: String) = JSONObject().put("error", message)
}
