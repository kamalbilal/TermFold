# Design

## Context

See `proposal.md` — Why. `AgentChatTimeline.syncCompletedToolCollapse` only writes `collapsedTools[id]` when the id is absent. `defaultToolCallCollapsed` already returns expanded when `showLiveToolDiffs && fileDiffs.isNotEmpty()`, but many Cursor tool calls are first synced as `completed` with empty `fileDiffs`; diff content arrives on a later `tool_call_update`. The map entry stays `collapsed = true`, so file sections never render until manual expand.

## Goals / Non-Goals

**Goals:**

- Match `acp-session-chat` live-diff expansion when diffs land asynchronously.
- Preserve explicit user header toggles for the session.
- Keep policy in pure helpers covered by unit tests.

**Non-Goals:**

- Rewriting collapse for all historical tools when the Settings toggle flips (unchanged from prior design).
- Expanding tools with only `detail` text and no structured `fileDiffs`.
- Persisting toggle state across process death.

## Decisions

### 1. Distinguish user toggles from automatic defaults

**Choice:** Maintain `userToggledToolIds: Set<Long>` (or equivalent) in `AgentSessionScreen`, updated only in `ToolCallHeaderRow.onToggle`. Automatic sync never adds to this set.

**Alternative:** Infer user intent from `collapsedTools` value vs `defaultToolCallCollapsed` — fragile when defaults change mid-stream; rejected.

### 2. Re-sync policy in `syncCompletedToolCollapse`

**Choice:** Extend sync signature to accept `userToggledToolIds: Set<Long>`:

- If id not in map → set `defaultToolCallCollapsed(tool, showLiveToolDiffs)`.
- Else if id not in `userToggledToolIds` → set `defaultToolCallCollapsed` again (may flip collapsed → expanded when `fileDiffs` populate).
- Else → leave map entry unchanged.

**Alternative:** Clear map entry when `fileDiffs` transition empty→non-empty — equivalent but less explicit; the re-apply-default approach also fixes “completed before first diff frame” without special cases.

### 3. Completion while expanded (in_progress)

In-progress always defaults expanded; if the user never toggled, re-sync on completion with `fileDiffs` keeps `false`. If the user collapsed during run, `userToggledToolIds` blocks auto-expand when diffs arrive — matches spec.

### 4. Settings copy (optional)

Append clause to `settings_agent_chat_live_diffs` subtitle if one exists, or extend main string: mentions expanded file tools — low risk, tasks optional.

## Risks / Trade-offs

- **Extra map writes each frame** → Only updates when computed collapsed differs; cheap for typical session sizes.
- **User collapses then diffs arrive** → Row stays collapsed by design; user may not notice diff summary in header — acceptable.
- **Live diffs off** → Re-sync still runs but `defaultToolCallCollapsed` returns collapsed for terminal file tools; no behavior change vs today.

## Migration Plan

Single app release; no timeline codec changes. Archive this change into main `acp-session-chat` spec after ship.

## Open Questions

- None blocking — heuristic stays `fileDiffs.isNotEmpty()` unless product asks to include file-kind tools without diff payloads.
