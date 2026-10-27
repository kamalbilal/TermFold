package com.termfold.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.pm.PmDecisionMemo
import com.termfold.app.ui.theme.Palette

@Composable
fun PmDecisionChrome(memo: PmDecisionMemo?) {
    if (memo == null) return
    if (memo.recommendation.isBlank() && memo.options.isBlank()) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.pm_decision_memo_title),
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Accent,
        )
        Spacer(Modifier.height(6.dp))
        if (memo.recommendation.isNotBlank()) {
            Text("Recommendation", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            Text(memo.recommendation, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
            Spacer(Modifier.height(6.dp))
        }
        if (memo.landscape.isNotBlank()) {
            Text("Landscape", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            Text(memo.landscape, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
            Spacer(Modifier.height(6.dp))
        }
        if (memo.options.isNotBlank()) {
            Text("Options", style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
            Text(memo.options, style = MaterialTheme.typography.bodySmall, color = Palette.Text)
        }
    }
}
