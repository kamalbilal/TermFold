# ACP Cursor workspaces — throughput checkpoint

Implementation-phase companion to **`acp-cursor-workspaces.md`**. Copy these four items into the task tracker when coding starts.

## 1. Blocking first steps

- Land **`WorkspaceRecord` + `.termfold/sessions/` path contract** and **`provisionWorkspace`** with tests for clean tree + `node_modules` before any Compose UI beyond stubs.
- **Gate:** guest script or unit test proves golden dirty file does not appear in provisioned dir.

## 2. Independent workstreams

| Stream | Owner focus | Depends on |
|--------|-------------|------------|
| A | `GitWorkspace` guest shell + progress callbacks | Phase 1 paths |
| B | New workspace dialog (recent instances + branch pick) | A API surface |
| C | Bottom bar branch chip + modals | A git checkout |
| D | `AcpClient` / navigation wiring | A + chosen workspace id in nav args |

Streams B and C can proceed in parallel once A exposes suspend functions and fake progress in tests.

## 3. Shared mutable state

- **Filesystem:** one provision job per destination path; mutex if user double-taps Create; cancel deletes partial dir under `.termfold/sessions/` only.
- **Golden project:** read-only during copy; no `git` commands that modify golden working tree during provision.
- **UI:** `StateFlow<ProvisionProgress>` (bytes + percent); no shared mutable vars across composables.

## 4. Smallest safe decomposition

Single module **`com.termfold.app.gitworkspace`** (name TBD) holds:

- Branch list / checkout
- Workspace provision
- Byte progress scanner

UI layers call it; they do not invoke `git` or raw copy themselves. Keeps PRoot/shell quirks in one place per **Boundary Discipline**.

---

## Suggested commit sequence

1. `docs: plan ACP cursor isolated workspaces` (this plan set)
2. `feat(git): workspace paths and persistence`
3. `feat(git): provision workspace from branch with progress`
4. `feat(ui): new workspace dialog`
5. `feat(ui): branch chip on ACP input bar`
6. `feat(acp): bind session to workspace guest path`
7. `test: workspace isolation and branch checkout`

Each commit should compile and pass unit tests added in that commit.
