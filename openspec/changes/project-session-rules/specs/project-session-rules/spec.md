# Spec Delta

## ADDED Requirements

### Requirement: Project rule profiles persist outside folder lifecycle

The system SHALL store project rule profiles in app-global storage separate from coding `Folder` records. Each profile SHALL have a stable profile id, a display name, an optional normalized **network** git origin key, and an ordered list of rules. Each rule SHALL have a stable rule id, a title, and a body. Deleting a coding folder, unlinking an automation folder, or deleting a workflow session SHALL NOT delete profiles or rule definitions.

#### Scenario: Delete folder keeps rules

- **WHEN** the user deletes a coding project that had sessions using project rules
- **THEN** the rule profiles and rule bodies SHALL remain available in Settings

#### Scenario: Edit rule body

- **WHEN** the user edits a rule body in Settings and saves
- **THEN** sessions that have that rule enabled SHALL use the updated body on the next user-initiated send

### Requirement: Settings management for profiles and rules

The system SHALL provide a Settings section where the user can create, rename, and delete profiles; add, edit, reorder, and delete rules within a profile; and set or clear an optional git origin matcher by pasting a remote URL or copying origin from an existing coding folder. Stored origin keys SHALL be the normalized network form; empty origin means name-based eligibility only.

#### Scenario: Create profile with rules

- **WHEN** the user creates a profile and adds two rules with titles and bodies
- **THEN** the profile SHALL appear in Settings and both rules SHALL be listed under that profile

#### Scenario: Delete profile

- **WHEN** the user deletes a profile
- **THEN** the profile and its rules SHALL be removed from Settings and SHALL no longer be offered for new enablement; per-session bindings that reference deleted rule ids SHALL be ignored at prompt assembly time

### Requirement: Eligibility uses the session coding folder

The system SHALL resolve eligible profiles from the session’s coding folder: the ACP folder for coding, Product Manager, and PR Reviewer sessions, or the linked coding folder for automation. Matching SHALL only list rules as available and SHALL NOT enable them. Multiple eligible profiles SHALL be shown grouped by profile.

#### Scenario: Re-clone same network origin

- **WHEN** the user clones a repo whose network origin normalizes to a profile’s stored origin key, even if the new folder display name differs
- **THEN** Project rules UI for sessions using that folder SHALL list that profile’s rules as available

#### Scenario: Name match only without origin

- **WHEN** a profile has no origin key and a folder has the same display name (case-insensitive)
- **THEN** that profile SHALL be eligible for sessions bound to that folder

### Requirement: Origin keys are network remotes only

A profile with an origin key SHALL be eligible only when the folder’s golden **network** origin normalizes to that key (host + path, case-insensitive, credentials and trailing `.git` stripped), including GitHub, GitLab, and other `https`/`ssh` remotes. Local filesystem remotes SHALL NOT be identity. A profile that already has an origin key SHALL NOT become eligible solely because display names match.

#### Scenario: Same name different remotes

- **WHEN** a profile’s origin key is `gitlab.com/acme/api` and the folder display name matches the profile but the folder origin is a different network remote
- **THEN** that profile SHALL NOT be eligible for that folder

#### Scenario: Session-copy filesystem origin ignored

- **WHEN** a session git copy’s `origin` is a local path to golden but golden’s network origin matches a profile
- **THEN** eligibility SHALL use the golden network origin, not the local path

### Requirement: Stamp origin on first enable

When the user enables a rule from a profile that has no origin key, and the current session’s coding folder has a network origin, the system SHALL store that normalized origin key on the profile and SHALL NOT overwrite an origin key that is already set.

#### Scenario: Enable then re-clone under a new name

- **WHEN** the user enables a rule on a name-matched origin-less profile whose folder has `https://github.com/acme/app.git`, then later deletes the folder and clones the same URL as a folder named something else
- **THEN** the profile SHALL be eligible via origin match

### Requirement: Per-session rule enablement

The system SHALL persist, per ACP session key, which rule ids are enabled. New sessions SHALL start with no rules enabled. Enabled state SHALL survive app restart and session resume. The user SHALL be able to enable and disable individual rules without deleting them from the profile.

#### Scenario: Toggle rule off

- **WHEN** the user disables a previously enabled rule in the session Project rules sheet
- **THEN** the next user-initiated send SHALL NOT include that rule in the injected block

