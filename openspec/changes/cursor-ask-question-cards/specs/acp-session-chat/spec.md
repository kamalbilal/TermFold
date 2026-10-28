# Spec Delta

## ADDED Requirements

### Requirement: Cursor ask-question ACP extension

For **Cursor** agent sessions only (`agent.id == "cursor"`), when the agent sends a blocking **`cursor/ask_question`** JSON-RPC request, the client SHALL parse the official payload (`toolCallId`, optional `title`, `questions[]` with `id`, `prompt`, `options[]`, optional `allowMultiple` or `allow_multiple`) and SHALL NOT respond with an unknown-method error.

#### Scenario: Agent asks during a turn

- **WHEN** the agent emits `cursor/ask_question` while handling the latest user prompt
- **THEN** the client SHALL hold the request until the user submits, skips, or cancels
- **AND** the agent SHALL receive a JSON-RPC result whose `outcome` is `answered`, `skipped`, or `cancelled` per Cursor ACP documentation

#### Scenario: Answered outcome shape

- **WHEN** the user completes the form with valid selections for every required question
- **THEN** the client SHALL respond with `outcome: "answered"` and `answers[]` entries pairing each `questionId` with `selectedOptionIds[]` (multiple ids when `allowMultiple` is true)

#### Scenario: Skipped outcome

- **WHEN** the user taps Skip on the ask card
- **THEN** the client SHALL respond with `outcome: "skipped"` and a non-empty `reason` string compatible with Cursor agent expectations (for example indicating the user skipped)

### Requirement: Per-turn ask-question cards in chat

The system SHALL render interactive ask-question UI as **turn footers** anchored to the **user message id** of the agent turn that produced the ask, using the same timeline placement pattern as other per-turn footers (content appears after that turn’s agent items; earlier turns keep their footers when scrolling).

#### Scenario: First turn ask

- **WHEN** the user sends message A and the agent raises `cursor/ask_question` before finishing that turn
- **THEN** the ask card SHALL appear under message A’s turn, below the agent content for that turn

#### Scenario: Second turn retains first card

- **WHEN** the user later sends message B and the agent responds without a new ask
- **THEN** message A’s ask card (answered, skipped, or cancelled state) SHALL remain visible under message A when the user scrolls up
- **AND** message B’s turn SHALL NOT show message A’s ask as an editable form

#### Scenario: Second turn new ask

- **WHEN** the user sends message B and the agent raises a new `cursor/ask_question`
- **THEN** the new interactive card SHALL appear only under message B’s turn

#### Scenario: Plain coding Cursor session

- **WHEN** the user chats in a folder session that uses the Cursor agent and does not pass a custom workflow `turnFooter`
- **THEN** the chat timeline SHALL still insert per-turn footer rows for Cursor so ask cards can render under the correct user message

#### Scenario: Stacked workflow footers

- **WHEN** Product Manager or PR Review sessions pass an existing workflow `turnFooter`
- **THEN** the ask card for that turn SHALL appear in the same footer slot above the workflow footer content without replacing it

### Requirement: Ask card interaction and ChatGPT-like UX within Cursor constraints

The ask card SHALL support single-select and multi-select questions, a synthetic **Other…** freeform option when not disabled by product policy, **Continue** (submit answers), and **Skip**, with presentation aimed at parity with common chat product patterns (inline card, not permission-dialog-only).

#### Scenario: Single select

- **WHEN** a question has `allowMultiple` false or absent
- **THEN** the UI SHALL allow exactly one selected option at a time among listed options (radio semantics)

#### Scenario: Multi select

- **WHEN** a question has `allowMultiple` true
- **THEN** the UI SHALL allow zero or more selected options until the user submits (checkbox semantics)
- **AND** Continue SHALL remain disabled until at least one option is selected for that question unless Skip is used

#### Scenario: Other freeform

- **WHEN** the user selects the Other… row for a question
- **THEN** the UI SHALL show a text field for custom input
- **AND** on Continue the client SHALL include the synthetic freeform option id in `selectedOptionIds` per Cursor CLI conventions

#### Scenario: Continue gating

- **WHEN** multiple questions are present in one ask
- **THEN** Continue SHALL stay disabled until every question has a valid selection (or valid Other text when Other is selected)
- **AND** Skip SHALL remain available without requiring selections

#### Scenario: After submit

- **WHEN** the user submits or skips
- **THEN** the card SHALL become read-only and SHALL show a compact summary of the outcome (choices and/or skipped state)
- **AND** the user SHALL NOT be able to change answers without sending a new chat turn

#### Scenario: Only latest open ask is editable

- **WHEN** the timeline contains an unanswered ask from an earlier user turn and a newer turn is active
- **THEN** the earlier ask SHALL display as read-only history
- **WHEN** the latest user turn has a pending unanswered ask
- **THEN** only that ask SHALL accept input

### Requirement: Composer blocked during pending ask

While a blocking `cursor/ask_question` request is awaiting user action for the active turn, the session chat composer SHALL NOT send new user prompts until the ask is answered, skipped, or cancelled.

#### Scenario: Send disabled

- **WHEN** `pendingAskQuestion` is non-null for the active Cursor session
- **THEN** the send action SHALL be disabled or ignored with clear affordance that input is required on the ask card

#### Scenario: Resume after response

- **WHEN** the client returns the JSON-RPC result for the ask
- **THEN** the composer SHALL become available again and the agent MAY continue the same turn

#### Scenario: Client send guard

- **WHEN** a pending ask is open
- **THEN** the ACP client SHALL NOT start a new user prompt via `send` until the ask is resolved

### Requirement: Ask-question persistence on timeline

The system SHALL persist each turn’s ask payload and **terminal** outcome (answered, skipped, or cancelled) in **`AcpPersistedTimeline`** / timeline store JSON so ask cards survive process restart and reload with read-only summaries. In-flight blocking RPC state SHALL NOT be required to survive restart.

#### Scenario: Round-trip save and load

- **WHEN** a session with one or more completed ask footers is saved and reopened
- **THEN** each footer's questions and outcome SHALL restore without contacting the agent again

#### Scenario: Prune on timeline truncate

- **WHEN** user messages are removed from the timeline (undo, edit-resend, or compress that drops turns)
- **THEN** stored ask entries for removed user message ids SHALL be dropped, mirroring per-turn report map pruning behavior

#### Scenario: Legacy timelines

- **WHEN** a timeline saved before ask persistence loads
- **THEN** the session SHALL load without error and SHALL simply omit historical ask footers
