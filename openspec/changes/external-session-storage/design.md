# Design

## Context

See `proposal.md` (Why). Today `ProjectWorkspaces.SESSIONS_DIR` is `.termfold/sessions` relative to the golden guest path (`sessionGuestPath` = `$golden/.termfold/sessions/$id`). Provisioning clones into that tree (`GitWorkspace`). `TermfoldGitignore` adds `.termfold/` to gitignore but many tools (ESLint, custom npm scripts) do not honor gitignore when scanning `.`.

Golden projects live under `/root/projects/<name>` per `Projects.GUEST_DIR`. TermFold already uses `/root/.termfold` for guest-side metadata (see setup scripts).

## Goals / Non-Goals

**Goals:**

- New session disks live under `/root/.termfold/project-sessions/<projectKey>/<sessionId>` (exact constant names in code).
- `projectKey` is stable for a given golden guest path (recommend: URL-safe encoding or hash of normalized path, e.g. SHA-256 hex of `/root/projects/foo` with consistent trimming).
- `isManagedSessionPath`, delete, and provision use the new resolver; legacy paths still match the old prefix rule.
- Unit tests cover path helpers and legacy vs external detection.
- Update main spec **Purpose** at archive time to describe external storage (delta does not carry Purpose for existing capability).

**Non-Goals:**

- Changing where the **golden** project lives (`~/projects`).
- Patching every third-party tool ignore file (`.eslintignore`, etc.) — out-of-tree storage is the fix.
- Migrating or deleting old `.termfold/sessions` trees automatically on app upgrade unless we implement the optional migration requirement (best-effort on session open).
- Symlinking from golden repo into sessions (would not fix traversal).

## Decisions

### 1. External root location

**Choice:** `/root/.termfold/project-sessions/<projectKey>/<sessionId>`

**Rationale:** Keeps all TermFold guest state under `~/.termfold`, outside importable project trees. Session cwd in terminals remains a normal absolute guest path.

**Alternatives:**

- Keep `.termfold/sessions` under golden but add ignore files — incomplete for arbitrary commands.
- `/tmp/termfold-sessions` — lost on guest wipe without backup; worse for long-lived sessions.

### 2. Project key algorithm

**Choice:** `projectKey = sha256Hex(normalize(goldenGuestPath))` where normalize trims trailing slashes and uses the guest path string (not host path).

**Rationale:** Stable across Android host path differences; no illegal filename characters.

**Alternative:** Last segment only (`my-app`) — collides if two golden paths differ only by parent (unlikely today but fragile).

### 3. Legacy compatibility

**Choice:** `isManagedSessionPath` returns true if guest path is under external root for that golden **or** under legacy `$golden/.termfold/sessions/`.

**Rationale:** Existing `WorkspaceRecord` JSON on device keeps working without forced migration.

### 4. Migration

**Choice:** Best-effort `rename`/`move` of host directory when loading a legacy record (IO dispatcher); on failure, keep legacy path. New provisions always use external root only.

**Rationale:** Clears duplicate trees over time without blocking session open.

### 5. Gitignore behavior

**Choice:** Unchanged — still call `TermfoldGitignore.ensureTermfoldIgnored` on golden and session copy after provision. Legacy folders may leave empty `.termfold` under golden; user can delete manually.

## Risks / Trade-offs

- **[Disk layout change]** → Document in release notes; old paths remain until migrated/deleted.
- **[Backup/restore]** → Workspace records store absolute guest paths; backup must include `/root/.termfold/project-sessions` (note for TermFold backup feature if separate).
- **[Clone script]** → `git clone` target path changes only for final move destination; `/tmp/termfold-ws-*` staging unchanged.
- **[Tests assuming `.termfold/sessions` path]** → Update `ProjectWorkspacesTest`, `WorkspaceRecordTest`, any hard-coded strings.

## Migration Plan

1. Ship path resolver + provision/delete updates.
2. On `WorkspaceStore.get` or session screen open, if path matches legacy pattern and external path missing, attempt host move + update record.
3. No rollback of moved data automatically; legacy path left intact if move fails.

## Open Questions

- Whether TermFold full backup should explicitly bundle `project-sessions` (follow-up if backup omits `.termfold` today).
- Whether to surface UI to “clean up legacy `.termfold/sessions`” after migration (nice-to-have, not required for isolation).
