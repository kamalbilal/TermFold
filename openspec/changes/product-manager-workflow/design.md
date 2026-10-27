# Design

## Context

TermFold Workflows already ships **Automation** (linked read-only folder + session docs + browser), **PR Reviewer** (paste URL → temp copy → ACP chat → Done deletes copy), and generic **Cursor** coding sessions on branch copies. PM work needs a third lane that combines **golden-linked discovery** (no checkout) with **release planning on isolated copies**, plus **optional competitive web research** with strict citations. See `proposal.md` for motivation.

Existing building blocks: `GitWorkspace.provisionWorkspace`, `BranchPickerDialog`, `BrowserBridge` / Gecko preview, `AcpClient` prompt assembly, `Folder` / `WorkspaceStore`, PR review session store pattern.

## Goals / Non-Goals

**Goals:**

- Two PM modes with clear lifecycle and UI badges (Discovery vs Release).
- Discovery reads git state at `viewBranch` without mutating golden (`git show`, `git log`, `git diff` at refs).
- Release uses one active temp copy per session; Done deletes copy, retains chat.
- Research pipeline: repo pack → optional competitive pack → decision memo with evidence tags.
- Read-only branch picker reuses branch list UI; no stash/checkout on golden for discovery.

**Non-Goals:**

- Auto-editing repo files from PM sessions (v1).
- Unbounded web crawling or login-gated competitor dashboards.
- Cloud sync of PM sessions; scheduled headless PM runs.
- Replacing OpenSpec CLI—PM may suggest proposals; user runs `/opsx-propose` manually.

## Decisions

### 1. Session model (`PmSessionStore`)

**Choice:** New store parallel to `PrReviewSession` / automation session records.

Fields (conceptual): `id`, `folderId`, `goldenGuestPath`, `mode` (`DISCOVERY` | `RELEASE`), `viewBranch` (discovery), `workspaceId` + `guestPath` (release copy), `branch` (release), `competitors[]` (name + homepage URL, extra URLs), `productOneLiner`, `includeCompetitiveResearch`, `releaseLabel` / optional `baseRef` (v1.1), timestamps.

Default `viewBranch` on new Discovery: golden `HEAD`’s branch name (`rev-parse --abbrev-ref HEAD`), fallback `origin/HEAD` target. Picker can change it later without checkout.

**Alternatives:** Reuse automation projects — rejected; PM semantics and mode split differ.

### 2. Discovery git reads (no checkout)

**Choice:** `PmRepoResearch` service using `GitWorkspace.runGit` with:

- `git rev-parse --verify viewBranch`
- `git show viewBranch:path` for file bodies
- `git log viewBranch -n N`, `git diff base..viewBranch` when user supplies range (release mode emphasis)

Never run `git checkout` on golden guest path from PM discovery flows.

**Alternatives:** Sparse checkout on golden — rejected (index/write risk, user saw `could not write index` on checkout).

### 3. Read-only branch picker

**Choice:** Extend branch picker with `readOnly: Boolean`. On select: persist `viewBranch` on session + refresh research cache; show header chip **Viewing: branch**.

**Alternatives:** Separate screen — rejected; reuse list/load/cache from `BranchListCache`.

### 4. Release copy lifecycle

**Choice:** Mirror PR Reviewer: `provisionWorkspace` on selected branch; single active copy per PM release session; **Done** with dirty confirm → delete copy, keep ACP timeline.

Branch switch on same session: v1 reuse copy + `checkoutBranch` on copy (with existing index-lock hardening).

### 5. Research orchestration

**Choice:** Explicit **Research & decide** action (or first user message) triggers:

1. **Repo pack** — bounded file list (README, docs/, openspec/specs, key app dirs), capped bytes; facts as `{claim, source: path@ref}`.
2. **Competitive pack** (if enabled + competitors non-empty) — per competitor: homepage, pricing, docs/changelog URLs only (depth cap); facts as `{claim, source: url, fetchedAt}`.
3. **Synthesis** — template sections: Landscape matrix, Recommendation, Options, In/Out scope, How/phases, Risks, Assumptions, Open questions.

Follow-up messages use cached pack unless user taps **Refresh research**.

**Alternatives:** Research every message — rejected (cost/latency on device).

