# Spec Delta

## ADDED Requirements

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
