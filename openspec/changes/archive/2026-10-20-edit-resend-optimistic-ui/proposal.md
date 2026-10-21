# Proposal

## Why

Editing a user message with **Revert files** runs workspace restore and wire session resync before the edited message appears in the timeline, leaving a 2–3 second gap with no user bubble and only a faint resync hint. That feels broken compared to a normal send, where the message and the working indicator show immediately.

## What Changes

- Show the edited user message in the timeline as soon as the user confirms revert-or-keep (after truncate), without waiting for file restore or `session/new`.
- Show the same **Working** row (stepping dots + elapsed time) used during agent turns while prep work (restore, wire resync) and the subsequent prompt are in flight.
- Refactor send path so edit-resend can append the user turn optimistically, then complete the wire `session/prompt` with the existing message id (no duplicate row).
- On prep or prompt failure after the optimistic row is shown: surface a clear error; leave the user message visible unless rollback is explicitly required (see design).
- **Keep-files** edit-resend uses the same optimistic pattern for consistency (truncate → message + Working → prompt).

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `acp-session-checkpoints`: Timeline feedback during edit-and-resend with file revert (optimistic user message + working state).

## Impact

- `AcpClient.editAndResend`, `sendNowSuspend` (split or parameterize append vs wire send).
- `AgentSessionScreen` timeline: may rely on `agentBusy` during prep instead of only `wireResyncing` banner; optional UX tweak to avoid duplicate “busy” affordances.
- Unit tests around edit-resend ordering and failure paths; manual device QA on revert-files edit flow.
