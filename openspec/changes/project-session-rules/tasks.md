# Tasks

## 1. Data layer

- [x] 1.1 Add models + `ProjectRulesStore` JSON (profiles, rules, `sessionKey` → enabled rule ids) outside `FolderStore`
- [x] 1.2 Add `RemoteOriginKey.normalize` (network remotes only; null for local/`file://`) and `ProjectRulesContextResolver` using golden guest origin + name fallback as spec’d
- [x] 1.3 Stamp `originKey` on first enable when the folder has a network origin; never overwrite
- [x] 1.4 Add `ProjectRulesPromptAssembler` (`termfold-project-rules`, mandatory preamble, skip empty bodies, char cap)
- [x] 1.5 Unit tests: encode/decode; GitHub/GitLab match; filesystem origin ignored; same-name different-origin not eligible; stamp then re-clone; deleted rule ids skipped; assembler empty vs enabled vs truncation

## 2. ACP prefix pipeline

- [x] 2.1 Change `AcpClient.buildPromptContent` to concatenate automation + extra + project-rules prefixes (not XOR); add `projectRulesPrefix` callback
- [x] 2.2 Wire `AgentSessionScreen` to pass assembler output for the current session key; edit/resend uses current enabled set
- [x] 2.3 Unit test: automation session with enabled rules includes both blocks; zero enabled omits the tag; wire text is not stored as the user timeline message

## 3. Settings UI

- [x] 3.1 Settings group: profile list, create/rename/delete
- [x] 3.2 Profile editor: origin paste + copy-from-folder, rule add/edit/reorder/delete, empty-body validation
- [x] 3.3 Strings for labels, empty states, delete confirms

## 4. Session chrome and binding

- [x] 4.1 Overflow **Project rules** + sheet (grouped checkboxes; leftover enabled-but-ineligible rows; empty match copy). Hide on unlinked automation
- [x] 4.2 Pass coding folder / linked folder from coding, Automation, PM, and PR session screens
- [x] 4.3 New sessions start with no rules enabled; toggles persist across resume

## 5. Backup and docs

- [x] 5.1 Include store file in `TermFoldBackup` export/import; test round-trip
- [x] 5.2 Add `termfold-project-rules-skill.md` and install via `ShellSetup` (Claude skill + agent notes), matching browser-skill pattern

## 6. Build

- [x] 6.1 Run `./gradlew :app:assembleDebug -Pandroid.aapt2FromMavenOverride=/root/projects/Termfold/.tools/aapt2` after `app/` changes
