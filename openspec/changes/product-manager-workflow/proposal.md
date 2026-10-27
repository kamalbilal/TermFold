# Proposal

## Why

TermFold already supports coding agents (Cursor), browser automation, and PR review workflows, but there is no dedicated lane for **product discovery and release planning**: evidence-backed recommendations on what to build, how to ship, and how the product compares to competitors—without treating the coding agent as a substitute PM. Users need a senior-PM-style guide that researches the linked repo (and optional competitors on the public web) before answering, cites sources, and produces a clear decision—not repeated clarifying questions or unsupported guesses.

## What Changes

- Add a **Product Manager** section on the Workflows home list (alongside Automation, PR Reviewer, and More).
- Support two session modes bound to a coding **Folder**:
  - **Discovery** — linked **golden** project; **read-only branch picker** sets a `viewBranch` ref for research (`git show` / `git log` at ref) without `git checkout` on the golden tree.
  - **Release planning** — provision a **temp session copy** on a chosen branch (reuse `GitWorkspace` / PR Reviewer patterns); real checkout on the copy only.
- PM sessions use ACP chat with a distinct persona: **research-first** (repo pack + optional competitive web pass), **citation-required** claims, structured outputs (landscape matrix, decision memo), and session docs (`vision`, `constraints`, `competitors`, release brief) injected each turn.
- Competitive research: user-maintained competitor list (**name + homepage URL required** for a web pass; optional extra URLs); bounded public URL fetch via preview/browser tooling; decisions must label evidence as repo, web URL, brief, or explicit gap.
- Each PM session owns an **isolated private memory database** (not shared with other PM sessions, Automation, or coding chats). The agent SHALL read/write findings, answers, and research notes through tools; the system also records completed research packs and decision memos. Later turns retrieve this store so the PM knows what it already concluded.
- The same store SHALL be visible in the PM chat **header overflow menu** (same pattern as Automation “Session memory”): a full-screen manager where the user can **search, filter by kind, browse, open, add, edit, and delete** chunks. No global/shared PM memory in v1 (unlike Automation’s site-nav DB).
- Release sessions expose **Done** to delete the temp copy while keeping chat **and** the session memory DB (PR Reviewer chat lifecycle + memory survival).
- v1 does **not** auto-implement code, auto-archive OpenSpec, or push git; handoff to Cursor is optional follow-up.

## Capabilities

### New Capabilities

- `product-manager`: Workflows PM section, session store (mode, folder, viewBranch, copy lifecycle), read-only branch picker for discovery, release copy provisioning, competitive research policy, research log, decision-memo templates, and **per-session isolated memory DB** with agent search/write tools.

### Modified Capabilities

- `acp-session-chat`: PM session kind (Cursor ACP + PM prompt, not a new agent binary), per-turn injection (session docs + research pack summary + top memory chunks), structured decision cards, write-blocked tools on golden Discovery, and PM memory tools.
- `bundled-linux-guest`: Bundled skill documenting PM research tools, competitive fetch bounds, and citation rules for agents.
- `workspace-files`: While a PM discovery session is active, Files MAY root at the golden folder with UI indicating read-only **view branch** (no write tools on golden from PM lane).

## Impact

- Workflows UI (`WorkflowsSection`, `AutomationScreens.kt`, `MainActivity` destinations)
- New PM session store (parallel to PR review / automation patterns)
- `GitWorkspace` helpers for git-at-ref reads (discovery) and existing provision/checkout (release)
- `BranchPickerDialog` read-only mode for discovery `viewBranch`
- ACP prompt assembly, session type, optional browser/fetch integration for competitors
- Strings, tests: mode selection, viewBranch without checkout, release Done lifecycle, competitive citation labels, session-scoped memory isolation
- New PM memory store (ObjectBox or equivalent, **not** `automation_memory`)
