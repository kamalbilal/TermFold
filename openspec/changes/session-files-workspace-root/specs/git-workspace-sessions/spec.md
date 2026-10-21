# Spec Delta

## Purpose

TermFold stores per-session git copies under `.termfold/` inside the project. That directory must not be committed when the repository already tracks ignore rules via `.gitignore`.

## ADDED Requirements

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