#### Scenario: Re-enable rule

- **WHEN** the user re-enables a rule in the same session
- **THEN** the next user-initiated send SHALL include that rule again

#### Scenario: New session starts empty

- **WHEN** the user opens a new ACP session for a folder that has eligible profiles
- **THEN** no rules SHALL be enabled until the user toggles them on

### Requirement: Enabled rules survive lost eligibility

Prompt assembly SHALL include a stored enabled rule if that rule id still exists, even if the profile is no longer eligible. The Project rules sheet SHALL list currently eligible rules plus any enabled-but-no-longer-eligible rules so the user can turn them off.

#### Scenario: Enabled after match lost

- **WHEN** a rule is enabled and the folder is later renamed so the profile is no longer eligible
- **THEN** the next send SHALL still include that rule until the user disables it, and the sheet SHALL still show it as enabled

### Requirement: Project rules session chrome

The session header overflow menu SHALL include **Project rules** when the session has a resolvable coding folder (coding session, PM or PR session bound to a folder, or automation session with a linked coding folder). Tapping it SHALL open a sheet of checkboxes grouped by profile. Unlinked automation sessions SHALL omit the menu item.

#### Scenario: Coding session menu

- **WHEN** the user opens overflow on a coding ACP session tied to a folder
- **THEN** **Project rules** SHALL be available

#### Scenario: Automation with linked folder

- **WHEN** the user opens agent chat in an automation session with a linked coding folder
- **THEN** eligible profiles SHALL be resolved from that linked folder, not from the automation project name

#### Scenario: Unlinked automation

- **WHEN** the user opens overflow on an automation session with no linked coding folder
- **THEN** **Project rules** SHALL be absent

#### Scenario: Eligible empty sheet

- **WHEN** the session has a coding folder but no eligible profiles and no leftover enabled rules
- **THEN** the sheet SHALL explain that no project rules match yet, with a pointer to Settings

### Requirement: Mandatory per-turn prompt injection

When a session has one or more existing enabled rules, every user-initiated agent send, including edit-and-resend, SHALL prepend a `termfold-project-rules` block before user text. The block SHALL include a fixed preamble that enabled rules MUST be followed while enabled, and SHALL list only those enabled rules, skipping empty bodies. When no existing rules are enabled, the client SHALL NOT inject the tag.

#### Scenario: Enabled rules on send

- **WHEN** the user sends a message with two rules enabled
- **THEN** the outbound prompt SHALL contain the mandatory preamble and both rule bodies before the user text, and the timeline SHALL show only the user’s message text

#### Scenario: No enabled rules

- **WHEN** the user sends a message with zero enabled rules
- **THEN** the outbound prompt SHALL NOT contain the project-rules tag

### Requirement: User message wins explicit conflicts

If the user’s latest message explicitly conflicts with an enabled project rule, the agent SHALL follow the user message and note which rule is not applied.

#### Scenario: User overrides a rule

- **WHEN** an enabled rule says not to edit tests and the user message says to update the tests
- **THEN** the preamble SHALL still be present and SHALL instruct the agent to follow the user message and note the skipped rule

### Requirement: Project-rules block is wire-only and re-injected

The project-rules block SHALL NOT appear in the chat timeline as a user message. Re-injecting on every send SHALL keep the rules present after context compression.

#### Scenario: After compress

- **WHEN** context compression runs and the user then sends another message with rules still enabled
- **THEN** that outbound prompt SHALL again include the project-rules block

### Requirement: Project-rules prefixes concatenate in a fixed order

The project-rules block SHALL be concatenated with other prefixes, not substituted for them. Outbound order SHALL be: pending wire context if any, then automation prefix if any, then workflow prefix (PM/PR) if any, then the project-rules block if any, then user text.

#### Scenario: Coexist with automation prefix

- **WHEN** the user sends a message in an automation session that also injects automation context and has at least one project rule enabled
- **THEN** the outbound prompt SHALL include both the automation context and the project-rules block

### Requirement: Backup and restore

Project rule profiles and per-session enablement maps SHALL be included in TermFold backup export and restored on import so profiles, rules, and session toggles match the backup on a fresh install.

#### Scenario: Backup round-trip

- **WHEN** the user exports a backup and restores it on a fresh install
- **THEN** profiles, rules, and per-session enabled rule ids SHALL match the pre-backup state
