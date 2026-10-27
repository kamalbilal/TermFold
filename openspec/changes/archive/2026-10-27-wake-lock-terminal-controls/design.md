# Design

## Context

See `proposal.md` — Why. Wake locks are implemented in `KeepAliveService` (`SessionWatcher.kt`), driven by `needsBackgroundLocks()` which today returns true when any shell looks `working` or any ACP client `keepsCpuAwake()`. Terminal UI uses shared `KeyRow` in `TerminalScreen.kt` (also `AgentAuxTerminals.kt`). Session keys come from `TerminalHost.sessionKey(folderId, workspaceId, sessionId)`.

## Goals / Non-Goals

**Goals:**

- Two `ModifierKey`-style toggles as the first keys on the **bottom** row of `KeyRow`: **Wake**, then **Auto**.
- Per-session state on `TerminalHost` (or adjacent store keyed by session key): `wakeEnabled` (default false), `wakeAuto` (default true).
- Pure decision function for “does this terminal request a wake lock right now?” used by `SessionWatcher`.
- Notification subtitle/second line for wake lock status; keep existing active-session title logic.
- Fix `syncKeepAlive` when called without `active`/`working` — recompute from live sessions instead of defaulting to zero.

**Non-Goals:**

- Global Termux-style notification button (per-terminal toggles are sufficient for v1).
- Changing ACP `keepsCpuAwake()` rules.
- Persisting Wake/Auto across app restart for closed sessions (in-memory per live session is enough; optional SharedPreferences later).
- Replacing the foreground service when sessions are idle (still runs while sessions exist).

## Decisions

1. **Decision function (testable)**

```text
terminalWantsLock(session):
  if !wakeEnabled -> false
  if wakeAuto -> session.running && session.working
  else -> session.running   // latched (Termux-style)

holdWakeLock =
  any terminalWantsLock(liveSessions)
  || any AcpClient.keepsCpuAwake()
```

Remove `shellWorkingCount > 0` shortcut from `needsBackgroundLocks`.

2. **UI: `KeyRow` API**

Add parameters `wakeOn`, `autoOn`, `onToggleWake`, `onToggleAuto`. Render two `ModifierKey`s before the bottom row `ExtraKey` buttons. When `wakeOn` is false, visually de-emphasize **Auto** (still togglable; stored for when Wake is turned on).

Call sites pass `TerminalHost` getters/setters for the current `sessionKey` (`TerminalScreen`, `AgentAuxTerminals`).

3. **Persistence**

Store on `TerminalHost.Entry` or parallel `Map<sessionKey, WakePrefs>`. Clear when `closeSession` removes the entry. Defaults applied in `ensureStarted`.

4. **`syncKeepAlive` fix**

When `active < 0` or `working < 0`, run the same counting logic as `tick()` (or call a shared `snapshot(): KeepAliveSnapshot`) instead of substituting `0`. Same for `holdWakeLock` when omitted — derive from current terminals + ACP.

5. **Notification copy**

Extend `KeepAliveService.notification()` with a line built from:

- `locksHeld: Boolean`
- `terminalLatchCount` / `terminalAutoActiveCount` (optional detail)
- `agentHold: Boolean`

Keep strings in `strings.xml`; use simple patterns like “Wake lock: off”, “Wake lock: on (terminal)”, “Wake lock: on (agent)”.

6. **Settings phantom text**

Update `settings_phantom_desc` to mention terminal **Wake** / **Auto** on the key row instead of implying all shells auto-hold CPU on activity.

## Risks / Trade-offs

- **[Risk] Quiet long jobs with Auto on** — Lock may drop when output pauses. → Mitigation: user turns Auto off for latched mode.
- **[Risk] Latched mode left on** — Battery drain at idle prompt. → Clear notification state; Wake defaults off.
- **[Risk] Crowded key row on phones** — Two more keys on a scrolling row. → Short labels (“Wake”, “Auto”).

## Migration Plan

Ship in one release. No data migration. Users with open sessions get Wake off / Auto on defaults.

## Open Questions

_None for implementation._
