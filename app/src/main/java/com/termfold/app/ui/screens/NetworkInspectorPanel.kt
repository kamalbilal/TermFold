package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.preview.network.NetworkCaptureStore
import com.termfold.app.ui.theme.Mono
import com.termfold.app.ui.theme.Palette

@Composable
fun NetworkInspectorDrawer(
    sessionKey: String,
    entries: List<NetworkCaptureStore.Entry>,
    height: androidx.compose.ui.unit.Dp,
    preserve: Boolean,
    onPreserveChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onSelectEntry: (NetworkCaptureStore.Entry) -> Unit,
) {
    var filterFailed by remember { mutableStateOf(false) }
    var filterXHR by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
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
        if (shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(Palette.Card)
            .border(1.dp, Palette.Border),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Network", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = Palette.Text)
            Spacer(Modifier.weight(1f))
            Text(
                "Close",
                color = Palette.TextDim,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(10.dp),
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(selected = filterXHR, onClick = { filterXHR = !filterXHR }, label = { Text("XHR") })
            FilterChip(selected = filterFailed, onClick = { filterFailed = !filterFailed }, label = { Text("Failed") })
            FilterChip(
                selected = preserve,
                onClick = { onPreserveChange(!preserve) },
                label = { Text("Preserve") },
            )
            Text(
                "Clear",
                color = Palette.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        androidx.compose.material3.OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            placeholder = { Text("Filter URL", fontSize = 12.sp) },
            singleLine = true,
        )
        if (shown.isEmpty()) {
            Text(
                "No requests yet.",
                modifier = Modifier.padding(16.dp).weight(1f),
                color = Palette.TextFaint,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(shown, key = { it.id }) { entry ->
                    val color = when {
                        entry.outcome == "blocked" -> Palette.Yellow
                        entry.failed -> Palette.Pink
                        else -> Palette.Text
                    }
                    Text(
                        entry.summaryLine(),
                        color = color,
                        fontFamily = Mono,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEntry(entry) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkEntryDetailSheet(
    sessionKey: String,
    entry: NetworkCaptureStore.Entry,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Headers", "Payload", "Response", "Timing")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(16.dp)) {
            Text(entry.url, color = Palette.Text, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, fontSize = 11.sp) })
                }
            }
            Spacer(Modifier.height(12.dp))
            val body = when (tab) {
                0 -> NetworkCaptureStore.detailText(sessionKey, entry.id, "overview")
                1 -> NetworkCaptureStore.detailText(sessionKey, entry.id, "headers")
                2 -> NetworkCaptureStore.readBodyFull(entry.requestBodyPath).ifBlank { "(empty)" }
                3 -> NetworkCaptureStore.readBodyFull(entry.responseBodyPath).ifBlank { "(empty)" }
                else -> "duration_ms=${entry.durationMs}"
            }
            Text(
                body,
                fontFamily = Mono,
                fontSize = 11.sp,
                color = Palette.TextDim,
                modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
