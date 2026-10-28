# Spec Delta

## ADDED Requirements

### Requirement: Project rules entry in session overflow

Agent session chat screens that use the shared session header SHALL expose **Project rules** in the overflow menu when the session has a resolvable coding folder, consistent with other session-scoped controls.

#### Scenario: Overflow shows project rules

- **WHEN** the user opens the overflow menu on a coding or workflow agent session with a bound coding folder
- **THEN** a **Project rules** item SHALL be visible and SHALL open the per-session rule enablement sheet

### Requirement: Prompt prefixes concatenate

The ACP prompt pipeline SHALL include every applicable prefix on a user-initiated send rather than choosing only one prefix source. Automation context, workflow-specific context (Product Manager or PR Reviewer), and the project-rules block MAY all appear on the same turn.

#### Scenario: Automation plus project rules

- **WHEN** an automation session has a linked folder and at least one project rule enabled
- **THEN** the outbound prompt SHALL contain both automation context and the project-rules block

### Requirement: Project rules injection on user turns

When project rules are enabled for an ACP session, the client SHALL include the mandatory project-rules block on every user-initiated turn, including Product Manager and PR Reviewer sessions, without replacing those kinds’ existing injected context.

#### Scenario: PM session with enabled rules

- **WHEN** the user sends a message in a Product Manager session with at least one project rule enabled
- **THEN** the outbound prompt SHALL include both PM-specific injected context and the project-rules block before the user text
