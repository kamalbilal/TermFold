# Tasks

## 1. Preferences and policy

- [x] 1.1 Add `IdleDisconnectPrefs` (enabled default true, timeout minutes default 10, clamp 1–120) with unit tests for defaults and clamping
- [x] 1.2 Add `IdleDisconnectTracker` (or equivalent) pure functions for eligibility and timer reset; unit test foreground/busy/disable cases per spec

## 2. ACP disconnect API

- [x] 2.1 Implement `AcpClient.disconnectIdle()` / `AcpSessions` wrapper: kill process, keep timeline, set idle-disconnected flag, persist notice item; verify unit or instrumented test that process is gone and items remain
- [x] 2.2 Ensure `start()` reconnects after idle disconnect without clearing chat; manual or test hook verifying phase transitions

## 3. SessionWatcher integration

- [x] 3.1 Wire tracker into `SessionWatcher.tick()`; disconnect when background idle exceeds timeout; reset on foreground or `keepsCpuAwake()`; verify with unit tests on tracker + manual background soak
- [x] 3.2 After idle disconnect, session no longer counts as `live` for keep-alive until reconnected; verify notification `active`/`live` counts drop when only idle agents remain

## 4. Notification and Settings UI

- [x] 4.1 Add `ToggleIdleDisconnectReceiver` and notification action with on/off labels; verify tap flips pref and notification text updates within one poll
- [x] 4.2 Settings rows for enable + timeout minutes; verify edits persist and match notification toggle state
- [x] 4.3 Add strings (`values/strings.xml` + locales as needed) for settings, notification actions, and idle notice

## 5. Chat UX

- [x] 5.1 Show inline notice when session was idle-disconnected; verify notice visible after reopen and clears or remains per product copy after successful reconnect

## Workflow follow-up

- Run `./gradlew :app:assembleDebug` after `app/` changes per project rule.
- Archive with `openspec archive` after review if the project uses archive workflow.
