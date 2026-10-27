# Design

## Context

`ThoughtRow` uses a dedicated header (label + chevron), collapsed one-line preview via `tickerText`, and a bordered body with full `Markdown` up to 360dp scroll. `ToolCallRow` puts `toolLabel()` in the header with `maxLines = if (open) 4 else 1`, only shows a chevron when `hasDetail`, and disables tap when there is no detail or diffs. Users see truncated titles with no way to read the rest on many tools.

See `proposal.md` for motivation.

## Goals / Non-Goals

**Goals:**

- Mirror Thought-row interaction for every `ToolCallRow` (default expanded, tap toggles, chevron always).
- Render **full** `toolLabel()` in the expanded body (mono/small, wrap allowed).
- Collapsed header: icon + optional status spinner + one-line preview (reuse `tickerText` or plain ellipsis on label) + chevron.
- Preserve `ToolCallDiffSection`, detail `Text`, live-diff prefs, nested scroll, and session presentation cache inside expanded body only.

**Non-Goals:**

- Changing ACP ingest, timeline codec, or tool-call data model.
- Auto-collapsing completed tools or persisting collapse state across app restart.
- Redesigning per-file diff headers inside `ToolCallDiffSection`.

## Decisions

### 1. Split header vs body like Thoughts

**Choice:** Header row = icon, static kind label or short status (optional), collapsed preview text when collapsed, chevron. Expanded section below = full label text, then diffs, then detail.

**Alternatives:** Keep label in header with higher `maxLines` — still clips on very long paths; rejected.

### 2. Rename `open` → `collapsed` with default `false`

**Choice:** Align naming and default with `ThoughtRow` (`collapsed = false` = expanded).

**Remove:** `LaunchedEffect(hasDetail) { open = true }` so user collapse is not overwritten when streaming adds content.

### 3. `clickable` always enabled

**Choice:** Every tool call toggles collapse; expanded-only tools with no diffs still show full label in body.

### 4. Expanded label presentation

**Choice:** `Text(toolLabel(...), softWrap = true)` in body, no `maxLines` cap (or high cap with scroll if combined height is an issue). Optional `heightIn(max = …)` + vertical scroll only if label + diffs exceed ~screen fraction — prefer natural wrap first.

## Risks / Trade-offs

- **[Risk] Taller chat items when many tools expanded** → Same as thoughts; user can collapse rows. No change to LazyColumn structure.
- **[Risk] Duplicate label (preview in header when collapsed vs full in body)** → When expanded, header shows minimal chrome (no repeated full label); only icon + chevron + spinner when running.

## Migration Plan

Single UI release. No data migration.

## Open Questions

_None._
