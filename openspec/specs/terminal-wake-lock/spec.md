# terminal-wake-lock Specification

## Purpose
Lets users control background CPU and network wake locks per terminal session, with a battery-friendly default and an optional latched mode, without affecting ACP agent automatic keep-awake behavior.

## Requirements

### Requirement: No automatic terminal wake lock without Wake enabled

The system SHALL NOT acquire background wake locks for a terminal session based solely on terminal output activity unless the user has enabled Wake for that session.

#### Scenario: Idle shell with Wake off

- **WHEN** a shell session is open at an idle prompt and Wake is off for that session
- **THEN** that session does not cause a partial wake lock, Wi‑Fi lock, or network hold to be acquired

#### Scenario: Busy shell with Wake off

- **WHEN** a shell session is producing output consistent with “working” and Wake is off for that session
- **THEN** that session still does not cause background wake locks to be acquired

### Requirement: Per-terminal Wake toggle

The system SHALL provide a Wake toggle on the terminal accessory key row for each terminal session, persisted for that session key until the session is closed.

#### Scenario: Wake default off

- **WHEN** the user opens a new terminal or aux terminal tab
- **THEN** Wake is off for that session

#### Scenario: User enables Wake

- **WHEN** the user turns Wake on for the visible terminal session
- **THEN** the preference is stored for that session key and reflected in the toggle’s active state on return to that session

### Requirement: Per-terminal Auto toggle with default on

The system SHALL provide an Auto toggle on the terminal accessory key row, default on for each session, persisted per session key until the session is closed.

#### Scenario: Auto default on

- **WHEN** the user opens a new terminal or aux terminal tab
- **THEN** Auto is on for that session

#### Scenario: Auto only applies when Wake is on

- **WHEN** Wake is off for a session
- **THEN** Auto has no effect on wake lock acquisition for that session

### Requirement: Battery-safe mode when Wake on and Auto on

When Wake is on and Auto is on for a terminal session, the system SHALL acquire background wake locks for that session only while the session is considered working by the existing terminal activity heuristic.

#### Scenario: Working terminal with Wake and Auto on

- **WHEN** Wake is on, Auto is on, and the terminal session is working
- **THEN** that session contributes to holding background wake locks

#### Scenario: Idle prompt with Wake and Auto on

- **WHEN** Wake is on, Auto is on, and the terminal session is not working
- **THEN** that session does not contribute to background wake locks

### Requirement: Latched mode when Wake on and Auto off

When Wake is on and Auto is off for a terminal session, the system SHALL treat that session as contributing to background wake locks while the session process is running, regardless of whether the terminal is working.

#### Scenario: Idle prompt with Wake on and Auto off

- **WHEN** Wake is on, Auto is off, and the shell is idle at a prompt
- **THEN** that session contributes to holding background wake locks

#### Scenario: User turns Wake off in latched mode

- **WHEN** Wake is turned off for a session that was holding a latched wake lock
- **THEN** that session stops contributing to background wake locks on the next keep-alive sync

### Requirement: ACP sessions unchanged

The system SHALL continue to acquire background wake locks for ACP chat sessions according to existing agent busy, permission, and in-flight turn rules, independent of terminal Wake toggles.

#### Scenario: Busy agent without terminal Wake

- **WHEN** an ACP session is in a state that requires CPU awake and no terminal has Wake on
- **THEN** background wake locks may still be acquired for the agent session

### Requirement: Ongoing notification reports wake lock state

The ongoing sessions notification SHALL indicate whether a background wake lock is currently held and distinguish terminal-driven holds from agent-driven holds when both may apply.

#### Scenario: Wake lock off with idle sessions

- **WHEN** sessions are open but no terminal or agent requires wake locks
- **THEN** the notification indicates that the wake lock is not active

#### Scenario: Wake lock on for terminal

- **WHEN** a terminal session contributes to an active wake lock
- **THEN** the notification indicates that the wake lock is active and that a terminal is the reason
