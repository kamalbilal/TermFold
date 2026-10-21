package com.termfold.app.shell

import android.util.Log
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet
import java.util.zip.GZIPOutputStream

/**
 * Builds a POSIX ustar tar stream in-process, mirroring what [TarExtractor] can read back.
 *
 * GNU long-name records (`L`) are emitted when a path does not fit the 100-byte name field.
 */
internal object TarWriter {

    private const val TAG = "TarWriter"
    private const val BLOCK = 512
    private const val MODE_FILE = 420
    private const val MODE_EXECUTABLE = 493
    private const val MODE_DIR = 493
    private const val MODE_LINK = 511

    fun gzip(output: OutputStream, block: Scope.() -> Unit) {
        GZIPOutputStream(output, 1 shl 16).use { gzip -> tar(gzip, block) }
    }

    fun tar(output: OutputStream, block: Scope.() -> Unit) {
        Scope(output).apply(block).finish()
    }

    class Scope(private val out: OutputStream) {
        private val hardLinkTargets = mutableMapOf<Any, String>()

        fun addBytes(path: String, data: ByteArray) {
            val name = normalize(path)
            writeEntry(name, '0'.code.toChar(), data.size.toLong(), MODE_FILE) {
                out.write(data)
                pad(data.size.toLong())
            }
        }

        fun addDirectory(path: String) {
            val name = normalize(path)
            writeEntry(name, '5'.code.toChar(), 0L, MODE_DIR) {}
        }

        fun addSymlink(path: String, target: String) {
            val name = normalize(path)
            writeEntry(name, '2'.code.toChar(), 0L, MODE_LINK, linkName = target) {}
        }

        fun addHardLink(path: String, existingPath: String) {
            val name = normalize(path)
            writeEntry(name, '1'.code.toChar(), 0L, MODE_FILE, linkName = normalize(existingPath)) {}
        }

