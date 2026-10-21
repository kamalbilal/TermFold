# Tasks

## 1. Timeline undo/redo predicates

- [x] 1.1 In `AgentSessionScreen` message loop, compute `showRedo` when `item.id == ghostFrom`, `canRedo`, and not `wireResyncing`; verify on a ghosted session only the branch-head user row qualifies
- [x] 1.2 Tighten `canUndo` with `ghostFrom == null || item.id < ghostFrom`, and suppress undo on the ghost head when `showRedo` is true; verify dimmed tail user rows never show restore

## 2. User message row UI

- [x] 2.1 Extend `UserMessageRow` with `showRedo` / `onRedo` using `acp_checkpoint_redo` label+icon (mirror restore row); wire `onRedo` to `client.redoCheckpoint()` without confirmation dialog
- [x] 2.2 Remove `SessionHeader` `onRedo` wiring and header redo button; verify header has no redo icon when redo stack is non-empty

## 3. Build and manual QA (inline redo)

- [x] 3.1 Run `./gradlew.bat :app:compileDebugKotlin` and confirm success
- [x] 3.2 On device: 6+ turn chat, undo turn 6 then turn 3 — confirm redo on turn 3, first redo restores ghost@6, second redo clears ghost; install via `:app:installDebug` if needed

## 4. Labels and conditional undo

- [x] 4.1 Rename strings to **Undo changes** / **Redo changes**; verify row labels and undo dialog title
- [x] 4.2 Track `undoableMessageIds` after turn completes when workspace differs from pre-turn hash; hide undo on chat-only turns

## 5. Performance and in-flight UX

- [x] 5.1 Run pre-turn shadow commit asynchronously on send (do not block `session/prompt`); verify message appears immediately
- [x] 5.2 Add `CheckpointBusy` + row spinner on undo/redo; keep action chips visible during wire resync

## 6. Edit in composer

- [x] 6.1 Add **Edit** chip on user rows; remove bubble tap-to-edit
- [x] 6.2 Load edit into main `Composer` with editing banner and cancel; submit opens revert/keep files dialog then `editAndResend`

## 7. Verify build

- [x] 7.1 `./gradlew.bat :app:compileDebugKotlin` succeeds after follow-ups

## 8. Edit revert wire session

- [x] 8.1 `wireResync` cancels and best-effort deletes the previous wire session before `session/new`; ignore `session/update` for non-active session ids
- [x] 8.2 `editAndResend(revertFiles=true)` clears redo on truncate, aborts send if resync fails, awaits pre-turn hash for restore
- [x] 8.3 Block composer send while revert/keep files dialog is open (`editFileChoice`)
- [x] 8.4 `./gradlew.bat :app:compileDebugKotlin` and `:app:installDebug` when device connected

## 9. TermFold session identity after wire resync

- [x] 9.1 Persist the local timeline per TermFold sessionKey; on handshake restore it and reopen the remembered wire session without replaying agent history
- [x] 9.2 Hide retired and resync-created wire session ids from `session/list`; keep `session/new` only as background agent context
- [x] 9.3 Unit tests for resume plan, past-list filtering, and timeline codec; compile Kotlin

## Workflow follow-up

- Run `openspec validate` before archive.
- Archive with `/opsx-archive` after review.
