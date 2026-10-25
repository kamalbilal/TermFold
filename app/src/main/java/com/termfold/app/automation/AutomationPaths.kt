package com.termfold.app.automation

import android.content.Context
import com.termfold.app.shell.Projects
import java.io.File

object AutomationPaths {
    const val GUEST_ROOT = "/root/.termfold/automation"

    fun projectGuest(projectId: String): String =
        "${GUEST_ROOT.trimEnd('/')}/$projectId"

    fun sessionGuest(projectId: String, sessionId: String): String =
        "${projectGuest(projectId).trimEnd('/')}/sessions/$sessionId"

    fun sessionDocsGuest(projectId: String, sessionId: String): String =
        "${sessionGuest(projectId, sessionId).trimEnd('/')}/docs"

    fun sessionSecretsGuest(projectId: String, sessionId: String): String =
        "${sessionGuest(projectId, sessionId).trimEnd('/')}/secrets"

    fun hostProjectDir(context: Context, projectId: String): File =
        Projects.hostPath(context, projectGuest(projectId))

    fun hostSessionDir(context: Context, projectId: String, sessionId: String): File =
        Projects.hostPath(context, sessionGuest(projectId, sessionId))
}
