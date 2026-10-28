# Proposal

## Why

Cursor’s agent blocks on **`cursor/ask_question`** when it needs structured clarifications (plan mode, ambiguous tasks, multi-option decisions). TermFold’s ACP client today answers those requests with **`Unknown method`**, so the run fails or the model falls back to messy free-text back-and-forth. Users expect a **ChatGPT-quality inline form** (single/multi choice, custom “Other” text, Continue / Skip) anchored **per user turn**—the same mental model as PR review report cards: first message’s forms stay visible above when you scroll to a second message; new asks appear only under the newer turn.

## What Changes

- Implement **`cursor/ask_question`** as a blocking ACP extension (**Cursor agent only**): parse request, hold **`pendingAskQuestion`**, return **`answered` / `skipped` / `cancelled`** with Cursor-compatible skip reason (**`Questions skipped by user`**).
- Add **per-turn ask cards** under **`AgentChatLazyEntry.TurnFooter`**, keyed by **user message id**—same anchoring as PR **`reportsByUserMessageId`** footers, but stored in **`AcpPersistedTimeline`** (not PR session JSON).
- **Enable turn footers for all Cursor sessions**, including plain coding and automation chats that today pass **`turnFooter = null`** (those sessions currently set **`turnFooters = false`** and never insert footer rows). PM/PR keep their existing footers; ask card **stacks above** them in the same footer slot via an internal wrapper in **`AgentSessionScreen`**.
- **Persist** completed ask snapshots in **`AcpTimelineCodec`** / **`AcpTimelineStore`**; prune on undo/truncate like **`prReviewReportsPrunedToTimeline`**. Do **not** persist in-flight RPC state across process death (terminal outcomes only).
- **Block new prompts** while a pending ask is open: **`AcpClient.send`** guard **and** composer UI (today **`canSend`** ignores **`pendingPermission`**—fix both for ask; optionally align permission in the same change).
- **Wake lock:** extend **`AcpWakePolicy.keepsCpuAwake`** (used by **`SessionWatcher`**) so pending ask keeps CPU awake like permission prompts.
- **UX polish** (Cursor wire format, ChatGPT-like feel): one card, radio/checkbox, **Other…** + **`__freeform_other__`**, **(Recommended)** badges, Continue/Skip, read-only summary; only latest pending ask is editable.
- **Out of scope v1:** `cursor/create_plan`, non-Cursor agents, MCP custom ask tool, headless auto-skip, duplicate synthetic user chat bubble.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `acp-session-chat`: Cursor ask-question ACP extension, turn-footer cards (all Cursor session kinds), timeline persistence, composer/send blocking, wake policy.

## Impact

- **`AcpClient`**: `handleLine`, `AcpUiState`, `respondAskQuestion`, `send`/`sendNow` guard, `persistTimeline` snapshot field, checkpoint undo prune hook.
- **`AcpTimelineCodec` / `AcpTimelineStore`**: `askQuestionsByUserMessageId` on **`AcpPersistedTimeline`**.
- **`AgentSessionScreen`**: internal **`AskQuestionTurnFooter`** + wrap external **`turnFooter`**; **`turnFooters`** predicate for Cursor; composer gating.
- **`AcpWakePolicy`** + **`AcpWakePolicyTest`**: pending ask flag.
- New helpers/tests mirroring **`PrReviewTurnReports`** prune/turn logic (shared **`agentChatItemsForUserTurn`** already exists).
- PM / PR / automation: no separate fork—inherit wrapper from **`AgentSessionScreen`**.

## Codegraph verification notes (2026-10-28)

Verified against indexed codebase:

| Claim | Status |
|--------|--------|
| `Unknown method` for unhandled RPC | **Confirmed** — only `session/request_permission` + fs stubs in `handleLine` |
| Turn footer hook | **Confirmed** — `TurnFooter` in `AgentChatTimeline.kt`; invoked when `turnFooter != null` |
| Plain coding lacks footers today | **Gap fixed in plan** — `MainActivity` `AgentSessionScreen` has no `turnFooter`; must enable for Cursor |
| PR persistence pattern | **Analogous, different store** — `reportsByUserMessageId` in **`PrReviewPersistence`**, not timeline codec; asks use **`AcpPersistedTimeline`** |
| Composer blocks on permission | **Not today** — `canSend` is content-only; plan adds ask (+ optional permission) block |
| Wake lock on permission | **Confirmed** — `AcpWakePolicy.pendingPermission` → `AcpClient.keepsCpuAwake()` |
