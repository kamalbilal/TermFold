# Proposal

## Why

Coding and workflow agent sessions share ACP chat, but project conventions live in the user’s head. Users need durable project-scoped rules they can write once, keep after deleting a project, re-attach after re-clone, enable per session, and inject as **mandatory** context on every turn.

## What Changes

- **Settings — project rule profiles**: Create and edit profiles (display name, optional remote-origin matcher, ordered rules with title + body). Stored **outside** `FolderStore` so deleting a coding project does not delete rules.
- **Re-attach after re-clone**: A session’s coding folder (or automation **linked** folder) is eligible for a profile when (a) the folder’s **network git origin** matches the profile’s stored origin key, or (b) the profile has **no** origin yet and the folder **display name** matches the profile name. Matching only lists rules as available; the user toggles which ones apply. Enabling a rule on a folder that has a network origin **stamps** that origin onto an origin-less profile so a later clone still matches even if the folder is renamed.
- **Per-session selection**: Overflow **Project rules** on coding, PM, PR Reviewer, and automation-with-linked-folder sessions. Checkboxes persist per ACP session key. New sessions start with all rules off. The user can disable an already-enabled rule later.
- **Mandatory per-turn injection**: Every user-initiated send prepends a `<termfold-project-rules>` block (fixed MUST-follow preamble + currently enabled rule bodies) **in addition to** automation/PM/PR prefixes. The chat timeline does not show this block as a user message. Explicit user-message conflicts: follow the user, note which rule was skipped.
- **Prefix pipeline fix**: Today `AcpClient` takes automation prefix **or** `extraPromptPrefix`, not both. This change concatenates prefixes so automation sessions can still receive project rules.
- **Backup**: Profiles and per-session enablement maps are included in TermFold backup/restore.

**Out of scope**: Importing `.cursor/rules` or `AGENTS.md`; cloud sync; server-side enforcement; auto-enabling rules on new sessions; copying enablement across sessions; matching on local filesystem `origin` (session copies); sessions with no coding-folder context (unlinked automation).

## Capabilities

### New Capabilities

- `project-session-rules`: Durable profiles, Settings CRUD, origin/name eligibility, origin stamping, per-session enablement, session chrome, mandatory per-turn injection, backup, bundled skill text.

### Modified Capabilities

- `acp-session-chat`: Shared overflow entry; concatenate per-kind prefixes with the project-rules block on every user turn (including automation, which currently exclusive-ors prefixes).
- `bundled-linux-guest`: Bundled skill/notes for the project-rules anchor and mandatory-follow semantics.

## Impact

- New `projectrules/` package: models, store, origin-key helper (not GitHub-only), resolver, prompt assembler
- `AcpClient.buildPromptContent` — concatenate prefixes instead of XOR
- `SettingsScreen.kt`, `AgentSessionScreen.kt` overflow + sheet
- Automation / PM / PR session screens pass linked/coding folder + session key
- `TermFoldBackup.kt`
- `ShellSetup` skill/notes install
- Unit tests: store, matching (including “name must not override a different origin”), stamp-on-enable, assembler, prefix concatenation
