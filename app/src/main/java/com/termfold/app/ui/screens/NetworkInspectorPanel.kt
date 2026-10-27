package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.preview.network.NetworkCaptureStore
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons
import java.net.URI

/** Full-screen network panel (Chrome DevTools–style table). */
@Composable
fun NetworkInspectorScreen(
    sessionKey: String,
    entries: List<NetworkCaptureStore.Entry>,
    preserve: Boolean,
    onPreserveChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filterFailed by remember { mutableStateOf(false) }
    var filterXHR by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<NetworkCaptureStore.Entry?>(null) }
    val listState = rememberLazyListState()
    val shown = remember(entries, filterFailed, filterXHR, search) {
        entries.filter { e ->
            if (filterFailed && !e.failed) return@filter false
            if (filterXHR && !e.isXHR) return@filter false
            if (search.isNotBlank() && !e.url.contains(search, ignoreCase = true)) return@filter false
            true
        }
    }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty() && selected == null) listState.scrollToItem(shown.lastIndex)
    }

    if (selected != null) {
        NetworkEntryDetailPane(
            sessionKey = sessionKey,
            entry = selected!!,
            onBack = { selected = null },
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Palette.Bg),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Close,
                contentDescription = stringResource(R.string.action_close),
                onClick = onClose,
            )
            Text(
                stringResource(R.string.preview_network_inspector),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.preview_network_clear),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = Palette.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = filterXHR,
                onClick = { filterXHR = !filterXHR },
                label = { Text(stringResource(R.string.preview_network_filter_xhr), fontSize = 12.sp) },
            )
            FilterChip(
                selected = filterFailed,
                onClick = { filterFailed = !filterFailed },
                label = { Text(stringResource(R.string.preview_network_filter_failed), fontSize = 12.sp) },
            )
            FilterChip(
                selected = preserve,
                onClick = { onPreserveChange(!preserve) },
                label = { Text(stringResource(R.string.preview_network_preserve_log), fontSize = 12.sp) },
            )
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = {
                Text(stringResource(R.string.preview_network_filter_url), fontSize = 13.sp)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
        )
        NetworkTableHeader()
        HorizontalDivider(color = Palette.Border)
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.preview_network_empty),
                modifier = Modifier.padding(20.dp),
                color = Palette.TextFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(shown, key = { it.id }) { entry ->
                    NetworkTableRow(entry = entry, onClick = { selected = entry })
                    HorizontalDivider(color = Palette.BorderSoft, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun NetworkTableHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Card)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell(stringResource(R.string.preview_network_col_name), Modifier.weight(1f))
        HeaderCell(stringResource(R.string.preview_network_col_method), Modifier.width(52.dp))
        HeaderCell(stringResource(R.string.preview_network_col_status), Modifier.width(44.dp))
        HeaderCell(stringResource(R.string.preview_network_col_type), Modifier.width(56.dp))
        HeaderCell(stringResource(R.string.preview_network_col_size), Modifier.width(52.dp))
        HeaderCell(stringResource(R.string.preview_network_col_time), Modifier.width(48.dp))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = Palette.TextDim,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun NetworkTableRow(
    entry: NetworkCaptureStore.Entry,
    onClick: () -> Unit,
) {
    val nameColor = when {
        entry.outcome == "blocked" -> Palette.Yellow
        entry.failed -> Palette.Pink
        entry.outcome == "pending" -> Palette.TextDim
        else -> Palette.Text
    }
    val statusText = networkStatusLabel(entry)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            networkDisplayName(entry.url),
            modifier = Modifier.weight(1f),
            color = nameColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.method,
            modifier = Modifier.width(52.dp),
            color = Palette.TextDim,
            fontFamily = Mono,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            statusText,
            modifier = Modifier.width(44.dp),
            color = networkStatusColor(entry),
            fontFamily = Mono,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            networkTypeLabel(entry.resourceType),
            modifier = Modifier.width(56.dp),
            color = Palette.TextFaint,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            formatNetworkBytes(entry.responseBodySize),
            modifier = Modifier.width(52.dp),
            color = Palette.TextDim,
            fontFamily = Mono,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            if (entry.outcome == "pending") "…" else "${entry.durationMs}",
            modifier = Modifier.width(48.dp),
            color = Palette.TextDim,
            fontFamily = Mono,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun NetworkEntryDetailPane(
    sessionKey: String,
    entry: NetworkCaptureStore.Entry,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.preview_network_tab_overview),
        stringResource(R.string.preview_network_tab_headers),
        stringResource(R.string.preview_network_tab_payload),
        stringResource(R.string.preview_network_tab_response),
        stringResource(R.string.preview_network_tab_timing),
    )
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BareIconButton(
                icon = TermFoldIcons.Back,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Text(
                networkDisplayName(entry.url),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            entry.url,
            color = Palette.TextFaint,
            fontSize = 11.sp,
            fontFamily = Mono,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TabRow(selectedTabIndex = tab, containerColor = Palette.Card) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, fontSize = 11.sp, maxLines = 1) },
                )
            }
        }
        val body = when (tab) {
            0 -> NetworkCaptureStore.detailText(sessionKey, entry.id, "overview")
            1 -> NetworkCaptureStore.detailText(sessionKey, entry.id, "headers")
            2 -> NetworkCaptureStore.readBodyFull(entry.requestBodyPath).ifBlank { "(empty)" }
            3 -> NetworkCaptureStore.readBodyFull(entry.responseBodyPath).ifBlank { "(empty)" }
            else -> "duration_ms=${entry.durationMs}\nstarted=${entry.startedAt}\nended=${entry.endedAt}"
        }
        Text(
            body,
            fontFamily = Mono,
            fontSize = 11.sp,
            color = Palette.TextDim,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

private fun networkDisplayName(url: String): String = runCatching {
    val u = URI(url)
    val path = u.path.orEmpty().ifBlank { "/" }
    val q = u.query?.let { "?$it" } ?: ""
    (u.host?.let { "$it$path$q" } ?: url).take(200)
}.getOrDefault(url.take(120))

private fun networkStatusLabel(entry: NetworkCaptureStore.Entry): String = when {
    entry.outcome == "blocked" -> "—"
    entry.status > 0 -> entry.status.toString()
    entry.outcome == "pending" -> "…"
    else -> entry.outcome.take(4)
}

@Composable
private fun networkStatusColor(entry: NetworkCaptureStore.Entry): androidx.compose.ui.graphics.Color = when {
    entry.outcome == "blocked" -> Palette.Yellow
    entry.failed -> Palette.Pink
    entry.status in 200..299 -> Palette.Green
    entry.status in 300..399 -> Palette.TextDim
    entry.outcome == "pending" -> Palette.TextFaint
    else -> Palette.Text
}

private fun networkTypeLabel(resourceType: String): String = when (resourceType.lowercase()) {
    "xmlhttprequest", "xhr" -> "xhr"
    "fetch" -> "fetch"
    "document", "main_frame" -> "doc"
    "stylesheet" -> "css"
    "script" -> "js"
    "image" -> "img"
    "font" -> "font"
    else -> resourceType.take(6)
}

private fun formatNetworkBytes(bytes: Int): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "${bytes} B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
