package com.termfold.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termfold.app.R
import com.termfold.app.acp.AcpPendingAskQuestion
import com.termfold.app.acp.AskQuestionAnswer
import com.termfold.app.acp.AskQuestionPhase
import com.termfold.app.acp.AskQuestionSpec
import com.termfold.app.acp.AskQuestionTurnSnapshot
import com.termfold.app.acp.AskQuestionWireMessage
import com.termfold.app.acp.CURSOR_ASK_FREEFORM_OPTION_ID
import com.termfold.app.ui.theme.Palette

private val RecommendedSuffix = Regex("""\s*\(Recommended\)\s*$""", RegexOption.IGNORE_CASE)

private data class AskSelectionState(
    val single: Map<String, String> = emptyMap(),
    val multi: Map<String, Set<String>> = emptyMap(),
    val otherText: Map<String, String> = emptyMap(),
    val otherSelected: Set<String> = emptySet(),
    val skippedQuestions: Set<String> = emptySet(),
)

@Composable
fun AskQuestionTurnFooters(
    userMessageId: Long,
    lastUserMessageId: Long?,
    pending: AcpPendingAskQuestion?,
    snapshots: List<AskQuestionTurnSnapshot>,
    onAnswer: (List<AskQuestionAnswer>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (snapshots.isEmpty() && pending?.userMessageId != userMessageId) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        snapshots.forEach { snapshot ->
            AskQuestionSnapshotCard(
                userMessageId = userMessageId,
                lastUserMessageId = lastUserMessageId,
                pending = pending,
                snapshot = snapshot,
                onAnswer = onAnswer,
                onSkip = onSkip,
            )
        }
        if (
            pending != null &&
            pending.userMessageId == userMessageId &&
            snapshots.none { it.toolCallId == pending.toolCallId }
        ) {
            AskQuestionSnapshotCard(
                userMessageId = userMessageId,
                lastUserMessageId = lastUserMessageId,
                pending = pending,
                snapshot = AskQuestionTurnSnapshot(
                    toolCallId = pending.toolCallId,
                    title = pending.title,
                    questions = pending.questions,
                    phase = AskQuestionPhase.Pending,
                ),
                onAnswer = onAnswer,
                onSkip = onSkip,
            )
        }
    }
}

