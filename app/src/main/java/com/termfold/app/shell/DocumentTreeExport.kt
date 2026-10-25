package com.termfold.app.shell

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** Copies a document-tree URI from the system picker into a host directory. */
object DocumentTreeExport {

    fun copyTreeTo(
        context: Context,
        treeUri: Uri,
        into: File,
        onProgress: (String) -> Unit = {},
    ): Int {
        val resolver = context.contentResolver
        val rootDoc = DocumentsContract.getTreeDocumentId(treeUri)
        var count = 0
        fun copyChildren(documentId: String, target: File, depth: Int) {
            if (depth > 64) return
            target.mkdirs()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            resolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = Projects.safeName(c.getString(1) ?: continue) ?: continue
                    val mime = c.getString(2)
                    val out = File(target, name)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        copyChildren(id, out, depth + 1)
                    } else {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        resolver.openInputStream(uri)?.use { input ->
                            out.outputStream().use { input.copyTo(it, 1 shl 16) }
                        }
                        count++
                        if (count % 25 == 0) onProgress("$count files")
                    }
                }
            }
        }
        copyChildren(rootDoc, into, 0)
        return count
    }
}
