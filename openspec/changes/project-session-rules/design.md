# Design

## Context

See `proposal.md`. Per-turn prefixes already exist for automation (`AutomationPromptAssembler`), PM (`PmPrompt.prefix`), and PR review (`PrReviewPrompt`). `AcpClient.buildPromptContent` currently does:

```
automationPromptPrefix?.takeIf { notBlank } ?: extraPromptPrefix
```

so automation sessions **never** send `extraPromptPrefix`. Session chrome is `AgentSessionScreen` / `SessionHeaderOverflowMenu`. `GitHubOrigin.normalize` only parses github.com URLs. Session-copy `origin` is often a filesystem path to golden (`SessionCopyOriginSync`). Folders live in `FolderStore`; deleting them must not delete this store.

## Goals / Non-Goals

**Goals:**

- Durable JSON store for profiles, rules, and per-session enabled rule ids.
- Eligibility from **golden network origin** + name-only fallback when a profile has no origin.
- Stamp origin on first enable.
- Settings CRUD + session checkbox sheet.
- Assembler + **concatenated** prefix pipeline.
- Backup + bundled skill/notes.

**Non-Goals:**

- Repo file import, auto-enable, cross-session copy of toggles, matching local-path remotes, token-budget UI beyond a char cap.

## Decisions

**1. Storage**

- One JSON file under app files, e.g. `files/project-rules/store.json` (not `FolderStore`).
- `profiles[]`: `id`, `displayName`, `originKey: String?`, `rules[]` (`id`, `title`, `body`, `sortOrder`).
- `sessionBindings`: map ACP `sessionKey` → list/set of enabled `ruleId`s (order = enable order or profile/rule order at assemble time; assemble in profile then rule `sortOrder`).
- Unknown rule ids in bindings are skipped.

**2. Session key**

- Same string `AcpClient` already uses: `SessionKeys.compose`, `AutomationSessionContext.key`, PM/PR `sessionKeyOverride`. Never bind on `folderId` alone.

**3. Origin key (not GitHubOrigin)**

- New `RemoteOriginKey.normalize(raw): String?`.
- Accept `https`/`http`/`ssh`/`git@` URLs: lowercase host, strip userinfo, strip trailing `/` and `.git`, keep `host/owner/repo` (or host + path).
- Return null for empty, local paths, and `file://` so session-copy origins never identity-match.
- Resolver always reads **golden** guest origin (`WorkspaceStore.goldenGuestPath` + `GitWorkspace.originUrl`), not the session copy.

**4. Eligibility**

- Input: coding `Folder?` (automation: `linkedFolder` only).
- Profile with `originKey`: eligible iff golden network origin equals that key.
- Profile without `originKey`: eligible iff `folder.name.trim().equals(profile.displayName.trim(), ignoreCase = true)`.
- Never name-match a profile that already has a different origin key.

**5. Stamp on enable**

- When toggling a rule on: if that rule’s profile `originKey` is null and the folder has a network origin, set `originKey`. Do not overwrite a set key. Do not stamp from a local-path origin.

**6. Inject vs sheet**

- Inject: enabled ids that still exist in any profile.
- Sheet: eligible rules, plus enabled-but-ineligible rows (so they can be turned off).
- New session: empty enabled set.

**7. Prompt assembler**

- Tag `termfold-project-rules`.
- Fixed preamble (not user-editable): MUST follow every turn while enabled; explicit user conflict → follow user and note the skipped rule.
- List enabled rules with title + body; skip blank bodies.
- `MAX_TOTAL_CHARS` (~10k) with a truncation marker if needed.
- Empty enabled set → empty string (no tag).

**8. AcpClient prefix order (required change)**

Replace XOR with concatenation:

1. `pendingWireContext` (existing, then clear)
2. `automationPromptPrefix` if non-blank
3. `extraPromptPrefix` (PM/PR) if non-blank
4. `projectRulesPrefix` if non-blank
5. user text

Each non-empty section separated by `\n\n---\n\n`. Add `projectRulesPrefix: (() -> String)?` on `AcpClient` / `AgentSessionScreen` so it cannot be swallowed by automation mode. Prefixes are wire-only; timeline stores the user text unchanged. Edit/resend uses current closures.

**9. UI**

- Settings group “Project rules”: profile list → editor (name, origin field: paste URL or “Copy from project…” picker, rule list).
- Overflow: show when `codingFolder != null`. Automation unlinked: hide.
- Sheet empty copy when nothing eligible and nothing leftover-enabled.

**10. Skill install**

- Asset `termfold-project-rules-skill.md`.
- Claude: `root/.claude/skills/termfold-project-rules/SKILL.md`.
- Other agents: wrap the body in `<!-- termfold-project-rules:start/end -->` notes the same way `installBrowserTools` patches `AGENT_NOTES`.

**11. Backup**

- Include `store.json` in export; restore replaces that file. Session keys in the backup must match restored ACP session keys (same as other session-scoped prefs).

## Risks / Trade-offs

- **[Risk] Two folders, same origin** → Both see the same profile; enablement stays per session. Acceptable.
- **[Risk] Name collision before stamp** → User must toggle; after first enable, origin disambiguates. Document in empty/hint copy if useful.
- **[Risk] Large rules** → Char cap.
- **[Risk] Models ignore MUST** → Same as PM/automation; skill notes. Not a runtime guarantee.
- **[Risk] Stale enabled rules after rename** → Intentional; sheet still shows them.

## Migration Plan

- Empty store on first launch. No data migration.
- Rollback: unused JSON file; prefix extra is additive.

## Open Questions

None for MVP. `.cursor/rules` import stays a later change.
