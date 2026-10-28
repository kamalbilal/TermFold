package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termfold.app.R
import com.termfold.app.acp.AcpPendingAskQuestion
import com.termfold.app.acp.AskQuestionAnswer
import com.termfold.app.acp.AskQuestionPhase
import com.termfold.app.acp.AskQuestionSpec
import com.termfold.app.acp.AskQuestionTurnSnapshot
import com.termfold.app.acp.CURSOR_ASK_FREEFORM_OPTION_ID
import com.termfold.app.ui.theme.Palette

private val RecommendedSuffix = Regex("""\s*\(Recommended\)\s*$""", RegexOption.IGNORE_CASE)

@Composable
fun AskQuestionTurnFooter(
    userMessageId: Long,
    lastUserMessageId: Long?,
    pending: AcpPendingAskQuestion?,
    snapshot: AskQuestionTurnSnapshot?,
    onAnswer: (List<AskQuestionAnswer>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = when {
        pending != null && pending.userMessageId == userMessageId -> snapshot?.copy(phase = AskQuestionPhase.Pending)
            ?: AskQuestionTurnSnapshot(
                toolCallId = pending.toolCallId,
                title = pending.title,
                questions = pending.questions,
                phase = AskQuestionPhase.Pending,
            )
        else -> snapshot
    } ?: return
    val interactive = effective.phase == AskQuestionPhase.Pending &&
        pending != null &&
        pending.userMessageId == userMessageId &&
        userMessageId == lastUserMessageId
    AskQuestionCard(
        snapshot = effective,
        interactive = interactive,
        onAnswer = onAnswer,
        onSkip = onSkip,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
fun AskQuestionCard(
    snapshot: AskQuestionTurnSnapshot,
    interactive: Boolean,
    onAnswer: (List<AskQuestionAnswer>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!interactive && snapshot.phase == AskQuestionPhase.Pending) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Card)
            .border(1.dp, Palette.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = snapshot.title?.ifBlank { null }
                ?: stringResource(R.string.acp_ask_questions_title),
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Text,
            fontWeight = FontWeight.SemiBold,
        )
        if (interactive) {
            InteractiveAskBody(snapshot = snapshot, onAnswer = onAnswer, onSkip = onSkip)
        } else {
            AskSummaryBody(snapshot = snapshot)
        }
    }
}

@Composable
private fun InteractiveAskBody(
    snapshot: AskQuestionTurnSnapshot,
    onAnswer: (List<AskQuestionAnswer>) -> Unit,
    onSkip: () -> Unit,
) {
    val singleSelections = remember(snapshot.toolCallId) { mutableStateMapOf<String, String>() }
    val multiSelections = remember(snapshot.toolCallId) { mutableStateMapOf<String, MutableSet<String>>() }
    val otherText = remember(snapshot.toolCallId) { mutableStateMapOf<String, String>() }
    var otherSelectedFor by remember(snapshot.toolCallId) { mutableStateOf<Set<String>>(emptySet()) }

    snapshot.questions.forEach { question ->
        Text(
            text = question.prompt,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.Text,
        )
        val displayOptions = question.options.filterNot { isCatchAllOptionLabel(it.label) }
        displayOptions.forEach { option ->
            val (label, recommended) = splitRecommended(option.label)
            val selected = if (question.allowMultiple) {
                multiSelections[question.id]?.contains(option.id) == true
            } else {
                singleSelections[question.id] == option.id
            }
            OptionRow(
                label = label,
                recommended = recommended,
                selected = selected,
                onClick = {
                    if (question.allowMultiple) {
                        val set = multiSelections.getOrPut(question.id) { mutableSetOf() }
                        if (set.contains(option.id)) set.remove(option.id) else set.add(option.id)
                        multiSelections[question.id] = set
                    } else {
                        singleSelections[question.id] = option.id
                        otherSelectedFor = otherSelectedFor - question.id
                    }
                },
            )
        }
        val otherLabel = stringResource(R.string.acp_ask_other)
        val otherOn = otherSelectedFor.contains(question.id) ||
            singleSelections[question.id] == CURSOR_ASK_FREEFORM_OPTION_ID ||
            multiSelections[question.id]?.contains(CURSOR_ASK_FREEFORM_OPTION_ID) == true
        OptionRow(
            label = otherLabel,
            recommended = false,
            selected = otherOn,
            onClick = {
                if (question.allowMultiple) {
                    val set = multiSelections.getOrPut(question.id) { mutableSetOf() }
                    set.add(CURSOR_ASK_FREEFORM_OPTION_ID)
                    multiSelections[question.id] = set
                    otherSelectedFor = otherSelectedFor + question.id
                } else {
                    singleSelections[question.id] = CURSOR_ASK_FREEFORM_OPTION_ID
                    otherSelectedFor = otherSelectedFor + question.id
                }
            },
        )
        if (otherOn) {
            androidx.compose.foundation.text.BasicTextField(
                value = otherText[question.id].orEmpty(),
                onValueChange = { otherText[question.id] = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Field)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.Text),
            )
        }
    }

    val allValid = snapshot.questions.all { q -> questionAnswered(q, singleSelections, multiSelections, otherText, otherSelectedFor) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.acp_ask_skip), color = Palette.TextDim)
        }
        TextButton(
            onClick = {
                onAnswer(buildAnswers(snapshot.questions, singleSelections, multiSelections, otherText, otherSelectedFor))
            },
            enabled = allValid,
        ) {
            Text(
                stringResource(R.string.acp_ask_continue),
                color = if (allValid) Palette.Accent else Palette.TextFaint,
            )
        }
    }
}