@Composable
fun AskQuestionSnapshotCard(
    userMessageId: Long,
    lastUserMessageId: Long?,
    pending: AcpPendingAskQuestion?,
    snapshot: AskQuestionTurnSnapshot,
    onAnswer: (List<AskQuestionAnswer>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effective = if (
        pending != null &&
        pending.userMessageId == userMessageId &&
        pending.toolCallId == snapshot.toolCallId
    ) {
        snapshot.copy(phase = AskQuestionPhase.Pending)
    } else {
        snapshot
    }
    val interactive = effective.phase == AskQuestionPhase.Pending &&
        pending != null &&
        pending.userMessageId == userMessageId &&
        pending.toolCallId == snapshot.toolCallId &&
        userMessageId == lastUserMessageId
    AskQuestionCard(
        snapshot = effective,
        interactive = interactive,
        onAnswer = onAnswer,
        onSkip = onSkip,
        modifier = modifier,
    )
}

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
    AskQuestionTurnFooters(
        userMessageId = userMessageId,
        lastUserMessageId = lastUserMessageId,
        pending = pending,
        snapshots = snapshot?.let { listOf(it) }.orEmpty(),
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
    var selection by remember(snapshot.toolCallId) { mutableStateOf(AskSelectionState()) }

    snapshot.questions.forEachIndexed { index, question ->
        val skipped = selection.skippedQuestions.contains(question.id)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = AskQuestionWireMessage.questionPromptLabel(
                    index,
                    snapshot.questions.size,
                    question.prompt,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (skipped) Palette.TextFaint else Palette.Text,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    selection = if (skipped) {
                        selection.copy(skippedQuestions = selection.skippedQuestions - question.id)
                    } else {
                        selection.copy(
                            skippedQuestions = selection.skippedQuestions + question.id,
                            single = selection.single - question.id,
                            multi = selection.multi - question.id,
                            otherText = selection.otherText - question.id,
                            otherSelected = selection.otherSelected - question.id,
                        )
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        if (skipped) R.string.acp_ask_undo_skip else R.string.acp_ask_skip_question,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextDim,
                )
            }
        }
        if (skipped) {
            Text(
                text = stringResource(R.string.acp_ask_question_skipped),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextFaint,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        } else {
        val displayOptions = question.options.filterNot { isCatchAllOptionLabel(it.label) }
        displayOptions.forEach { option ->
            val (label, recommended) = splitRecommended(option.label)
            val selected = if (question.allowMultiple) {
                selection.multi[question.id]?.contains(option.id) == true
            } else {
                selection.single[question.id] == option.id
            }
            OptionRow(
                label = label,
                recommended = recommended,
                selected = selected,
                onClick = {
                    selection = if (question.allowMultiple) {
                        val current = selection.multi[question.id].orEmpty()
                        val next = if (current.contains(option.id)) {
                            current - option.id
                        } else {
                            current + option.id
                        }
                        selection.copy(
                            multi = selection.multi + (question.id to next),
                            skippedQuestions = selection.skippedQuestions - question.id,
                        )
                    } else {
                        selection.copy(
                            single = selection.single + (question.id to option.id),
                            otherSelected = selection.otherSelected - question.id,
                            skippedQuestions = selection.skippedQuestions - question.id,
                        )
                    }
                },
            )
        }
        val otherLabel = stringResource(R.string.acp_ask_other)
        val otherOn = selection.otherSelected.contains(question.id) ||
            selection.single[question.id] == CURSOR_ASK_FREEFORM_OPTION_ID ||
            selection.multi[question.id]?.contains(CURSOR_ASK_FREEFORM_OPTION_ID) == true
        OptionRow(
            label = otherLabel,
            recommended = false,
            selected = otherOn,
            onClick = {
                selection = if (question.allowMultiple) {
                    val current = selection.multi[question.id].orEmpty()
                    val next = if (current.contains(CURSOR_ASK_FREEFORM_OPTION_ID)) {
                        current - CURSOR_ASK_FREEFORM_OPTION_ID
                    } else {
                        current + CURSOR_ASK_FREEFORM_OPTION_ID
                    }
                    selection.copy(
                        multi = selection.multi + (question.id to next),
                        otherSelected = if (next.contains(CURSOR_ASK_FREEFORM_OPTION_ID)) {
                            selection.otherSelected + question.id
                        } else {
                            selection.otherSelected - question.id
                        },
                        skippedQuestions = selection.skippedQuestions - question.id,
                    )
                } else {
                    selection.copy(
                        single = selection.single + (question.id to CURSOR_ASK_FREEFORM_OPTION_ID),
                        otherSelected = selection.otherSelected + question.id,
                        skippedQuestions = selection.skippedQuestions - question.id,
                    )
                }
            },
        )
        if (otherOn) {
            AskOtherAnswerField(
                value = selection.otherText[question.id].orEmpty(),
                onValueChange = { text ->
                    selection = selection.copy(
                        otherText = selection.otherText + (question.id to text),
                        skippedQuestions = selection.skippedQuestions - question.id,
                    )
                },
            )
        }
        }
    }

    val allValid = snapshot.questions.all { q ->
        selection.skippedQuestions.contains(q.id) ||
            questionAnswered(
                q,
                selection.single,
                selection.multi,
                selection.otherText,
                selection.otherSelected,
            )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (snapshot.questions.size > 1) {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.acp_ask_skip_all), color = Palette.TextDim)
            }
        } else {
            TextButton(onClick = onSkip) {
                Text(stringResource(R.string.acp_ask_skip), color = Palette.TextDim)
            }
        }
        TextButton(
            onClick = {
                onAnswer(
                    buildAnswers(
                        snapshot.questions,
                        selection.single,
                        selection.multi,
                        selection.otherText,
                        selection.otherSelected,
                        selection.skippedQuestions,
                    ),
                )
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
private fun AskOtherAnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (focused) Palette.Accent else Palette.Border
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 80.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(Palette.Field)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = Palette.Text,
            lineHeight = 22.sp,
        ),
        cursorBrush = SolidColor(Palette.Accent),
        interactionSource = interactionSource,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.acp_ask_other_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextFaint,
                    )
                }
                inner()
            }
        },
    )
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
    return AskQuestionWireMessage.formatAnswerDisplaySummary(
        snapshot.title,
        snapshot.questions,
        answers,
    )
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
    skippedQuestions: Set<String>,
): List<AskQuestionAnswer> = questions.map { q ->
    if (skippedQuestions.contains(q.id)) {
        AskQuestionAnswer(questionId = q.id, selectedOptionIds = emptyList(), skipped = true)
    } else if (q.allowMultiple) {
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
    snapshots: List<AskQuestionTurnSnapshot>,
): Boolean =
    snapshots.isNotEmpty() || pending?.userMessageId == userMessageId

fun askFooterVisibleForTurn(
    userMessageId: Long,
    lastUserMessageId: Long?,
    pending: AcpPendingAskQuestion?,
    snapshots: Map<Long, AskQuestionTurnSnapshot>,
): Boolean = askFooterVisibleForTurn(
    userMessageId,
    lastUserMessageId,
    pending,
    snapshots[userMessageId]?.let { listOf(it) }.orEmpty(),
)
