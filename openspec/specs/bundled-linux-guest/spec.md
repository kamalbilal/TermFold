# bundled-linux-guest Specification

## Purpose

Improve throughput of everyday guest workloads (Node, npm, lint, agents) by giving every PRoot session consistent cache paths, a dedicated scratch tree, and built-in diagnostics—without changing how projects are stored or exposing the host GPU to Debian.

## Requirements

### Requirement: Guest sessions expose Node and npm cache environment

The system SHALL set guest environment variables on every PRoot session so Node and npm use predictable cache locations under TermFold-managed scratch, without requiring user shell profile edits.

#### Scenario: New shell session

- **WHEN** the user opens a terminal or the app starts a one-shot guest command
- **THEN** the guest environment includes `NODE_COMPILE_CACHE` and npm cache configuration pointing at directories under the TermFold scratch tree

#### Scenario: User override

- **WHEN** a session passes explicit environment overrides for those variables
- **THEN** the override values take precedence over the defaults

### Requirement: Guest /tmp uses TermFold scratch

The system SHALL route guest temporary files through a TermFold-managed scratch directory that is created before PRoot starts, so `/tmp` in the guest does not rely on the rootfs image alone.

#### Scenario: Package manager or tool writes temp files

- **WHEN** a guest program writes to `/tmp` or honors `TMPDIR`
- **THEN** files are created under the TermFold scratch tree bind-mounted or equivalent for that session

### Requirement: termfold-bench diagnostic command

The system SHALL provide an executable guest command `termfold-bench` that prints a short human-readable report: CPU count, available memory summary, GuestCompat migration status, and timed micro-benchmarks for Node process startup and a small file-read loop.

#### Scenario: User runs bench after setup

- **WHEN** the user runs `termfold-bench` in a provisioned guest
- **THEN** the command exits zero and prints timing lines suitable for comparing devices or before/after app updates

#### Scenario: Bench on unmigrated rootfs

- **WHEN** GuestCompat migration has not completed
- **THEN** the report clearly states that legacy link emulation may still be active

### Requirement: Document phone-oriented lint and install practices

The system SHALL document that ESLint caching and separating type-aware lint from daily lint improve repeated `npm run lint` on device, in material shipped with the app (setup banner or bundled agent skill), without modifying user project repositories.

#### Scenario: First-time setup completes

- **WHEN** `termfold-setup` finishes successfully
- **THEN** the user sees a brief pointer to phone performance tips including ESLint `--cache`

#### Scenario: Agent reads bundled skill

- **WHEN** an agent loads the TermFold browser/shell skill from app assets
- **THEN** the skill includes guidance for faster lint on phone (cache, scope, concurrency)

### Requirement: Scratch directories are provisioned safely

The system SHALL create and maintain the scratch subtree during rootfs provisioning and before starting PRoot, and SHALL NOT place scratch on shared Android storage that breaks Linux semantics.

#### Scenario: Cold app start

- **WHEN** the app prepares the Linux environment before the first terminal session
- **THEN** required scratch directories exist and are writable by the guest root user
