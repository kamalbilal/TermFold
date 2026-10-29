package com.termfold.app.ui.screens

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import com.termfold.app.R
import com.termfold.app.core.Folder
import com.termfold.app.core.Session
import com.termfold.app.memory.AgentMemoryWorkflow
import com.termfold.app.pdftools.PdfToolsAuthzClass
import com.termfold.app.pdftools.PdfToolsFields
import com.termfold.app.pdftools.PdfToolsInspect
import com.termfold.app.pdftools.PdfToolsJobType
import com.termfold.app.pdftools.PdfToolsKeys
import com.termfold.app.pdftools.PdfToolsPaths
import com.termfold.app.pdftools.PdfToolsPrompt
import com.termfold.app.pdftools.PdfToolsSession
import com.termfold.app.pdftools.PdfToolsSessionContext
import com.termfold.app.preview.BrowserBridge
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.components.BottomNavSpacer
import com.termfold.app.ui.components.LeadingTile
import com.termfold.app.ui.components.ListRow
import com.termfold.app.ui.components.PrimaryButton
import com.termfold.app.ui.components.RowSpacer
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import com.termfold.app.ui.theme.folderTint
import java.io.File

@Composable
fun PdfToolsSectionScreen(
    sessions: List<PdfToolsSession>,
    error: String?,
    librariesReady: Boolean = false,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSessionOptions: (String) -> Unit,
    onStartInspect: () -> Unit,
    onStartGenerate: () -> Unit,
    onInstallTools: () -> Unit = {},
    onToolkit: (String, List<File>, PdfToolsAuthzClass, Map<String, String>) -> Unit,
    useGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var toolkitAuthz by remember { mutableStateOf(PdfToolsAuthzClass.OWN_DATA_DOC) }
    var pendingToolkit by remember { mutableStateOf("merge") }
    var splitStart by remember { mutableStateOf("1") }
    var splitEnd by remember { mutableStateOf("1") }
    var showMoreTools by remember { mutableStateOf(false) }
    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val files = uris.mapNotNull { uri ->
            val name = com.termfold.app.files.FileActions.displayName(context, uri) ?: "file.bin"
            val ext = name.substringAfterLast('.', missingDelimiterValue = "bin").ifBlank { "bin" }
            val tmp = File(context.cacheDir, "pdf-toolkit-${System.nanoTime()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
            tmp.takeIf { it.isFile }
        }
        if (files.isNotEmpty()) {
            val extra = if (pendingToolkit == "split") {
                mapOf("start" to splitStart, "end" to splitEnd)
            } else {
                emptyMap()
            }
            onToolkit(pendingToolkit, files, toolkitAuthz, extra)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        WorkflowsSubscreenHeader(
            onBack = onBack,
            title = stringResource(R.string.workflows_tab_pdf_tools),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.pdf_tools_how),
                    color = Palette.TextDim,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    icon = TermFoldIcons.FilePlus,
                    label = stringResource(R.string.pdf_tools_open),
                    onClick = onStartInspect,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onStartGenerate) {
                    Text(stringResource(R.string.pdf_tools_new_letter), maxLines = 1)
                }
                if (!librariesReady) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        icon = TermFoldIcons.Upload,
                        label = stringResource(R.string.pdf_tools_install),
                        onClick = onInstallTools,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.pdf_tools_install_hint),
                        color = Palette.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showMoreTools = !showMoreTools }) {
                    Text(stringResource(R.string.pdf_tools_more_tools), maxLines = 1)
                }
            }
            if (showMoreTools) {
            item {
                if (librariesReady) {
                    Text(
                        stringResource(R.string.pdf_tools_install_ready),
                        color = Palette.TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onInstallTools) {
                        Text(stringResource(R.string.pdf_tools_install), maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(stringResource(R.string.pdf_tools_toolkit), color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pdf_tools_authz), color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = toolkitAuthz == PdfToolsAuthzClass.OWN_DATA_DOC,
                        onClick = { toolkitAuthz = PdfToolsAuthzClass.OWN_DATA_DOC },
                        label = { Text(stringResource(R.string.pdf_tools_authz_own_doc), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                    FilterChip(
                        selected = toolkitAuthz == PdfToolsAuthzClass.OWN_TEMPLATE,
                        onClick = { toolkitAuthz = PdfToolsAuthzClass.OWN_TEMPLATE },
                        label = { Text(stringResource(R.string.pdf_tools_authz_own_template), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        icon = TermFoldIcons.Files,
                        label = stringResource(R.string.pdf_tools_merge),
                        onClick = { pendingToolkit = "merge"; pickPdfs.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        icon = TermFoldIcons.FileText,
                        label = stringResource(R.string.pdf_tools_rotate),
                        onClick = { pendingToolkit = "rotate"; pickPdfs.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = splitStart,
                        onValueChange = { splitStart = it.filter { ch -> ch.isDigit() }.ifBlank { "1" } },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.pdf_tools_split_start)) },
                        singleLine = true,
                        colors = pdfFieldColors(),
                    )
                    OutlinedTextField(
                        value = splitEnd,
                        onValueChange = { splitEnd = it.filter { ch -> ch.isDigit() }.ifBlank { "1" } },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.pdf_tools_split_end)) },
                        singleLine = true,
                        colors = pdfFieldColors(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    TextButton(onClick = { pendingToolkit = "split"; pickPdfs.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.pdf_tools_split), maxLines = 1)
                    }
                    TextButton(onClick = { pendingToolkit = "extract-text"; pickPdfs.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.pdf_tools_extract_text), maxLines = 1)
                    }
                    TextButton(onClick = { pendingToolkit = "extract-images"; pickPdfs.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.pdf_tools_extract_images), maxLines = 1)
                    }
                    TextButton(onClick = { pendingToolkit = "images-to-pdf"; pickPdfs.launch(arrayOf("image/*")) }) {
                        Text(stringResource(R.string.pdf_tools_images_to_pdf), maxLines = 1)
                    }
                }
            }
            }
            item {
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Palette.Accent, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pdf_tools_jobs), color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }
            if (sessions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.pdf_tools_empty_hint),
                        hint = stringResource(R.string.pdf_tools_empty_subtitle),
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    ListRow(
                        title = session.name,
                        subtitle = {
                            Text(
                                if (session.jobType == PdfToolsJobType.GENERATE) {
                                    stringResource(R.string.pdf_tools_generate)
                                } else {
                                    stringResource(R.string.pdf_tools_open)
                                },
                                color = Palette.TextDim,
                            )
                        },
                        leading = {
                            LeadingTile(tint = folderTint(session.tint), icon = TermFoldIcons.FileText)
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BareIconButton(
                                    icon = TermFoldIcons.More,
                                    contentDescription = stringResource(R.string.cd_more),
                                    onClick = { onOpenSessionOptions(session.id) },
                                    size = 34,
                                    tint = Palette.TextFaint,
                                )
                                Icon(
                                    TermFoldIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = Palette.TextFaint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = { onOpenSession(session.id) },
                    )
                    RowSpacer()
                }
            }
            item { BottomNavSpacer() }
        }
    }
}

