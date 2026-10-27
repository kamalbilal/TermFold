# Spec Delta

## ADDED Requirements

### Requirement: Idle disconnect is visible in chat

When an ACP session was disconnected because of the idle disconnect policy, the chat timeline SHALL show a short inline notice explaining that the agent was stopped to save battery and that sending a message or reopening will reconnect.

#### Scenario: Notice after idle disconnect

- **WHEN** the user opens a session that was disconnected due to idle background timeout
- **THEN** the UI SHALL show an inline notice describing idle disconnect and SHALL show the agent as not running until reconnect

#### Scenario: Reconnect on send

- **WHEN** the user sends a message in a session that was idle-disconnected
- **THEN** the system SHALL start or reconnect the agent and SHALL continue the existing timeline without removing prior messages
