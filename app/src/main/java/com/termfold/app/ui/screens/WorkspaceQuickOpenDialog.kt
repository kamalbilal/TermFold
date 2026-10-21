package com.termfold.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.termfold.app.R
import com.termfold.app.acp.WorkspaceMentionIndex
import com.termfold.app.shell.Projects
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * VS Code-style quick open: fuzzy file search in the workspace, best match first.
 */
@Composable
fun WorkspaceQuickOpenDialog(
    guestWorkspace: String,
    onDismiss: () -> Unit,
    onOpenFile: (File) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<WorkspaceMentionIndex.Entry>?>(null) }
    var highlighted by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(guestWorkspace, query) {
        highlighted = 0
        val needle = query
        delay(160)
        if (needle != query) return@LaunchedEffect
        matches = withContext(Dispatchers.IO) {
            WorkspaceMentionIndex.searchFiles(context, guestWorkspace, needle)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(highlighted, matches?.size) {
        val size = matches?.size ?: return@LaunchedEffect
        if (size == 0) return@LaunchedEffect
        val idx = highlighted.coerceIn(0, size - 1)
        listState.animateScrollToItem(idx)
    }

    fun pick(entry: WorkspaceMentionIndex.Entry) {
        val root = Projects.hostPath(context, guestWorkspace)
        val file = File(root, entry.relativePath)
        if (file.isFile) {
            onOpenFile(file)
            onDismiss()
        }
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.Bg)
                .navigationBarsPadding()
                .imePadding(),
            color = Palette.Bg,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val list = matches ?: return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (list.isNotEmpty()) highlighted = (highlighted + 1).coerceAtMost(list.size - 1)
                                true
                            }
                            Key.DirectionUp -> {
                                if (list.isNotEmpty()) highlighted = (highlighted - 1).coerceAtLeast(0)
                                true
                            }
                            Key.Enter -> {
                                list.getOrNull(highlighted)?.let { pick(it) }
                                true
                            }
                            else -> false
                        }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BareIconButton(
                        icon = TermFoldIcons.Close,
                        contentDescription = stringResource(R.string.action_close),
                        onClick = onDismiss,
                    )
                    Text(
                        stringResource(R.string.acp_quick_open_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Palette.Text,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Card)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(TermFoldIcons.Search, null, tint = Palette.TextFaint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                        cursorBrush = SolidColor(Palette.Accent),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.acp_quick_open_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Palette.TextFaint,
                                )
                            }
                            inner()
                        },
                    )
                }
                when {
                    matches == null -> Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.Accent)
                    }
                    matches!!.isEmpty() -> Text(
                        text = if (query.isBlank()) {
                            stringResource(R.string.acp_quick_open_empty)
                        } else {
                            stringResource(R.string.acp_quick_open_no_match, query.trim())
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    else -> {
                        val list = matches!!
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            itemsIndexed(list, key = { _, e -> e.relativePath }) { index, entry ->
                                val active = index == highlighted
                                val lines = WorkspaceMentionIndex.displayLines(entry.relativePath)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) Palette.CardPressed else Color.Transparent)
                                        .clickable { pick(entry) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        TermFoldIcons.Files,
                                        contentDescription = null,
                                        tint = if (active) Palette.Accent else Palette.TextFaint,
                                        modifier = Modifier.size(17.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = lines.mentionName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp),
                                            color = Palette.Text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (lines.fullPathFromRoot != lines.mentionName) {
                                            Text(
                                                text = lines.fullPathFromRoot,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = Mono,
                                                    fontSize = 11.sp,
                                                ),
                                                color = Palette.TextFaint,
                                                maxLines = 1,
                                                overflow = TextOverflow.StartEllipsis,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
