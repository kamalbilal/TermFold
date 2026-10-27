# Design

## Context

See `proposal.md` — Why. `AcpClient.startNewSession()` currently calls `hideFromPastList(agentSessionId)` before clearing the tab timeline and `session/new`. That was added in the 2026-10-23 session-history change to avoid a “ghost” of the abandoned chat in Earlier sessions. Users instead treat **Sessions in this folder** as Cursor’s thread list for the project; **New session** should start a fresh tab timeline while leaving the prior wire thread listable. Hidden-id storage remains per TermFold tab (`agent|workspace|sessionKey`). Wire resync still uses `retireWireSession()` + `hideFromPastList(newClone)` and is out of scope for behavior change.

## Goals / Non-Goals

**Goals:**

- Remove outgoing-wire hide from `startNewSession()` only.
- Keep delete + resync hiding semantics and per-tab prefs keys.
- Update explainer string and refresh list after new session so the UI reflects the prior thread immediately.
- Regression tests on visibility helpers and client behavior.

**Non-Goals:**

- Restoring the cleared local timeline when reopening a past session (still agent replay via `openPastSession`).
- Un-hiding wire ids users already hid via New session on older app versions (prefs are sticky).
- Multi-tab TermFold session management or syncing titles with Cursor.
- Calling `session/delete` on the outgoing wire when starting new (leave thread on agent for list/reopen).

## Decisions

**1. Do not hide on New session**

- **Choice:** Delete the `wireSessionToHideBeforeNew` → `hideFromPastList` call in `startNewSession()`. Optionally remove the helper if unused elsewhere (still used only here today — can delete or keep for documentation).
- **Rationale:** `session/list` is the source of truth for reopenable threads; hiding was a product mistake for explicit New session.
- **Alternative:** Hide only when local timeline is empty — rejected; users still want empty wire threads listable.

**2. List refresh**

- **Choice:** After `startNewSession()` completes `openSession()`, if the history dialog is not shown, no work. When dialog is open, dismiss already happens; on next open `LaunchedEffect` refetches. Add `LaunchedEffect(currentId)` or explicit `listPastSessions()` after new session if we keep dialog open in future — for now, refetch when dialog opens using `LaunchedEffect(client.currentSessionId)` keyed on wire id change so reopening dialog or staying open shows updated list.
- **Rationale:** Cheap IO; avoids “never came back until restart” perception.

**3. UI copy**

- **Choice:** Update `acp_history_explainer` to say New session clears **this tab’s** chat locally; earlier threads stay in the list below.
- **Rationale:** Sets expectation; ADHD-friendly one sentence.

**4. Spec delta**

- **Choice:** MODIFY requirement in `acp-past-sessions` — replace hide scenario with keep-visible scenario. Add note in checkpoint delta that resync hide is unchanged.

## Risks / Trade-offs

- **[Risk] List shows many abandoned threads** → Mitigation: user can delete; search/filter unchanged; matches Cursor desktop behavior.
- **[Risk] Current wire session appears twice (list + tab)** → Mitigation: “open now” badge on current id unchanged; current thread not duplicated as a second tab entry.
- **[Risk] Users who liked auto-hide cannot opt in** → Mitigation: explicit Delete remains; no setting in v1.

## Migration Plan

- Ship app update; no prefs migration. Previously hidden ids stay hidden.
- Archive change and sync spec to main `openspec/specs/acp-past-sessions/spec.md`.

## Open Questions

_None._
