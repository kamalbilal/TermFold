package com.termfold.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.backup.TermFoldBackup
import com.termfold.app.core.AppViewModel
import com.termfold.app.ui.theme.Palette
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BackupCard(viewModel: AppViewModel) {
    val context = LocalContext.current
    val job by viewModel.backupJob.collectAsStateWithLifecycle()
    val busy = job?.let { !it.done && it.error == null } == true
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    val sizeText by produceState(initialValue = "", key1 = Unit) {
        value = withContext(Dispatchers.IO) { shellSizeText(context) }
    }

    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri != null) viewModel.startBackup(uri)
    }

    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) confirmRestore = uri
    }

    if (confirmRestore != null) {
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_restore_confirm_body,
                        sizeText.ifBlank { "?" },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = confirmRestore!!
                        confirmRestore = null
                        viewModel.startRestore(uri)
                    },
                ) {
                    Text(stringResource(R.string.backup_restore_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    when {
        busy -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(job!!.title) },
                text = {
                    Column {
                        Text(job!!.step, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { job!!.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {},
            )
        }
        job?.error != null -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupJob() },
                title = { Text(stringResource(R.string.backup_failed_title)) },
                text = { Text(job!!.error!!) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissBackupJob() }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }
        job?.done == true -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBackupJob() },
                title = { Text(stringResource(R.string.backup_done_title)) },
                text = { Text(job!!.step) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissBackupJob() }) {
                        Text(stringResource(R.string.action_close))
                    }
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.backup_title),
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.backup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextDim,
        )
        Spacer(Modifier.height(14.dp))
        ActionRow(
            items = listOf(
                stringResource(R.string.backup_create) to {
                    if (!busy) createBackup.launch(TermFoldBackup.suggestedFileName())
                },
                stringResource(R.string.backup_restore) to {
                    if (!busy) openBackup.launch(arrayOf("application/octet-stream", "application/gzip", "*/*"))
                },
            ),
        )
    }
}
