# acp-idle-disconnect Specification

## Purpose
Automatically disconnect idle ACP agent processes after background time to save battery, with user-controlled enablement, timeout, and a notification toggle.

## Requirements

### Requirement: Idle disconnect preferences

The system SHALL store whether background idle disconnect is enabled and the idle timeout in minutes. Idle disconnect SHALL default to **enabled**. The timeout SHALL default to **10** minutes and SHALL be configurable within a bounded range suitable for mobile use (at least 1 minute and at most 120 minutes).

#### Scenario: Defaults on first install

- **WHEN** the user has never changed idle disconnect settings
- **THEN** idle disconnect SHALL be enabled and the timeout SHALL be 10 minutes

#### Scenario: Settings change timeout

- **WHEN** the user sets idle disconnect timeout to a valid number of minutes in Settings
- **THEN** subsequent idle timers SHALL use that value until changed again

### Requirement: Idle disconnect eligibility

The system SHALL consider disconnecting an ACP chat session only when **all** of the following hold: idle disconnect is enabled; the app is not in the foreground; that session does not require CPU wake (`keepsCpuAwake()` is false); and the session has remained continuously eligible for the full configured timeout since the last time it became ineligible.

#### Scenario: Foreground resets timer

- **WHEN** the app returns to the foreground before the timeout elapses
- **THEN** the idle timer for every session SHALL reset and no disconnect SHALL occur for that background stint

#### Scenario: Active agent resets timer

- **WHEN** a session begins requiring CPU wake (busy turn, permission prompt, or wire resync)
- **THEN** the idle timer for that session SHALL reset and SHALL not count background time until it is idle again

#### Scenario: Disabled policy never disconnects

- **WHEN** idle disconnect is disabled
- **THEN** the system SHALL NOT disconnect ACP sessions for idle background time regardless of duration

### Requirement: Idle disconnect action

When the idle timer fires for a session, the system SHALL tear down that session's agent process and connection while preserving the on-disk chat timeline. The session entry MAY remain listed in the app; reopening or sending a message SHALL reconnect the agent using the existing session start flow without deleting messages.

#### Scenario: Disconnect after timeout

- **WHEN** idle disconnect is enabled, the app has been in the background for the configured timeout, and a session has been continuously idle-eligible for that entire period
- **THEN** the system SHALL disconnect that session's agent process

#### Scenario: Terminals unaffected

- **WHEN** a terminal shell session is running in the background under the same conditions
- **THEN** the system SHALL NOT apply idle disconnect to that terminal session

### Requirement: Notification toggle for idle disconnect

The ongoing running-sessions notification SHALL include an action that toggles whether idle disconnect is enabled. The action label SHALL reflect the current state (on vs off). Tapping the action SHALL flip the preference immediately without a confirmation dialog and SHALL update the notification on the next refresh.

#### Scenario: Toggle off from notification

- **WHEN** idle disconnect is enabled and the user taps the notification toggle action
- **THEN** idle disconnect SHALL become disabled and no further idle disconnects SHALL occur until re-enabled

#### Scenario: Toggle on from notification

- **WHEN** idle disconnect is disabled and the user taps the notification toggle action
- **THEN** idle disconnect SHALL become enabled and idle timers SHALL apply on subsequent background idle periods

### Requirement: Settings UI for idle disconnect

Settings SHALL expose controls to enable or disable idle disconnect and to edit the timeout in minutes, using the same preference store as the notification toggle.

#### Scenario: Settings mirrors notification toggle

- **WHEN** the user disables idle disconnect in Settings
- **THEN** the notification toggle state SHALL show off on the next notification update
