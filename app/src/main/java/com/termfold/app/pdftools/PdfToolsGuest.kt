package com.termfold.app.pdftools

import android.content.Context
import com.termfold.app.acp.AcpInstaller
import com.termfold.app.shell.ShellPaths
import java.io.File

object PdfToolsGuest {
    /** pypdf + OCR/toolkit (fast; used on import and Path A). */
    private const val BASIC_MARKER = "var/lib/termfold/pdfsys-py-2"

    /** WeasyPrint + PyMuPDF + SSIM (Path B recreate/QA only). */
    private const val PATHB_MARKER = "var/lib/termfold/pdfsys-py-3"

    fun run(context: Context, args: List<String>, timeoutSeconds: Long = 120): Pair<Int, String> {
        syncCli(context)
        val quoted = args.joinToString(" ") { shellQuote(it) }
        val script = "${PdfToolsPaths.CLI_GUEST} $quoted"
        return AcpInstaller.runCapture(
            context,
            script,
            guestCwd = "/root",
            timeoutSeconds = timeoutSeconds,
        )
    }

    fun librariesReady(context: Context): Boolean {
        val root = ShellPaths.rootfsDir(context)
        return File(root, BASIC_MARKER).isFile || File(root, PATHB_MARKER).isFile
    }

    fun pathBLibrariesReady(context: Context): Boolean =
        File(ShellPaths.rootfsDir(context), PATHB_MARKER).isFile

    fun syncCli(context: Context) {
        val rootfs = ShellPaths.rootfsDir(context)
        fun writeAsset(assetName: String, guestRelative: String, executable: Boolean = true) {
            val dest = File(rootfs, guestRelative)
            dest.parentFile?.mkdirs()
            if (java.nio.file.Files.isSymbolicLink(dest.toPath())) return
            val text = context.assets.open(assetName).use { it.readBytes().decodeToString() }.replace("\r\n", "\n")
            if (dest.isFile && dest.readText() == text) return
            dest.writeText(text)
            if (executable) dest.setExecutable(true, false)
        }
        writeAsset("pdfsys/pdfsys.py", "usr/local/bin/pdfsys")
        writeAsset("termfold-pdf.js", "usr/local/bin/termfold-pdf")
        writeAsset("pdfsys/qa_loop.py", "usr/local/lib/pdfsys/qa_loop.py")
        writeAsset("pdfsys/render_pages.py", "usr/local/lib/pdfsys/render_pages.py")
    }

    /** Light stack for inspect, toolkit, Path A apply — avoids apt/pip heavy install on every import. */
    fun ensureBasicLibraries(context: Context): Pair<Int, String> {
        syncCli(context)
        val marker = File(ShellPaths.rootfsDir(context), BASIC_MARKER)
        if (marker.isFile && basicProbe(context).first == 0) {
            return 0 to "ok"
        }
        val install = runBasicInstall(context)
        if (install.first == 0) {
            marker.parentFile?.mkdirs()
            marker.writeText("1")
        }
        return install
    }

    /** Full Path B stack (WeasyPrint, PyMuPDF, SSIM). Call before pdf_qa or agent Path B generate. */
    fun ensurePathBLibraries(context: Context): Pair<Int, String> {
        val basic = ensureBasicLibraries(context)
        if (basic.first != 0) return basic
        syncCli(context)
        val marker = File(ShellPaths.rootfsDir(context), PATHB_MARKER)
        if (marker.isFile && pathBProbe(context).first == 0) {
            return 0 to "ok"
        }
        val install = runPathBInstall(context)
        if (install.first == 0) {
            marker.parentFile?.mkdirs()
            marker.writeText("1")
        }
        return install
    }

    /** @deprecated alias — prefer [ensureBasicLibraries] or [ensurePathBLibraries]. */
    fun ensureLibraries(context: Context): Pair<Int, String> = ensureBasicLibraries(context)

    private fun basicProbe(context: Context): Pair<Int, String> =
        AcpInstaller.runCapture(
            context,
            """
            python3 -c "import sys; sys.path.insert(0,'/usr/local/lib/pdfsys-pkgs'); import pypdf" && command -v tesseract >/dev/null && command -v pdftoppm >/dev/null
            """.trimIndent(),
            guestCwd = "/root",
            timeoutSeconds = 30,
        )

    private fun pathBProbe(context: Context): Pair<Int, String> =
        AcpInstaller.runCapture(
            context,
            """
            python3 -c "import sys; sys.path.insert(0,'/usr/local/lib/pdfsys-pkgs'); import fitz; import weasyprint; import skimage.metrics"
            """.trimIndent(),
            guestCwd = "/root",
            timeoutSeconds = 45,
        )

    private fun runBasicInstall(context: Context): Pair<Int, String> =
        AcpInstaller.runCapture(
            context,
            """
            export DEBIAN_FRONTEND=noninteractive
            mkdir -p /usr/local/lib/pdfsys-pkgs
            apt-get update -qq || true
            apt-get install -y --no-install-recommends python3-pypdf python3-reportlab python3-pypdf2 tesseract-ocr tesseract-ocr-eng poppler-utils || true
            python3 -c "import sys; sys.path.insert(0,'/usr/local/lib/pdfsys-pkgs'); import pypdf" 2>/dev/null || python3 -m pip install --target /usr/local/lib/pdfsys-pkgs pypdf reportlab
            python3 -c "import sys; sys.path.insert(0,'/usr/local/lib/pdfsys-pkgs'); import pypdf"
            command -v tesseract >/dev/null
            command -v pdftoppm >/dev/null
            """.trimIndent(),
            guestCwd = "/root",
            timeoutSeconds = 300,
        )

    private fun runPathBInstall(context: Context): Pair<Int, String> {
        val req = context.assets.open("pdfsys/requirements-pdf-tools.txt").use { it.readBytes().decodeToString() }
        File(ShellPaths.rootfsDir(context), "tmp/requirements-pdf-tools.txt").writeText(req)
        return AcpInstaller.runCapture(
            context,
            """
            export DEBIAN_FRONTEND=noninteractive
            mkdir -p /usr/local/lib/pdfsys-pkgs
            apt-get update -qq || true
            apt-get install -y --no-install-recommends \
              libpango-1.0-0 libpangocairo-1.0-0 libcairo2 libgdk-pixbuf-2.0-0 libffi-dev shared-mime-info \
              || true
            python3 -m pip install --upgrade pip || true
            python3 -m pip install --target /usr/local/lib/pdfsys-pkgs -r /tmp/requirements-pdf-tools.txt || \
              python3 -m pip install --target /usr/local/lib/pdfsys-pkgs pymupdf pillow numpy scikit-image weasyprint diffpdf opencv-python-headless
            python3 -c "import sys; sys.path.insert(0,'/usr/local/lib/pdfsys-pkgs'); import fitz; import weasyprint; import skimage.metrics"
            """.trimIndent(),
            guestCwd = "/root",
            timeoutSeconds = 600,
        )
    }

    fun fontsHost(context: Context): File =
        com.termfold.app.shell.Projects.hostPath(context, PdfToolsPaths.FONTS_GUEST)

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
