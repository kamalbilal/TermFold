# acp-session-checkpoints — Delta

## MODIFIED Requirements

_No requirement text changes._

Clarification only (documentation): checkpoint **wire resync** SHALL continue to retire the outgoing wire session and hide both the retired id and the resync clone from Earlier sessions for that tab. That behavior is unchanged and is separate from **New session**, which SHALL NOT hide the outgoing wire id (see `acp-past-sessions` delta).