@Composable
fun PdfToolsAgentSessionScreen(
    session: PdfToolsSession,
    onBack: () -> Unit,
    onUpdate: (PdfToolsSession) -> Unit,
    onImportFile: (File, String?) -> Unit,
    onGenerate: (Map<String, String>) -> Unit,
    onShare: () -> Unit,
    onApprovePlan: (String) -> Unit,
    pendingPlanJson: String? = null,
    modifier: Modifier = Modifier,
    filesOpen: Boolean = false,
    onToggleFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val tmp = File(context.cacheDir, "pdf-import-${session.id}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        }
        if (tmp.isFile) onImportFile(tmp, password.ifBlank { null })
    }
    DisposableEffect(session.id) {
        PdfToolsSessionContext.activeSessionId = session.id
        PdfToolsSessionContext.authzClass = session.authzClass
        PdfToolsSessionContext.approvedPlanId = session.approvedPlanId
        BrowserBridge.pdfToolsSessionId = session.id
        BrowserBridge.writeBrowserConfig(context.applicationContext)
        PdfToolsPaths.ensureStub(context, session.id)
        PdfToolsFields.ensure(PdfToolsPaths.fieldsFile(PdfToolsPaths.hostSessionDir(context, session.id)))
        onDispose {
            PdfToolsSessionContext.clearIf(session.id)
            if (BrowserBridge.pdfToolsSessionId == session.id) {
                BrowserBridge.pdfToolsSessionId = null
            }
            BrowserBridge.writeBrowserConfig(context.applicationContext)
        }
    }
    LaunchedEffect(session.authzClass, session.approvedPlanId) {
        PdfToolsSessionContext.authzClass = session.authzClass
        PdfToolsSessionContext.approvedPlanId = session.approvedPlanId
    }
    val pendingFromAgent by PdfToolsSessionContext.pendingPlanJson.collectAsState()
    val pendingPlan = pendingPlanJson ?: pendingFromAgent
    var recipient by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        com.termfold.app.pdftools.PdfToolsFields.fromCsv(text).onSuccess { map ->
            recipient = map["recipient"] ?: recipient
            subject = map["subject"] ?: subject
            body = map["body"] ?: body
            sender = map["sender"] ?: sender
        }
    }
    val guestDir = PdfToolsPaths.sessionGuest(session.id)
    val hostDir = PdfToolsPaths.hostSessionDir(context, session.id)
    var extraFields by remember(session.id) {
        mutableStateOf(PdfToolsFields.read(PdfToolsPaths.fieldsFile(hostDir)))
    }
    var newFieldKey by remember(session.id) { mutableStateOf("") }
    var newFieldValue by remember(session.id) { mutableStateOf("") }
    LaunchedEffect(session.id, session.updatedAtMs) {
        extraFields = PdfToolsFields.read(PdfToolsPaths.fieldsFile(hostDir))
    }
    val folder = Folder(
        id = "pdf-tools-${session.id}",
        name = session.name,
        path = hostDir.absolutePath,
        tint = session.tint,
        sessions = listOf(
            Session(
                id = session.id,
                name = session.name,
                command = "",
                tint = session.tint,
                acpAgentId = session.acpAgentId,
                workspaceId = "pdf-stub",
            ),
        ),
    )
    val inspect = session.inspectJson?.let { PdfToolsInspect.parse(it) }
    var showDocument by remember { mutableStateOf(false) }
    LaunchedEffect(session.id) {
        val source = File(PdfToolsPaths.inputDir(hostDir), "source.pdf")
        if (!source.isFile && session.jobType == PdfToolsJobType.INSPECT) showDocument = true
    }
    LaunchedEffect(pendingPlan) {
        if (!pendingPlan.isNullOrBlank()) showDocument = true
    }
    Box(modifier.fillMaxSize()) {
        AgentSessionScreen(
            folder = folder,
            sessionId = session.id,
            workspaceId = "pdf-stub",
            agentId = session.acpAgentId,
            onBack = onBack,
            filesOpen = filesOpen,
            onToggleFiles = onToggleFiles,
            sessionKeyOverride = PdfToolsKeys.acpSessionKey(session.id),
            guestDirOverride = guestDir,
            hideBranchPicker = true,
            promptPrefix = { _ -> PdfToolsPrompt.prefix(session) },
            agentMemoryWorkflow = AgentMemoryWorkflow.PDF_TOOLS,
            pdfToolsMode = true,
            onPdfToolsDocument = { showDocument = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (showDocument) {
            Dialog(
                onDismissRequest = { showDocument = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.Bg)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                    color = Palette.Bg,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BareIconButton(
                                icon = TermFoldIcons.Back,
                                contentDescription = stringResource(R.string.cd_back),
                                onClick = { showDocument = false },
                            )
                            Text(
                                stringResource(R.string.pdf_tools_document_title),
                                color = Palette.Text,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(session.jobType.name, color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                listOf(
                                    PdfToolsAuthzClass.OWN_TEMPLATE to R.string.pdf_tools_authz_own_template,
                                    PdfToolsAuthzClass.OWN_DATA_DOC to R.string.pdf_tools_authz_own_doc,
                                    PdfToolsAuthzClass.THIRD_PARTY_ISSUED to R.string.pdf_tools_authz_third_party,
                                    PdfToolsAuthzClass.UNKNOWN to R.string.pdf_tools_authz_unknown,
                                ).forEach { (cls, res) ->
                                    FilterChip(
                                        selected = session.authzClass == cls,
                                        onClick = { onUpdate(session.copy(authzClass = cls)) },
                                        label = { Text(stringResource(res), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                            if (session.jobType == PdfToolsJobType.INSPECT) {
                                Spacer(Modifier.height(8.dp))
                                PrimaryButton(
                                    icon = TermFoldIcons.FilePlus,
                                    label = stringResource(R.string.pdf_tools_import),
                                    onClick = { pickPdf.launch("application/pdf") },
                                )
                                val sourcePdf = File(PdfToolsPaths.inputDir(hostDir), "source.pdf")
                                val fallbackPages = remember(sourcePdf.path, sourcePdf.lastModified()) {
                                    androidPdfPageCount(sourcePdf)
                                }
                                val pages = inspect?.pages?.takeIf { it > 0 } ?: fallbackPages
                                if (pages > 0 || inspect?.ok == true) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "pages $pages · ${if (inspect?.encrypted == true) "encrypted" else "open"} · signed=${inspect?.signed == true}",
                                        color = Palette.TextDim,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    inspect?.signatureNote?.let {
                                        Text(it, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (session.encrypted) {
                                    Text(stringResource(R.string.pdf_tools_encrypted), color = Palette.Accent, style = MaterialTheme.typography.bodySmall)
                                    field(password, { password = it }, R.string.pdf_tools_password)
                                }
                                Spacer(Modifier.height(8.dp))
                                PdfPagePreview(sourcePdf)
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.pdf_tools_fields), color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
                                extraFields.forEach { (key, value) ->
                                    OutlinedTextField(
                                        value = value,
                                        onValueChange = { next ->
                                            val map = extraFields.toMutableMap()
                                            map[key] = next
                                            extraFields = map
                                            PdfToolsFields.write(PdfToolsPaths.fieldsFile(hostDir), map)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        placeholder = { Text(key, color = Palette.TextFaint) },
                                        colors = pdfFieldColors(),
                                        singleLine = true,
                                    )
                                }
                                OutlinedTextField(
                                    value = newFieldKey,
                                    onValueChange = { newFieldKey = it },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    placeholder = { Text(stringResource(R.string.pdf_tools_field_key), color = Palette.TextFaint) },
                                    colors = pdfFieldColors(),
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = newFieldValue,
                                    onValueChange = { newFieldValue = it },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    placeholder = { Text(stringResource(R.string.pdf_tools_field_value), color = Palette.TextFaint) },
                                    colors = pdfFieldColors(),
                                    singleLine = true,
                                )
                                TextButton(
                                    onClick = {
                                        val key = newFieldKey.trim()
                                        if (key.isEmpty()) return@TextButton
                                        val map = extraFields.toMutableMap()
                                        map[key] = newFieldValue
                                        extraFields = map
                                        PdfToolsFields.write(PdfToolsPaths.fieldsFile(hostDir), map)
                                        newFieldKey = ""
                                        newFieldValue = ""
                                    },
                                ) {
                                    Text(stringResource(R.string.pdf_tools_add_field))
                                }
                                val outPdf = PdfToolsPaths.outputPdf(hostDir)
                                if (outPdf.isFile) {
                                    Spacer(Modifier.height(8.dp))
                                    PdfPagePreview(outPdf)
                                }
                            } else {
                                field(recipient, { recipient = it }, R.string.pdf_tools_recipient)
                                field(subject, { subject = it }, R.string.pdf_tools_subject)
                                field(body, { body = it }, R.string.pdf_tools_body)
                                field(sender, { sender = it }, R.string.pdf_tools_sender)
                                TextButton(onClick = { pickCsv.launch("text/csv") }) {
                                    Text(stringResource(R.string.pdf_tools_csv))
                                }
                                Spacer(Modifier.height(8.dp))
                                PrimaryButton(
                                    icon = TermFoldIcons.Sparkle,
                                    label = stringResource(R.string.pdf_tools_generate),
                                    onClick = {
                                        onGenerate(
                                            mapOf(
                                                "recipient" to recipient,
                                                "subject" to subject,
                                                "body" to body,
                                                "sender" to sender,
                                            ),
                                        )
                                    },
                                )
                                PdfPagePreview(PdfToolsPaths.outputPdf(hostDir))
                            }
                            if (!pendingPlan.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.pdf_tools_plan_title), color = Palette.Text, style = MaterialTheme.typography.labelMedium)
                                Text(pendingPlan, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { onApprovePlan(pendingPlan) }) {
                                    Text(stringResource(R.string.pdf_tools_approve_plan))
                                }
                            }
                            if (session.lastReport != null || session.inspectJson != null) {
                                TextButton(onClick = onShare) { Text(stringResource(R.string.pdf_tools_share)) }
                            }
                            BottomNavSpacer()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun field(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        placeholder = { Text(stringResource(label), color = Palette.TextFaint) },
        colors = pdfFieldColors(),
        singleLine = label != R.string.pdf_tools_body,
    )
}

@Composable
private fun pdfFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Palette.Text,
    unfocusedTextColor = Palette.Text,
    cursorColor = Palette.Accent,
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Border,
    focusedLabelColor = Palette.TextDim,
    unfocusedLabelColor = Palette.TextFaint,
)

private fun androidPdfPageCount(file: File): Int {
    if (!file.isFile) return 0
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    }.getOrDefault(0)
}

@Composable
private fun PdfPagePreview(file: File) {
    if (!file.isFile) return
    val bitmap = remember(file.path, file.lastModified()) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(0)
            val bmp = Bitmap.createBitmap(page.width.coerceAtMost(600), page.height.coerceAtMost(800), Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bmp
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Card),
        )
    }
}
