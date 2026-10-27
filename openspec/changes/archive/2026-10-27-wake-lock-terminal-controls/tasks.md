# Tasks

## 1. Terminal session preferences

- [x] 1.1 Add per-session `wakeEnabled` (default false) and `wakeAuto` (default true) on `TerminalHost`, cleared when a session is closed; verify via unit test or small test that new sessions get defaults

- [x] 1.2 Expose `terminalWantsWakeLock(liveSession)` (or equivalent) implementing Wake+Auto rules from design; verify with unit tests for all four combinations (Wake/Auto × working/idle × running)

## 2. SessionWatcher and KeepAliveService

- [x] 2.1 Replace `needsBackgroundLocks(shellWorkingCount)` with terminal preference logic plus existing ACP `keepsCpuAwake()`; verify tests from 1.2 cover integration expectations

- [x] 2.2 Refactor `syncKeepAlive` to recompute `active`, `working`, and `holdWakeLock` when not passed explicitly (fix screen on/off and `onSessionStarted`); verify by test or documented manual check that enabling Wake on a busy shell immediately updates without waiting for poll

- [x] 2.3 Extend ongoing notification text to show wake lock active/inactive and terminal vs agent reason; verify strings exist in `values/strings.xml`

## 3. Terminal UI

- [x] 3.1 Add **Wake** and **Auto** `ModifierKey` toggles as the first two keys on the bottom row of `KeyRow`; wire `TerminalScreen` to the current session key; verify toggles highlight correctly and do not send input to the PTY

- [x] 3.2 Wire `AgentAuxTerminals` `KeyRow` to the aux session key so each tab has independent Wake/Auto; verify switching tabs restores each tab’s toggle state

## 4. Copy and settings

- [x] 4.1 Add user-facing strings for Wake, Auto, and notification wake-lock lines (English `values/strings.xml` minimum); verify labels fit the key row

- [x] 4.2 Update `settings_phantom_desc` to describe terminal Wake/Auto behavior; verify wording matches spec modes

## 5. Verification

- [x] 5.1 Run `./gradlew :app:testDebugUnitTest` for new wake-lock tests; on device: Wake off + busy shell → no lock; Wake on + Auto on + idle → no lock; Wake on + Auto off + idle → lock shown in notification until Wake off
