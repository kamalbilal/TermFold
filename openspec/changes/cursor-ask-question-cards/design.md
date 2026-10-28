# Design

## Context

See **proposal.md — Why** and **Codegraph verification notes**. TermFold runs **`agent acp`**; **`cursor/ask_question`** is documented as blocking ([Cursor ACP docs](https://cursor.com/docs/cli/acp)) but **`AcpClient.handleLine`** returns **`Unknown method`**.

**Turn footers today:** `buildAgentChatTimelineEntries(..., turnFooters = turnFooter != null)` — PM and PR pass **`turnFooter`**; plain coding and automation **`AgentSessionScreen`** calls do **not**, so footer lazy rows never appear there.

**PR report pattern:** `reportsByUserMessageId` in **`PrReviewSession`** JSON + **`prReviewReportsPrunedToTimeline`**. Ask snapshots follow the **same map + prune semantics** but live on **`AcpPersistedTimeline`** so every Cursor tab (coding, automation, PM stub key, PR stub key) shares one mechanism.

## Goals / Non-Goals

**Goals:**

- Compliant **`cursor/ask_question`** (Cursor **`agent.id == "cursor"`** only).
- Turn-anchored cards on **all** Cursor session surfaces.
- **`AcpTimelineCodec`** persistence for **terminal** outcomes only.
- Send blocked at **client + UI** while pending ask.
- **`AcpWakePolicy`** parity with permission.
- ChatGPT-feel card UX within Cursor option ids.

**Non-Goals:**

- `cursor/create_plan`, `cursor/update_todos` UI.
- Storing in-flight JSON-RPC across app kill (agent reconnects separately).
- Synthetic **`AcpItem.UserMessage`** for answers (v1).

## Decisions

### 1. Wire handler in `AcpClient`

Add **`cursor/ask_question`** beside **`session/request_permission`**. Ignore or **`respondError`** for non-Cursor agents.

**State:** **`AcpPendingAskQuestion(requestId, toolCallId, title, questions, userMessageId)`** on **`AcpUiState`** (or parallel field cleared with permission).

**Turn id:** latest **`AcpItem.UserMessage.id`** when request arrives.

**Parse:** accept **`allowMultiple`** and **`allow_multiple`**.

**Response:** **`respondAskQuestion`** — nested **`result.outcome`** matching Cursor docs; skip **`reason = "Questions skipped by user"`**.

**Second ask while pending:** respond **`cancelled`** to the new request **or** cancel previous pending with logged policy — prefer **reject second** with error log (single flight).

### 2. Turn footer placement (codegraph-corrected)

**Choice:** In **`AgentSessionScreen`**, for **`isCursor`**:

1. **`turnFootersEnabled = isCursor || turnFooter != null`** (always footers for Cursor so ask rows exist even in plain coding chat).
2. Default footer composable:

```kotlin
Column {
  AskQuestionTurnFooter(userMessageId, ...)  // if snapshot or pending for this id
  turnFooter?.invoke(userMessageId, turnItems, turnFinished)
}
```

Pass this wrapped lambda to **`Timeline`**. PM/PR/automation unchanged at call sites.

**Why:** Codegraph shows **`turnFooters = turnFooter != null`** — without (1), coding sessions never show ask cards.

### 3. State: `asksByUserMessageId` on persisted timeline

**Choice:** Extend **`AcpPersistedTimeline`**:

```kotlin
val askQuestionsByUserMessageId: Map<Long, AskQuestionTurnSnapshot> = emptyMap()
```

Encode/decode JSON object keyed by user message id string. **`persistTimeline()`** includes map from screen-held state synced via **`LaunchedEffect(items)`** or client-owned map updated on submit.

**In-memory pending:** **`pendingAskQuestion`** on **`AcpUiState`** only (not persisted).

**Prune:** **`askQuestionsPrunedToTimeline(items, map)`** — copy **`prReviewReportsPrunedToTimeline`** using **`prReviewUserMessageIds`** / duplicate one-liner on user ids.

**Checkpoint undo:** when **`AcpClient`** prunes maps on restore (**`prunePreTurnMap`** pattern at ~1697), prune ask map the same way.

**Rejected:** PR-style separate **`PrReviewPersistence`** store — wrong for coding/automation tabs.

### 4. UI: `AskQuestionCard`

Same as prior plan: collapsible card, radio/checkbox, Other + **`__freeform_other__`**, Recommended badge, Continue/Skip, summary when terminal.

**Interactive when:** `pendingAskQuestion?.userMessageId == userMessageId` **and** id is latest user message.

**Reference chrome:** **`PrReviewReportChrome`** / **`Palette.Card`** spacing — new composable file e.g. **`AskQuestionCard.kt`**.

### 5. Composer and `send()` blocking (codegraph-corrected)

**Choice:**

- **`AcpClient.send` / `sendNow`:** if **`pendingAskQuestion != null`**, ignore (or enqueue only after answered — prefer **ignore** with no queue so agent is not double-fed).
- **`Composer`:** **`needsAskAnswer = pendingAskQuestion != null`** passed from screen; disable send/queue while true (stricter than today’s permission behavior).

**Optional same PR:** also disable send when **`pendingPermission != null`** — small UX fix, same touch area.

### 6. Wake lock

Extend **`AcpWakePolicy.keepsCpuAwake(..., pendingAskQuestion: Boolean)`**; **`AcpClient.keepsCpuAwake()`** passes **`ui.pendingAskQuestion != null`**. Update **`AcpWakePolicyTest`**.

### 7. Cursor extension notifications

**`cursor/update_todos`**, **`cursor/task`**, **`cursor/generate_image`** remain ignored notifications (log only). No v1 UI. Document so implementers do not confuse with blocking ask.

### 8. Freeform wire format

Use **`__freeform_other__`** in **`selectedOptionIds`**; store display text in snapshot for summary. Smoke task 4.4 confirms agent acceptance; adjust in one encoder if needed.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Extra footer rows on every Cursor turn | Footer slot cheap; ask composable returns early when no snapshot |
| App killed mid-ask | Pending RPC lost; persist terminal only; user may retry turn |
| PM footer + ask stack height | Column order: ask above PR/PM chrome |
| Permission + ask simultaneous | Rare; permission dialog + ask card — prefer permission first if both set |
| Timeline JSON size | Compact snapshot (ids + labels for summary only) |

## Migration Plan

Forward-compatible **`AcpTimelineCodec`** field optional on decode. Ship handler + UI together. Rollback restores **`Unknown method`**.

## Open Questions

- Freeform: confirm with device smoke whether custom text rides on option id only (task 4.4).
- Permission send-block: include in v1 with ask or separate tiny fix — **recommend include** while touching composer.
