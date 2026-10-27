# acp-session-checkpoints — Delta

## MODIFIED Requirements

### Requirement: Wire resync may use an explicit summary payload

When rotating the wire session for checkpoint undo, edit-resend, or context compress after `/summarize`, the system MAY set pending prompt context from an explicit summary string instead of only `ConversationSummary.fromItems`.

#### Scenario: Context compress after summarize

- **WHEN** context compress runs after `/summarize`
- **THEN** pending prompt context uses the agent summarize text (with standard header), not a mechanical re-summary of truncated items

#### Scenario: Checkpoint undo after compress

- **WHEN** the user undoes a workspace checkpoint on a message that predates compress
- **THEN** wire resync builds context from the full retained timeline items and ghost marker as today
