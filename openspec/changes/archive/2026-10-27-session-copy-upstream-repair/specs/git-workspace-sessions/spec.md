# Spec Delta

## ADDED Requirements

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
