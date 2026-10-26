package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.prreview.PrReviewFinding
import com.termfold.app.prreview.PrReviewReport
import com.termfold.app.prreview.PrReviewReportParser
import com.termfold.app.prreview.PrReviewRisk
import com.termfold.app.prreview.PrReviewSeverity
import com.termfold.app.prreview.PrReviewSurface
import com.termfold.app.ui.theme.Palette

@Composable
fun PrReviewReportChrome(
    report: PrReviewReport?,
    parseError: String?,
    modifier: Modifier = Modifier,
) {
    if (report == null && parseError == null) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (parseError != null) {
            CardBlock(accent = Palette.Yellow) {
                Text(
                    text = when (parseError) {
                        "missing" -> stringResource(R.string.pr_review_report_missing)
                        else -> stringResource(R.string.pr_review_report_invalid, parseError)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Yellow,
                )
            }
        }
        if (report != null) {
            CardBlock(accent = riskColor(report.risk)) {
                Text(
                    stringResource(R.string.pr_review_report_summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    report.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Text,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    stringResource(R.string.pr_review_report_risk, riskLabel(report.risk)),
                    style = MaterialTheme.typography.labelMedium,
                    color = riskColor(report.risk),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val order = listOf(
                PrReviewSurface.ANDROID,
                PrReviewSurface.LINUX_GUEST,
                PrReviewSurface.SHARED,
                PrReviewSurface.CI_DOCS,
                PrReviewSurface.IOS,
            )
            val grouped = report.inDiffBySurface()
            order.forEach { surface ->
                val items = grouped[surface].orEmpty()
                if (items.isEmpty()) return@forEach
                FindingGroup(
                    title = stringResource(R.string.pr_review_report_in_diff, surfaceLabel(surface)),
                    titleColor = surfaceColor(surface),
                    findings = items,
                )
            }
            val preexisting = report.outOfDiff()
            if (preexisting.isNotEmpty()) {
                FindingGroup(
                    title = stringResource(R.string.pr_review_report_out_of_diff),
                    titleColor = Palette.TextDim,
                    findings = preexisting,
                )
            }
            if (report.coverage.reviewed.isNotEmpty() || report.coverage.skipped.isNotEmpty()) {
                CardBlock(accent = Palette.Green) {
                    Text(
                        stringResource(R.string.pr_review_report_coverage),
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Green,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (report.coverage.reviewed.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.pr_review_report_reviewed,
                                report.coverage.reviewed.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Text,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (report.coverage.skipped.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.pr_review_report_skipped,
                                report.coverage.skipped.joinToString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Yellow,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            if (report.missingTests.isNotEmpty() || report.openQuestions.isNotEmpty()) {
                CardBlock(accent = Palette.Purple) {
                    if (report.missingTests.isNotEmpty()) {
                        Text(
                            stringResource(R.string.pr_review_report_missing_tests),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                        report.missingTests.forEach {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Text,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (report.openQuestions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.pr_review_report_questions),
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Purple,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = if (report.missingTests.isNotEmpty()) 8.dp else 0.dp),
                        )
                        report.openQuestions.forEach {
                            Text(
                                "• $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Text,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingGroup(title: String, titleColor: Color, findings: List<PrReviewFinding>) {
    CardBlock(accent = titleColor) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
        )
        findings.forEach { finding ->
            val severity = severityColor(finding.severity)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(severity),
                )
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            finding.id,
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Accent,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            severityLabel(finding.severity),
                            style = MaterialTheme.typography.labelMedium,
                            color = severity,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        finding.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.Text,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (finding.location.isNotBlank()) {
                        Text(
                            finding.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Blue,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (finding.evidence.isNotBlank()) {
                        Text(
                            stringResource(R.string.pr_review_report_why, finding.evidence),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Yellow,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (finding.suggestion.isNotBlank()) {
                        Text(
                            stringResource(R.string.pr_review_report_fix, finding.suggestion),
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.Green,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBlock(accent: Color = Palette.Border, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Card)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        content()
    }
}

private fun riskColor(risk: PrReviewRisk): Color = when (risk) {
    PrReviewRisk.LOW -> Palette.Green
    PrReviewRisk.MEDIUM -> Palette.Yellow
    PrReviewRisk.HIGH -> Palette.Pink
}

private fun severityColor(severity: PrReviewSeverity): Color = when (severity) {
    PrReviewSeverity.BLOCKER -> Palette.Pink
    PrReviewSeverity.SHOULD_FIX -> Palette.Accent
    PrReviewSeverity.NIT -> Palette.Blue
    PrReviewSeverity.QUESTION -> Palette.Purple
}

private fun surfaceColor(surface: PrReviewSurface): Color = when (surface) {
    PrReviewSurface.ANDROID -> Palette.Green
    PrReviewSurface.LINUX_GUEST -> Palette.Blue
    PrReviewSurface.SHARED -> Palette.Accent
    PrReviewSurface.CI_DOCS -> Palette.Yellow
    PrReviewSurface.IOS -> Palette.Purple
}

@Composable
private fun riskLabel(risk: PrReviewRisk): String = stringResource(
    when (risk) {
        PrReviewRisk.LOW -> R.string.pr_review_risk_low
        PrReviewRisk.MEDIUM -> R.string.pr_review_risk_medium
        PrReviewRisk.HIGH -> R.string.pr_review_risk_high
    },
)

@Composable
private fun surfaceLabel(surface: PrReviewSurface): String = stringResource(
    when (surface) {
        PrReviewSurface.ANDROID -> R.string.pr_review_surface_android
        PrReviewSurface.LINUX_GUEST -> R.string.pr_review_surface_linux_guest
        PrReviewSurface.SHARED -> R.string.pr_review_surface_shared
        PrReviewSurface.CI_DOCS -> R.string.pr_review_surface_ci_docs
        PrReviewSurface.IOS -> R.string.pr_review_surface_ios
    },
)

@Composable
private fun severityLabel(severity: PrReviewSeverity): String = stringResource(
    when (severity) {
        PrReviewSeverity.BLOCKER -> R.string.pr_review_severity_blocker
        PrReviewSeverity.SHOULD_FIX -> R.string.pr_review_severity_should_fix
        PrReviewSeverity.NIT -> R.string.pr_review_severity_nit
        PrReviewSeverity.QUESTION -> R.string.pr_review_severity_question
    },
)

fun prReviewReportFromSessionJson(raw: String?): PrReviewReport? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        PrReviewReportParser.decodeStored(org.json.JSONObject(raw))
    }.getOrNull()
}