        fun addFile(path: String, file: File) {
            val name = normalize(path)
            val size = file.length()
            val mode = if (file.canExecute()) MODE_EXECUTABLE else MODE_FILE
            writeEntry(name, '0'.code.toChar(), size, mode) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(1 shl 16)
                    var remaining = size
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) {
                            throw IOException("Short read archiving ${file.path} ($remaining bytes left)")
                        }
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                }
                pad(size)
            }
        }

        /**
         * Adds every file and directory under [root] at `prefix/...` in the archive.
         * Symbolic links are stored as links; hard links point at the first archived name.
         */
        fun walkTree(
            prefix: String,
            root: File,
            onEntry: (() -> Unit)? = null,
            /** Relative paths under [root] to omit (whole subtrees for directories). */
            skipRelative: (String) -> Boolean = { false },
        ) {
            if (!root.exists()) return
            hardLinkTargets.clear()
            val base = normalize(prefix)
            val rootPath = root.toPath()
            Files.walkFileTree(
                rootPath,
                EnumSet.noneOf(FileVisitOption::class.java),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (dir == rootPath) return FileVisitResult.CONTINUE
                        val rel = rootPath.relativize(dir).toString().replace('\\', '/')
                        if (skipRelative(rel)) return FileVisitResult.SKIP_SUBTREE
                        addDirectory("$base/$rel")
                        onEntry?.invoke()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val rel = rootPath.relativize(file).toString().replace('\\', '/')
                        if (skipRelative(rel)) return FileVisitResult.CONTINUE
                        val arc = "$base/$rel"
                        when {
                            attrs.isSymbolicLink -> {
                                val target = runCatching { Files.readSymbolicLink(file).toString() }
                                    .getOrElse { error ->
                                        Log.w(TAG, "Skipping symlink $rel: ${error.message}")
                                        return FileVisitResult.CONTINUE
                                    }
                                addSymlink(arc, target)
                            }
                            attrs.isRegularFile -> {
                                val key = attrs.fileKey()
                                if (key != null && hardLinkTargets.containsKey(key)) {
                                    addHardLink(arc, hardLinkTargets[key]!!)
                                } else {
                                    if (key != null) hardLinkTargets[key] = arc
                                    runCatching { addFile(arc, file.toFile()) }.onFailure { error ->
                                        Log.w(TAG, "Skipping unreadable file $rel: ${error.message}")
                                    }
                                }
                            }
                        }
                        onEntry?.invoke()
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult =
                        FileVisitResult.CONTINUE
                },
            )
        }

        fun finish() {
            out.write(ByteArray(BLOCK))
            out.write(ByteArray(BLOCK))
        }

        private fun normalize(path: String): String =
            path.trim('/').replace('\\', '/').removePrefix("./")

        private fun writeEntry(
            path: String,
            type: Char,
            size: Long,
            mode: Int,
            linkName: String = "",
            body: () -> Unit,
        ) {
            val needsLong = path.length > 100 ||
                (path.contains('/') && path.substringBeforeLast('/').length > 155)
            if (needsLong) {
                val payload = path.toByteArray(StandardCharsets.UTF_8)
                val withNul = ByteArray(payload.size + 1)
                payload.copyInto(withNul)
                writeHeader("././@LongLink", 'L'.code.toChar(), withNul.size.toLong(), MODE_FILE)
                out.write(withNul)
                pad(withNul.size.toLong())
                writeHeader(path.substringAfterLast('/'), type, size, mode, linkName)
            } else {
                writeHeader(path, type, size, mode, linkName)
            }
            body()
        }

        private fun writeHeader(
            path: String,
            type: Char,
            size: Long,
            mode: Int,
            linkName: String = "",
        ) {
            val header = ByteArray(BLOCK)
            val slash = path.lastIndexOf('/')
            val baseName = if (slash >= 0) path.substring(slash + 1) else path
            val dirPrefix = if (slash >= 0) path.substring(0, slash) else ""
            val (name, prefix) = when {
                needsLongName(path) -> baseName to ""
                dirPrefix.isEmpty() -> path to ""
                dirPrefix.length + 1 + baseName.length <= 100 -> path to ""
                dirPrefix.length <= 155 && baseName.length <= 100 -> baseName to dirPrefix
                else -> baseName to ""
            }
            writeString(header, 0, 100, name)
            writeOctal(header, 100, 8, mode.toLong())
            writeOctal(header, 108, 8, 0)
            writeOctal(header, 116, 8, 0)
            writeOctal(header, 124, 12, size)
            writeOctal(header, 136, 12, 0)
            header[156] = type.code.toByte()
            writeString(header, 157, 100, linkName)
            writeString(header, 257, 6, "ustar")
            writeString(header, 263, 2, "00")
            if (prefix.isNotEmpty() && prefix.length <= 155) {
                writeString(header, 345, 155, prefix)
            }
            writeChecksum(header)
            out.write(header)
        }

        private fun needsLongName(path: String): Boolean =
            path.length > 100 || (path.contains('/') && path.substringBeforeLast('/').length > 155)

        private fun writeString(buffer: ByteArray, offset: Int, length: Int, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            val copy = minOf(bytes.size, length - 1)
            bytes.copyInto(buffer, offset, 0, copy)
        }

        private fun writeOctal(buffer: ByteArray, offset: Int, length: Int, value: Long) {
            val text = String.format("%0${length - 1}o", value)
            writeString(buffer, offset, length, text)
        }

        private fun writeChecksum(header: ByteArray) {
            for (index in 148 until 156) header[index] = ' '.code.toByte()
            var sum = 0
            for (byte in header) sum += byte.toInt() and 0xff
            writeOctal(header, 148, 8, sum.toLong())
        }

        private fun pad(size: Long) {
            val padding = (BLOCK - (size % BLOCK)) % BLOCK
            if (padding > 0) out.write(ByteArray(padding.toInt()))
        }
    }
}