@Composable
private fun AskSummaryBody(snapshot: AskQuestionTurnSnapshot) {
    val summary = when (snapshot.phase) {
        AskQuestionPhase.Skipped -> stringResource(R.string.acp_ask_summary_skipped)
        AskQuestionPhase.Cancelled -> stringResource(R.string.acp_ask_summary_cancelled)
        AskQuestionPhase.Answered -> formatAnswerSummary(snapshot)
        AskQuestionPhase.Pending -> return
    }
    Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
    )
}

private fun formatAnswerSummary(snapshot: AskQuestionTurnSnapshot): String {
    val answers = snapshot.answers.orEmpty()
    if (answers.isEmpty()) return ""
    return answers.joinToString("\n") { answer ->
        val q = snapshot.questions.firstOrNull { it.id == answer.questionId }?.prompt ?: answer.questionId
        val labels = answer.selectedOptionIds.map { id ->
            when (id) {
                CURSOR_ASK_FREEFORM_OPTION_ID -> answer.freeformText?.ifBlank { null } ?: "Other"
                else -> snapshot.questions
                    .firstOrNull { it.id == answer.questionId }
                    ?.options
                    ?.firstOrNull { it.id == id }
                    ?.label
                    ?.let { splitRecommended(it).first }
                    ?: id
            }
        }
        "$q → ${labels.joinToString(", ")}"
    }
}

@Composable
private fun OptionRow(
    label: String,
    recommended: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Palette.AccentSoft else Color.Transparent)
            .border(
                1.dp,
                if (selected) Palette.Accent else Palette.BorderSoft,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Palette.Text else Palette.TextDim,
            modifier = Modifier.weight(1f),
        )
        if (recommended) {
            Text(
                text = stringResource(R.string.acp_ask_recommended),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Accent,
            )
        }
    }
}

private fun splitRecommended(label: String): Pair<String, Boolean> {
    val match = RecommendedSuffix.find(label)
    return if (match != null) {
        label.removeRange(match.range).trimEnd() to true
    } else {
        label to false
    }
}

private fun isCatchAllOptionLabel(label: String): Boolean {
    val n = label.trim().lowercase()
    return n == "other" || n == "other…" || n == "other..." || n.startsWith("something else")
}

private fun questionAnswered(
    question: AskQuestionSpec,
    single: Map<String, String>,
    multi: Map<String, Set<String>>,
    otherText: Map<String, String>,
    otherSelected: Set<String>,
): Boolean {
    return if (question.allowMultiple) {
        val ids = multi[question.id].orEmpty()
        ids.isNotEmpty() && ids.all { id ->
            id != CURSOR_ASK_FREEFORM_OPTION_ID || !otherText[question.id].isNullOrBlank()
        }
    } else {
        when (val id = single[question.id]) {
            null -> false
            CURSOR_ASK_FREEFORM_OPTION_ID -> !otherText[question.id].isNullOrBlank()
            else -> true
        }
    }
}

private fun buildAnswers(
    questions: List<AskQuestionSpec>,
    single: Map<String, String>,
    multi: Map<String, Set<String>>,
    otherText: Map<String, String>,
    otherSelected: Set<String>,
): List<AskQuestionAnswer> = questions.map { q ->
    if (q.allowMultiple) {
        val ids = multi[q.id].orEmpty().toList()
        AskQuestionAnswer(
            questionId = q.id,
            selectedOptionIds = ids,
            freeformText = if (ids.contains(CURSOR_ASK_FREEFORM_OPTION_ID)) otherText[q.id] else null,
        )
    } else {
        val id = single[q.id]!!
        AskQuestionAnswer(
            questionId = q.id,
            selectedOptionIds = listOf(id),
            freeformText = if (id == CURSOR_ASK_FREEFORM_OPTION_ID) otherText[q.id] else null,
        )
    }
}

fun askFooterVisibleForTurn(
    userMessageId: Long,
    lastUserMessageId: Long?,
    pending: AcpPendingAskQuestion?,
    snapshots: Map<Long, AskQuestionTurnSnapshot>,
): Boolean =
    snapshots.containsKey(userMessageId) || pending?.userMessageId == userMessageId
