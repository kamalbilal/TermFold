package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.acp.mcp.McpCatalog
import com.termfold.app.acp.mcp.McpPrefs
import com.termfold.app.acp.mcp.McpServerEntry
import com.termfold.app.acp.mcp.McpSource
import com.termfold.app.acp.mcp.McpTransport
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons

@Composable
fun McpServersDialog(
    workspaceGuestDir: String,
    cursorAgent: Boolean,
    onReloadSession: () -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val servers = remember(workspaceGuestDir, cursorAgent, refresh) {
        McpCatalog.list(context, workspaceGuestDir, cursorAgent)
    }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                stringResource(R.string.mcp_title),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Text,
            )
        },
        text = {
            Column(Modifier.heightIn(max = 520.dp)) {
                Text(
                    stringResource(R.string.mcp_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
                Spacer(Modifier.height(10.dp))
                if (servers.isEmpty()) {
                    Text(
                        stringResource(R.string.mcp_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                        modifier = Modifier.padding(8.dp),
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(servers, key = { it.key }) { entry ->
                            McpServerRow(
                                entry = entry,
                                expanded = expandedKey == entry.key,
                                onExpand = { expandedKey = if (expandedKey == entry.key) null else entry.key },
                                onEnabledChange = { enabled ->
                                    McpPrefs.setEnabled(
                                        context,
                                        entry.key,
                                        enabled,
                                        configDefault = !entry.configDisabled,
                                    )
                                    refresh++
                                },
                                onSecretChange = { name, value ->
                                    McpPrefs.setSecret(context, entry.key, name, value)
                                    refresh++
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { refresh++ }) {
                        Icon(TermFoldIcons.Refresh, null, modifier = Modifier.size(16.dp), tint = Palette.TextDim)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mcp_rescan), color = Palette.TextDim)
                    }
                    TextButton(onClick = { onNewSession(); onDismiss() }) {
                        Text(stringResource(R.string.mcp_apply_session), color = Palette.TextDim)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReloadSession(); onDismiss() },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            ) {
                Text(stringResource(R.string.mcp_reload_session), color = Palette.Accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = Palette.TextDim)
            }
        },
    )
}

@Composable
private fun McpServerRow(
    entry: McpServerEntry,
    expanded: Boolean,
    onExpand: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSecretChange: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val configDefault = !entry.configDisabled
    val enabled = McpPrefs.isEnabled(context, entry.key, configDefault)
    val sourceLabel = when (entry.source) {
        McpSource.USER -> stringResource(R.string.mcp_source_user)
        McpSource.PROJECT -> stringResource(R.string.mcp_source_project)
        McpSource.BUILTIN -> stringResource(R.string.mcp_source_builtin)
    }
    val detail = when (val transport = entry.transport) {
        is McpTransport.Stdio -> transport.command + " " + transport.args.take(2).joinToString(" ")
        is McpTransport.Remote -> transport.url
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onExpand() },
            ) {
                Text(entry.name, style = MaterialTheme.typography.labelLarge, color = Palette.Text, maxLines = 1)
                Text(
                    "$sourceLabel · $detail",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono, fontSize = 11.sp),
                    color = Palette.TextFaint,
                    maxLines = 2,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.Accent,
                    checkedTrackColor = Palette.Accent.copy(alpha = 0.35f),
                ),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            entry.configPath?.let { path ->
                Text(
                    path,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = Mono, fontSize = 10.sp),
                    color = Palette.TextFaint,
                )
                Spacer(Modifier.height(6.dp))
            }
            if (entry.authHints.isEmpty()) {
                Text(
                    stringResource(R.string.mcp_no_auth),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            } else {
                entry.authHints.forEach { hint ->
                    val current = McpPrefs.secret(context, entry.key, hint) ?: ""
                    var draft by remember(entry.key, hint, current) { mutableStateOf(current) }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            onSecretChange(hint, it)
                        },
                        label = { Text(hint) },
                        singleLine = true,
                        visualTransformation = if (hint.contains("KEY", ignoreCase = true) ||
                            hint.contains("TOKEN", ignoreCase = true) ||
                            hint.contains("SECRET", ignoreCase = true) ||
                            hint.equals("Authorization", ignoreCase = true)
                        ) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text,
                            unfocusedTextColor = Palette.Text,
                            focusedBorderColor = Palette.Accent,
                            unfocusedBorderColor = Palette.BorderSoft,
                        ),
                    )
                }
            }
        }
    }
}
