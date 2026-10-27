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

### Requirement: Product Manager agent skill

The system SHALL ship a bundled agent skill that documents the Product Manager workflow: discovery vs release modes, read-only view branch, research and refresh controls, competitor list and bounded web fetch, citation rules (repo, web, brief, gap), decision memo structure, and session-scoped memory tools for findings and prior answers.

#### Scenario: Agent loads PM skill

- **WHEN** a Product Manager session starts an ACP agent with access to bundled skills
- **THEN** the skill text describes how to run repo-at-ref reads and competitive fetches without writing to golden

#### Scenario: Citation rule in skill

- **WHEN** the agent prepares a market or competitor claim
- **THEN** the skill instructs the agent to cite a URL from the research log or mark the claim as a gap

### Requirement: Skill documents session network rules

The system SHALL document in bundled `termfold-browser` skill and MCP tool text how agents block and override responses with URL patterns, that rules are per bound session, and that they must use those tools rather than page `fetch` patches.

#### Scenario: Agent reads skill

- **WHEN** an agent loads the bundled TermFold browser skill
- **THEN** the skill describes block and override-body tools, URL wildcards, session isolation, and reading the network log to pick patterns

### Requirement: Browser skill documents long automation token playbook

The system SHALL ship bundled agent skill material that documents token-efficient long browser automations: prefer `wait` and incremental snapshot modes, use `outline` and `console --errors` before full snapshots, use `network --failed`, avoid repeated screenshots unless `if_changed`, and read full snapshot files from disk when grep is needed.

#### Scenario: Skill lists token order

- **WHEN** an agent reads the TermFold browser skill from app assets
- **THEN** the material SHALL describe a recommended verification order that minimizes MCP payload size

#### Scenario: Setup mentions efficiency

- **WHEN** `termfold-setup` completes successfully
- **THEN** linked tips SHALL mention snapshot `--changed` and wait tools for SPA workflows

### Requirement: Browser skill documents background preview expectations

The system SHALL document that agent browser tools MAY run while the preview UI is hidden when a session is bound, and that the user may need to keep the agent run active per foreground notification policy.

#### Scenario: Background automation documented

- **WHEN** an agent reads automation or browser skill material
- **THEN** it SHALL state that opening the preview bubble is not required for every browser command during an active bound session
