# Design

## Context

See `proposal.md` for motivation. Today `AcpClient.toolContentText` maps `type: diff` to `"Edited " + path` and `AcpItem.ToolCall` only stores `detail: String`. `ToolCallRow` renders that string inside a nested `verticalScroll` capped at 280dp, which composes all lines at once. Git workspace changes already implement `parseUnifiedDiff`, `prepareDiffPresentation`, `GitDiffDisplayRow`, context folding, and IO-thread preparation in `GitChangesPane`.

ACP v2 defines authoritative `changes[]` plus optional `patch: { format: git_patch, text }`. v1 uses `path`, `oldText`, `newText`. TermFold must normalize both into unified diff text, then reuse the git diff presentation stack.

## Goals / Non-Goals

**Goals:**

- Structured ingest, persistence, and UI for per-file tool-call diffs.
- Synthetic unified diff for v1 (and v2 without patch) using line-based diff (Myers/LCS on lines).
- Per-file default-collapsed headers with `+`/`-` stats; tool call itself remains expand/collapse as today.
- Session-scoped presentation cache; debounced refresh while `in_progress` when live diffs enabled.
- `AcpChatPrefs.showLiveToolDiffs` (default `true`) on Settings under a new **Agent chat** group (applies to all ACP agents, not only Cursor).

**Non-Goals:**

- Side-by-side diff mode in chat (unified only).
- Flattening diff lines into top-level chat `LazyColumn` items (only if profiling proves inner lazy insufficient).
- Re-fetching diffs from the agent on replay; local timeline is source of truth.
- Changing git workspace diff behavior.

## Decisions

### 1. Model: structured parts on `ToolCall`

Add something like `fileDiffs: List<AcpToolFileDiff>` on `AcpItem.ToolCall`, each holding:

- `path` (absolute as received)
- `operation` (`add` | `delete` | `modify` | `move` | `copy` | `unknown`)
- `patchText: String?` (unified diff for that file or multi-file section)
- Optional `plainDetail` remains for non-diff `content` merged into `detail` for backward compatibility and mixed tool output.

**Alternative:** Only store raw JSON blob — rejected; harder to test and render.

Ingest walks `content[]` on `tool_call` / `tool_call_update`:

- `diff` v2: read `changes[]` for paths/operations; use `patch.text` when `format == git_patch`, split multi-file patches by `diff --git` boundaries when multiple files share one patch object.
- `diff` v1: `synthesizeUnifiedDiff(path, oldText, newText)` → single-file patch text.
- `content` / `text`: append to `detail` as today.

Update `toolPaths()` to collect paths from `changes[].path` and `changes[].oldPath` for v2 entries.

### 2. Synthetic unified diff

New function in `gitworkspace` (e.g. `SyntheticUnifiedDiff.kt`):

- **Add:** `--- /dev/null`, `+++ <path>`, all `+` lines.
- **Delete:** `--- <path>`, `+++ /dev/null`, all `-` lines.
- **Modify:** line-based LCS/Myers; emit standard `@@` hunks with reasonable context (3 lines) so `parseUnifiedDiff` produces familiar rows.

Cap input size (e.g. same order of magnitude as git diff load limits); truncate with a marker line in fallback text if over cap.

Unit tests cover add/delete/modify, empty files, and multiline Unicode.

### 3. Presentation cache (performance)

- Key: `"${toolCallId}:$fileIndex"` or stable path key within session.
- On model update, `AcpClient` or a small `ToolCallDiffCache` in the session scope launches `prepareDiffPresentation(patchText)` on `Dispatchers.Default`.
- Store `CachedDiffPresentation` + precomputed `added`/`removed` counts for headers.
- Debounce: while `status == in_progress` and live diffs on, coalesce updates (~100ms) before replacing cache entries.

**UI (`ToolCallRow`):**

- Remove outer `verticalScroll` for diff bodies.
- For each expanded file, `LazyColumn` with `Modifier.heightIn(max = …)` inside the tool-call column (pattern from `GitChangesPane` file cards).
- Default: file sections collapsed → zero diff rows composed.
- Reuse `GitDiffDisplayRow`, `gitDiffLineTextStyle()`, `applyContextFolding` with per-file fold keys.

Read `AcpChatPrefs.showLiveToolDiffs(context)` once per `AgentSessionScreen` composition; refresh when returning from Settings (`LifecycleResume` or pass callback).

Live diffs **off** + `in_progress`: show tool header and per-file summary rows (path + counts) without inner lazy hunk list; enable hunks after terminal status.

### 4. Persistence (`AcpTimelineCodec`)

Encode `fileDiffs` as JSON array on tool items (`operation`, `path`, `patchText`, optional `oldPath`). Keep `detail` for legacy clients and plain text.

Decode: if `fileDiffs` missing, fall back to `detail` only.

### 5. Settings

`AcpChatPrefs` in `acp` package, SharedPreferences file `acp_chat`, key `show_live_tool_diffs`, default `true`.

`SettingsScreen`: new group `settings_agent_chat` with `SwitchRow` mirroring `CursorAcpCard` pattern.

## Risks / Trade-offs

- **[Risk] Nested LazyColumn jank** → Mitigation: bounded height, collapsed-by-default files, debounced updates; escalate to flattening chat items only if needed.
- **[Risk] Large patches memory** → Mitigation: size cap on stored `patchText`; context folding in UI.
- **[Risk] Agent sends inconsistent v2 patch vs changes** → Mitigation: trust `changes` for labels/counts; render `patch.text` when present.
- **[Risk] Old sessions show no hunks** → Mitigation: documented; legacy `detail` still visible.

## Migration Plan

Ship in one release. No server migration. Users on old timelines see previous `"Edited …"` text until new sessions are recorded.

## Open Questions

_None — live-diff OFF during `in_progress` shows summary only (path + counts), full hunks after completion._
