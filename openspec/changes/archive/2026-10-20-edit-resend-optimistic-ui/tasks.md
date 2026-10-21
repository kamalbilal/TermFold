# Tasks

## 1. Refactor user turn pipeline

- [x] 1.1 Extract `beginUserTurn` from `sendNowSuspend` (allocate id, async pre-turn commit, append `UserMessage`, `agentBusy`/`turnInFlight`, persist) and `completeUserTurn` (wire payload + `session/prompt` + finalize + clear busy); verify `send()` behavior unchanged via existing unit tests or a focused test that mocks prompt
- [x] 1.2 Wire `sendNowSuspend` to call begin then complete in sequence; verify `:app:compileDebugKotlin` succeeds

## 2. Optimistic edit resend

- [x] 2.1 Reorder `editAndResend`: after successful restore (or skip when keep-files), truncate then `beginUserTurn`, then `wireResync` when reverting files, then `completeUserTurn`; verify restore failure still skips truncate and optimistic row
- [x] 2.2 On resync or prompt failure after optimistic row, call `appendSystemError`, clear busy/`turnInFlight`, and leave the user message; verify with a unit test or test hook that simulates failed `openSession`
- [x] 2.3 Add or extend unit test covering ordering: truncate → items contain new user text while `agentBusy` true before prompt completes

## 3. Timeline UI

- [x] 3.1 Confirm `WorkingRow` shows whenever `agentBusy` during edit resend (no extra UI state); optionally hide redundant top `wireResyncing` item if Working is visible — verify on device or screenshot checklist
- [x] 3.2 Device QA: edit message → Revert files → confirm message appears immediately and Working dots follow; agent reply still arrives; undo still works on file-changing turn after completion

## Workflow follow-up

- Archive with `/opsx-archive edit-resend-optimistic-ui` after QA.
