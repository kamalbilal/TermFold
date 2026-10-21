# Design

## Context

See `proposal.md`. v1 implementation shipped a single-column list with full-screen unified diff on tap. User feedback: need **sidebar file list with status colors** and **collapsible per-file diff cards** with **GitHub-style split diff** (green add / red delete line backgrounds).

Git data layer (`GitWorkspace.loadChangeSummary`, `loadUnifiedDiff`) remains; this is a **UI and diff-rendering** pass.

## Goals / Non-Goals

**Goals:**

- **Sidebar** (left): Staged Changes + Changes sections; each row shows basename, optional parent path, status letter/chip with color (added=green, deleted=red, modified=amber, renamed/copied=dim accent).
- **Main** (right): Scrollable **collapsible cards** per file in the selected section (or all files with sidebar selection highlighting one). Header = filename + +/- counts + chevron; expanded body = diff viewer.
- **Diff viewer**: Parse unified diff into hunks; render **side-by-side** (old | new) on wide enough width; fall back to unified single column below ~600dp if needed. Line-level backgrounds: remove = reddish (`#3e1a1a` family on dark bg), add = greenish (`#1b2e1b` family), context = default surface. Gutter line numbers both sides. `@@` hunk bar subdued (blue-grey band).
- Selection: clicking sidebar row scrolls to / expands that file's card and loads diff if not cached.
- Keep sync badge, refresh, close, guest git only, lazy diff load, 512KB cap.

**Non-Goals:**

- Stage / commit / discard actions (still read-only).
- Full GitHub PR chrome (viewed checkbox, comment threads).
- Syntax highlighting inside diff lines (v2); monospace + background color is enough for v2 UI pass.

## Decisions

### 1. Layout breakpoints

- **Wide** (`WindowWidth.isWide` or pane width > 560dp): `Row` with sidebar `weight(0.32f)` + main `weight(0.68f)`.
- **Narrow**: `Column` — sidebar list full width; on row tap show main diff with back affordance (or overlay slide). Reuse same card component.

### 2. Diff pipeline

1. `loadUnifiedDiff` returns unified text (existing).
2. New `UnifiedDiffParser` → `List<DiffHunk>` with `DiffLine(kind: CONTEXT|ADD|REMOVE|HEADER, oldNo, newNo, text)`.
3. `SideBySideDiffRow` pairs REMOVE/ADD with blank padding on opposite side (standard algorithm).
4. Compose: `LazyColumn` of hunk rows inside expanded card.

No WebView, no diff2html (keeps APK small, matches TermFold Compose stack).

### 3. Collapsible cards

- `remember { mutableStateMapOf<String, Boolean> }` keyed by `staged:path`.
- Default: expand first file in list; collapsing others optional.
- Sidebar selection sets `expanded[path]=true` and scrolls main list to card.

### 4. Colors

Add to `Palette` or local `GitDiffColors` object (dark theme only for now):

- `diffAddBg`, `diffAddFg`, `diffRemoveBg`, `diffRemoveFg`, `diffHunkHeader`, `diffGutter`

Align with user reference screenshots (GitHub dark PR diff).

### 5. Status row colors (sidebar)

| Status | Sidebar accent |
|--------|----------------|
| ADDED / UNTRACKED | Green (`Palette.Accent` or dedicated) |
| DELETED | `Palette.Pink` |
| MODIFIED | Amber (`#E5A50A` approx) |
| RENAMED / COPIED | TextDim |

## Risks / Trade-offs

- **Side-by-side on phone** → Fall back to unified column; sidebar still colored.
- **Large diffs** → Truncate with message; collapse by default for files over N lines.
- **Parser edge cases** (binary, no newline) → Show placeholder string in card body.

## Migration Plan

Replace `GitChangesPane` internals; no user data migration.

## Open Questions

None.
