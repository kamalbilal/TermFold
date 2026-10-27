# Design

## Context

See `proposal.md` — Why. ACP clients live in `AcpSessions` with a long-lived `AcpClient` and proot-backed `Process`. `SessionWatcher` polls every second, drives `KeepAliveService`, and already tracks per-session busy state. `AppPresence.inForeground` is available. `AcpWakePolicy.keepsCpuAwake()` defines when chat must stay hot. Timelines persist via `AcpTimelineStore`.

## Goals / Non-Goals

**Goals:**

- Disconnect idle ACP agents after configurable background time (default 10 min, policy on by default).
- Global enable toggle in Settings and on the running notification (action label reflects state).
- Preserve timeline; reconnect via existing `AcpClient.start()` when user returns.
- Unit-testable eligibility and timer reset rules.

**Non-Goals:**

- Freezing or SIGSTOP on proot trees.
- Idle disconnect for `TerminalHost` shells.
- Per-session idle toggle (global preference only in v1).
- Changing when the foreground service stops (still `live > 0` unless a future change narrows it).

## Decisions

1. **Where the timer runs**  
   Extend `SessionWatcher.tick()` (1s poll): for each ACP client, update idle tracking when `!AppPresence.inForeground && IdleDisconnectPrefs.enabled && !client.keepsCpuAwake()`. When continuous eligible duration ≥ `timeoutMs`, call disconnect.  
   *Alternative:* `WorkManager` one-shot — rejected; harder to reset on foreground and duplicates session knowledge.

2. **Disconnect semantics**  
   Add `AcpClient.disconnectIdle()` (or `AcpSessions.disconnectIdle(key)`): destroy `Process`, clear wire session pointers as needed, set phase to `CLOSED` (or dedicated `DISCONNECTED_IDLE`), **do not** remove from `AcpSessions` map or clear timeline. Set a flag `idleDisconnected = true` for UI notice.  
   `start()` on re-entry already restarts when phase is `CLOSED`/`ERROR`/`AUTH_REQUIRED`.  
   *Alternative:* `AcpSessions.close()` — removes client; rejected because it complicates re-entry and `live` accounting.

3. **Preferences**  
   `IdleDisconnectPrefs` in `app/.../acp/` or `notify/`: `enabled` (default `true`), `timeoutMinutes` (default `10`, clamp 1–120). Backed by `SharedPreferences` (same pattern as `CursorAcpPrefs`). Expose `onChanged` callback → `SessionWatcher.onKeepAliveInputsChanged` to refresh notification.

4. **Notification toggle**  
   Android does not offer a true toggle widget; use `NotificationCompat.addAction` with label `running_idle_disconnect_on` / `running_idle_disconnect_off` (or single string with state). `BroadcastReceiver` (e.g. `ToggleIdleDisconnectReceiver`) flips `IdleDisconnectPrefs.enabled` and triggers notification rebuild via `KeepAliveService.update` with current snapshot.  
   Place action beside existing **Stop** (order: toggle, then stop).

5. **Settings UI**  
   New card or rows under an existing “Sessions” / “Battery” section: switch for enable, numeric stepper or text field for minutes (validate on save). Strings in `values/strings.xml`.

6. **Chat notice**  
   On idle disconnect, append `AcpItem.Notice` with string resource, or set `AcpUiState.statusText` + persist timeline so reload shows notice. Prefer `Notice` item for consistency with other system messages.

7. **Timer reset**  
   Per-key `var idleEligibleSince: Long?` in `SessionWatcher` (or small `IdleDisconnectTracker` object). Set to `now` when eligibility becomes true; clear when false. Fire once per stint (clear after disconnect).

## Risks / Trade-offs

- **[Risk] User expects agent to stay warm in background** → Default-on is aggressive; notification toggle makes opt-out one tap; Settings documents behavior.  
- **[Risk] Disconnect during slow permission UX if misclassified idle** → `keepsCpuAwake()` includes `pendingPermission` and `wireResyncing`.  
- **[Risk] Reconnect latency or auth** → Same as today after process death; document in notice.  
- **[Trade-off] Global toggle** → Simpler UX; per-session can be a follow-up.

## Migration Plan

- Ship with defaults (enabled, 10 min); no data migration.  
- Rollback: disable via preference default change only if needed; users who enabled keep setting.

## Open Questions

- Exact min/max minutes in UI (proposed 1–120).  
- Whether to remove disconnected clients from `live` count immediately (recommended: yes, after process destroyed, so notification reflects fewer “running” agents).
