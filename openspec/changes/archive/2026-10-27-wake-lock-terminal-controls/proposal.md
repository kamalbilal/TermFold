# Proposal

## Why

TermFold currently acquires CPU and network wake locks for shell sessions whenever terminal output looks “busy,” without user consent. That can drain the battery when the user did not intend to keep work alive in the background, and there is no visible indication of whether a wake lock is held. Users need explicit per-terminal control (like Termux), with a battery-friendly default and an optional latched mode for long or quiet jobs.

## What Changes

- Remove automatic terminal-based wake locks driven only by shell output heuristics.
- Add per-terminal **Wake** (on/off, default off) and **Auto** (on/off, default on) toggles on the terminal accessory key row (first two keys of the bottom row), shared by `TerminalScreen` and agent aux terminals (`KeyRow`).
- **Wake off:** this terminal never contributes to background wake locks.
- **Wake on + Auto on (default):** battery-safe — hold locks only while that terminal session is “working” (existing `TerminalBridge.isWorking` heuristic).
- **Wake on + Auto off:** Termux-style — hold locks for that terminal while Wake stays on, including idle at a prompt.
- ACP native chat sessions keep today’s automatic `keepsCpuAwake()` behavior; this change is scoped to `TerminalHost` shells only.
- Ongoing `KeepAliveService` notification shows whether a wake lock is held and summarizes terminal vs agent reasons.
- Fix `SessionWatcher.syncKeepAlive` partial updates (screen on/off, session start) so notification counts and wake-lock state are not reset incorrectly between poll ticks.

## Capabilities

### New Capabilities

- `terminal-wake-lock`: Per-session Wake/Auto preferences, terminal contribution rules to `KeepAliveService`, and notification status for wake locks.

### Modified Capabilities

_None._

## Impact

- `app/src/main/java/com/termfold/app/shell/TerminalHost.kt` — store Wake/Auto per session key
- `app/src/main/java/com/termfold/app/ui/screens/TerminalScreen.kt` — `KeyRow` toggles
- `app/src/main/java/com/termfold/app/ui/screens/AgentAuxTerminals.kt` — pass session key into `KeyRow`
- `app/src/main/java/com/termfold/app/notify/SessionWatcher.kt` — `needsBackgroundLocks`, `syncKeepAlive`, notification builder
- `app/src/main/res/values/strings.xml` (+ locales as needed)
- Unit tests for wake-lock decision logic (pure function or small testable helper)
