# git-workspace-sessions Specification

## Purpose
TermFold stores per-session git copies under `.termfold/` inside the project. That directory must not be committed when the repository already tracks ignore rules via `.gitignore`.

## Requirements

### Requirement: Ignore TermFold metadata when gitignore exists

The system SHALL ensure the golden project repository’s `.gitignore` includes an entry for `.termfold/` when that file already exists and does not yet ignore `.termfold/`, without duplicating an existing equivalent entry.

#### Scenario: Golden repo has gitignore

- **WHEN** a session workspace copy is provisioned for a golden project that already has a `.gitignore` file
- **THEN** the golden project `.gitignore` gains a `.termfold/` line if missing

#### Scenario: Already ignored

- **WHEN** `.gitignore` already contains `.termfold` or `.termfold/`
- **THEN** the system does not add a duplicate line

#### Scenario: No gitignore file

- **WHEN** the golden project has no `.gitignore` file
- **THEN** the system does not create a new `.gitignore` solely for this rule

### Requirement: Session copy gitignore updated after clone

The system SHALL apply the same `.termfold/` ignore rule to the session copy’s `.gitignore` after provisioning when that copy’s `.gitignore` file exists.

#### Scenario: Copy inherits gitignore

- **WHEN** a provisioned session copy includes a `.gitignore` from the cloned branch
- **THEN** `.termfold/` is appended if not already present

### Requirement: Session copy upstream matches a fetchable remote branch

The system SHALL, after provisioning a managed session workspace copy and after switching its checked-out branch, configure upstream tracking for that branch only when `origin/<branch>` exists following `git fetch origin --prune` in the copy.

#### Scenario: Branch exists on origin

- **WHEN** the session copy is on branch `feature` and `refs/remotes/origin/feature` exists after fetch
- **THEN** the copy’s branch `feature` tracks `origin/feature`

#### Scenario: Local-only branch on golden

- **WHEN** the session copy is on branch `main-dev`, golden had upstream set to `origin/main-dev`, but `refs/remotes/origin/main-dev` does not exist after fetch in the copy
- **THEN** the copy has no upstream configured for `main-dev` and `git pull` does not claim a missing remote ref

#### Scenario: Existing copy opened later

- **WHEN** the user opens a session copy that was provisioned before this behavior and upstream points at a ref that fetch cannot resolve
- **THEN** the system repairs upstream on first git workspace operation in that copy (status, branch switch, or changes load) using the same rules as provisioning

### Requirement: Golden workspace upstream is unchanged

The system SHALL NOT modify branch upstream configuration in the golden project tree when repairing session copies.

#### Scenario: Provision session copy

- **WHEN** a new session copy is created from golden on a local-only branch
- **THEN** golden’s `branch.*.merge` and remote settings remain as they were before provisioning

### Requirement: Session copies stored outside golden project tree

The system SHALL provision new per-session git workspace copies outside the golden project’s directory tree so commands run from the golden root do not traverse session clones.

#### Scenario: New session workspace path

- **WHEN** the user creates a new session workspace for a golden project at guest path `/root/projects/my-app`
- **THEN** the session copy’s guest path is not under `/root/projects/my-app/`

#### Scenario: Golden tree isolation

- **WHEN** a developer runs a recursive tool from the golden project root (for example `npm run lint` or `eslint .`)
- **THEN** that tool does not include source files from session workspace copies provisioned after this behavior ships

### Requirement: Stable external session root

The system SHALL store session workspaces under a TermFold-managed guest directory using a stable per-golden-project key derived from the golden guest path so paths are reproducible across app restarts.

#### Scenario: Same golden path same key

- **WHEN** two session workspaces are provisioned for the same golden guest path at different times
- **THEN** both session directories share the same parent key directory under the external session root

#### Scenario: Distinct golden paths

- **WHEN** session workspaces are provisioned for two different golden guest paths
- **THEN** their session directories do not share the same parent key directory unless those golden paths map to the same key

### Requirement: Legacy in-repo session paths

The system SHALL continue to recognize and operate on workspace records whose guest paths remain under `<golden>/.termfold/sessions/` from prior TermFold versions.

#### Scenario: Open existing in-repo session

- **WHEN** a workspace record points to `<golden>/.termfold/sessions/<id>` and that directory exists on disk
- **THEN** the app treats it as a managed session workspace for that golden project

#### Scenario: Delete legacy session

- **WHEN** the user deletes a legacy in-repo session workspace
- **THEN** the system removes that session directory and updates stored workspace metadata the same as for external sessions

### Requirement: Optional migration of legacy session directories

The system MAY move an on-disk session tree from `<golden>/.termfold/sessions/<id>` to the external session root when opening or provisioning, and SHALL update the stored guest path when migration succeeds.

#### Scenario: Successful migration

- **WHEN** migration runs for a legacy session whose directory exists only under the golden tree
- **THEN** the session data lives under the external session root and the workspace record guest path matches the new location

#### Scenario: Migration skipped on failure

- **WHEN** migration cannot complete (for example insufficient space or move error)
- **THEN** the legacy path remains in use and the session remains usable
