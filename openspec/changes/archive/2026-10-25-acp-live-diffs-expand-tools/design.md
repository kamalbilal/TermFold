# Design

## Context

Agent chat renders tool calls as lazy timeline entries (`ToolHeader` + optional `ToolFileSection` rows). `buildAgentChatTimelineEntries` skips file sections when `collapsedTools[id] == true`. `showLiveToolDiffs` only affects `showHunkLines` (`!inProgress || showLiveToolDiffs`), not collapse.

`syncCompletedToolCollapse` (in `AgentChatTimeline.kt`) sets the initial map entry for each new tool id to `collapsed = (status != "in_progress")`, so every completed tool starts collapsed. In-progress tools stay expanded until completion, then the next `LaunchedEffect(items)` pass does **not** update existing keys—so a tool that was expanded while running stays expanded only if the user never got a default key set before completion. In practice, tools that finish before the first sync frame get `collapsed = true` immediately.

The `acp-tool-call-expandable` change spec says tool calls should be expanded by default; the auto-collapse-on-complete behavior was likely added for scroll performance but was never wired to the live-diff preference.

## Goals / Non-Goals

**Goals:**

- When `showLiveToolDiffs` is true, file-changing tool calls are visible without manual expansion (tool row expanded by default through completion).
- When false, preserve collapsed completed tools to limit chat height.
- Never overwrite `collapsedTools[id]` after the user toggles a row.
- Re-read preference on resume from Settings so behavior matches the toggle for **new** tool ids seen after the change.

**Non-Goals:**

- Global “expand all tools” control.
- Changing per-file section defaults (`expandedToolFiles`) unless testing shows users still must expand file headers—in code, files already default expanded (`!= false`); this change targets tool headers first.
- Archiving or merging the older `acp-tool-call-expandable` OpenSpec change (can be done separately).

## Decisions

### 1. Collapse policy function

**Choice:** Replace inline logic in `syncCompletedToolCollapse` with a pure helper, e.g. `defaultToolCallCollapsed(item: AcpItem.ToolCall, showLiveToolDiffs: Boolean): Boolean`:

| Condition | Default collapsed |
|-----------|-------------------|
| `status == in_progress` | `false` (always expanded) |
| `showLiveToolDiffs && fileDiffs.isNotEmpty()` | `false` |
| else | `true` for terminal status; `false` while in progress (already covered) |

For tools with no `fileDiffs`, use `true` on terminal status (current clutter reduction). Optional follow-up: expand all terminal tools when live diffs on—out of scope unless product asks.

**Alternative:** Remove auto-collapse entirely — matches expandable spec but increases scroll cost for everyone; rejected as default; live-diffs-on path achieves the user ask.

### 2. Completion transition

**Choice:** When `items` updates and a tool transitions `in_progress` → `completed`, update `collapsedTools[id]` **only if** the user has not set a preference for that id (still absent from map) **or** document that we only set on first sight.

**Problem:** If first sight was `in_progress` with `collapsed=false`, key exists; completion leaves `false` — good.

If first sight is already `completed` (replay/history), initial key must use the policy above with current `showLiveToolDiffs`.

**Edge:** Session restore loads many completed tools at once — with live diffs on, all file tools expand → tall history. Acceptable for users who opted in; they can collapse individually.

### 3. Settings toggle mid-session

**Choice:** On `showLiveToolDiffs` change (lifecycle resume), do **not** bulk-rewrite existing `collapsedTools` entries. Only affect tools whose id is not yet in the map. Optional: one-time migration flag if we later want “apply new default to all file tools”—not in v1.

### 4. Settings copy (optional)

**Choice:** Append to `R.string` for live diffs: “Keeps file edit tools expanded in chat” (or similar) so the setting description matches behavior.

## Risks / Trade-offs

- **Taller chat with live diffs on** — User explicitly opted in; collapse per row still available.
- **Spec overlap** — Delta spec clarifies relationship to “Live diff updates preference” and tool-row expansion.
- **Two sources of truth** — Collapse policy must stay in one helper used by tests and UI sync.

## Migration Plan

Ship in one app release. No timeline codec migration.

## Open Questions

- Should **non-diff** tools (e.g. long `detail` only) also stay expanded when live diffs are on? **Default no** (proposal out of scope); revisit if users report confusion.
