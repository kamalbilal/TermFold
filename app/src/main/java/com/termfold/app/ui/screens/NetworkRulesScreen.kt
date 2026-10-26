package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.preview.network.NetworkRule
import com.termfold.app.preview.network.NetworkRuleKind
import com.termfold.app.preview.network.NetworkRuleStore
import com.termfold.app.preview.network.NetworkRulesHub
import com.termfold.app.ui.components.BareIconButton
import com.termfold.app.ui.theme.Palette
import com.termfold.app.ui.theme.TermFoldIcons

@Composable
fun NetworkRulesScreen(
    sessionKey: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val revision by NetworkRulesHub.revision.collectAsState()
    val store = remember(context) {
        NetworkRulesHub.attach(context)
        NetworkRuleStore.forApp(context)
    }
    val rules = remember(revision, sessionKey) { store.list(sessionKey) }
    val blocked = rules.filter { it.kind == NetworkRuleKind.BLOCK }
    val overrides = rules.filter { it.kind == NetworkRuleKind.OVERRIDE }

    fun delete(rule: NetworkRule) {
        store.remove(sessionKey, rule.id)
        NetworkRulesHub.notifyChanged()
    }

    fun clearAll() {
        store.clear(sessionKey)
        NetworkRulesHub.notifyChanged()
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
                stringResource(R.string.preview_network_rules),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            if (rules.isNotEmpty()) {
                Text(
                    stringResource(R.string.preview_network_rules_clear_all),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = Palette.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { clearAll() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        HorizontalDivider(color = Palette.Border)
        if (rules.isEmpty()) {
            Text(
                stringResource(R.string.preview_network_rules_empty),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                if (blocked.isNotEmpty()) {
                    Text(
                        stringResource(R.string.preview_network_rules_blocked),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Pink,
                    )
                    blocked.forEach { RuleRow(it, onDelete = { delete(it) }) }
                }
                if (overrides.isNotEmpty()) {
                    Text(
                        stringResource(R.string.preview_network_rules_overrides),
                        style = MaterialTheme.typography.labelLarge,
                        color = Palette.Yellow,
                        modifier = Modifier.padding(top = if (blocked.isEmpty()) 0.dp else 18.dp),
                    )
                    overrides.forEach { RuleRow(it, onDelete = { delete(it) }) }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: NetworkRule, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp))
            .background(Palette.Card, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                rule.pattern,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (rule.kind == NetworkRuleKind.OVERRIDE && rule.body.isNotBlank()) {
                Text(
                    rule.bodyPreview(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Palette.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        BareIconButton(
            icon = TermFoldIcons.Trash,
            contentDescription = stringResource(R.string.preview_network_rules_delete),
            onClick = onDelete,
            tint = Palette.TextFaint,
            size = 34,
        )
    }
}