### 6. Competitive fetch

**Choice:** Reuse Gecko headless or automation-style fetch with allowlist = competitor registrable domains + user-added URLs; store fetch log in session for UI collapse.

No generic Google search in v1.

**Alternatives:** External API (Crunchbase) — rejected for v1 scope.

### 7. Per-session isolated memory DB

**Choice:** One private store **per PM session id**, physically separate from Automation’s `automation_memory` BoxStore and from other PM sessions. Reuse on-device embeddings / chunk search **patterns** from automation memory, not the same database file.

Contents:

- **Chunks** the agent writes: finding, decision, competitor note, open question, assumption (title, body, optional source tags, timestamps).
- **System records:** after each research pass, persist pack summary + decision memo as chunks (`kind=research_pack` / `kind=decision`) so recall does not depend on the agent remembering to write.
- **Search:** vector similarity **scoped to that session id only**. No cross-session, no global PM knowledge base in v1.
- **Prompt inject:** bounded top-K chunks + last decision headline (cap tokens). Full logs never dumped.
- **Tools:** `pm_memory_search`, `pm_memory_write`, `pm_memory_get(id)` — session-scoped; write rejects other session ids.
- **Lifecycle:** deleting the PM session deletes its DB. **Done** on Release deletes the git copy only; memory + chat remain. Backup/restore includes these DBs with TermFold backup when that path already covers automation DBs.
- **User UI:** Reuse the Automation memory-manager **pattern** (`AutomationMemoryManagerDialog` session-private mode), not the same BoxStore. PM chat header overflow (⋮) includes **Session memory** → full-screen dialog: search box, kind filter (finding, decision, competitor, research_pack, note, …), optional semantic search, chunk list, tap to read, add/edit, delete with confirm. No “Site navigation memory” row for PM. No run-log tab unless we later add research-event log (v1: chunks only).

**Alternatives:** One shared PM DB with sessionId column in a global store — rejected; easier to leak across projects. Chat timeline as sole memory — rejected; compression and long threads drop facts. Settings-only memory UI — rejected; user asked for in-session header access like Automation.

### 8. ACP integration

**Choice:** Reuse **Cursor ACP** (no new agent binary) with a PM session profile:

- System instructions: cite or gap; no unsourced market claims; persist material findings via memory tools.
- Tools: git-at-ref read/log/diff; optional bounded fetch; **file write/edit tools denied on Discovery golden**; memory search/write/get; **no** generic `read_file` of working tree that would ignore `viewBranch` (use ref-aware reads).
- Per-turn inject: session markdown docs + last research pack summary + top memory chunks (size cap).
- After Done: ACP cwd is a **stub guest dir** (PR Reviewer `ensureStub` pattern), not the deleted copy.

**Alternatives:** Separate non-ACP LLM — rejected; reuse timeline, settings, idle disconnect.

### 9. UI placement

**Choice:** `WorkflowsSection.PRODUCT_MANAGER` → list PM sessions + **New** → mode picker → folder → (discovery: read-only branch) / (release: branch + provision) → chat screen with research log + decision cards + header **Session memory** menu.

## Risks / Trade-offs

- **[Risk] Large repos slow research** → Path caps, progressive UI, “focus path” optional field.
- **[Risk] Web fetch blocked/CAPTCHA** → Mark competitive pass incomplete; decision repo-only sections still valid.
- **[Risk] Wrong competitor entity** → User-confirmed competitor list; suggestions require confirm.
- **[Risk] viewBranch omits local-only untracked files** → Banner: view is git state at branch tip.
- **[Trade-off] No auto-implement** → User switches to Cursor for execution; optional “Copy epic to new Cursor session” later.
- **[Risk] Memory vs chat desync** → System auto-writes research/decision chunks; agent writes are extra, not the only record.
- **[Risk] Files tree is working tree, research is viewBranch** → Discovery Files header warns, or hide Files in v1 if cheaper.

## Migration Plan

Greenfield feature; no data migration. Ship behind Workflows section entry; no change to existing Cursor/Automation/PR sessions.

## Open Questions

- v1.1 git range for release (“since tag vX”) — defer; release v1 uses branch tip only.
- Whether Discovery Files is warning-only vs hidden in v1 (recommend: warning + no PM write authorization).
