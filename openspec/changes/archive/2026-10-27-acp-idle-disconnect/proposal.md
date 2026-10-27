# Proposal

## Why

ACP agent processes (proot + agent CLI) stay alive in the background after a turn finishes, even when the partial wake lock is off. That wastes RAM and background CPU on phones when the user is not actively chatting. A timed idle disconnect—with user control—matches the battery-saving approach we discussed without the complexity of freezing the whole guest.

## What Changes

- After the app has been in the **background** for a configurable idle period (default **10 minutes**), automatically **disconnect** each ACP chat that is **not** doing work (`!keepsCpuAwake()`), including no in-flight turn, no permission prompt, and no wire resync.
- **Default:** idle disconnect **enabled**.
- **Settings:** user-configurable idle timeout in minutes (default 10; sensible min/max).
- **Running-session notification:** add an action that **toggles** whether idle disconnect is enabled (label reflects on/off). Tapping flips the preference immediately; no separate confirmation.
- On disconnect: end the agent process for that session key; **persist** chat timeline on disk; when the user returns to the session, existing **start/reconnect** flow brings the agent back without losing messages.
- Idle disconnect applies to **ACP native chat sessions only** (not `TerminalHost` shells). Terminals are unchanged in this change.
- Optional subtle feedback when a session was disconnected due to idle policy (in-chat notice or status), so “stale agent” is explainable.

## Capabilities

### New Capabilities

- `acp-idle-disconnect`: Idle timer policy, preferences (enabled + minutes), notification toggle action, disconnect/reconnect behavior, and settings UI.

### Modified Capabilities

- `acp-session-chat`: Surface when a session was disconnected due to idle policy so the user understands why the agent is not running until they reopen or send a message.

## Impact

- `app/src/main/java/com/termfold/app/notify/SessionWatcher.kt` — idle tracking per ACP key, timer, disconnect calls
- `app/src/main/java/com/termfold/app/notify/KeepAliveService` notification builder — new toggle action
- `app/src/main/java/com/termfold/app/acp/AcpClient.kt` / `AcpSessions.kt` — idle disconnect API (process teardown, preserved timeline)
- `app/src/main/java/com/termfold/app/ui/screens/SettingsScreen.kt` — timeout + copy
- `app/src/main/res/values/strings.xml` (+ locales as needed)
- New prefs helper + unit tests for idle eligibility and timer reset rules
