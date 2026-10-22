# Design

## Context

See `proposal.md` — Why. Today `AcpClient` stores hidden wire ids under `acp_deleted_sessions` with key `agent.id + "|" + workspaceGuestDir`, filters `session/list` to four pages, and hides ids on wire resync via `hideFromPastList`. TermFold session tabs already own timelines on disk (`sessionKey`) and remembered wire ids in `acp_sessions` prefs.

## Goals / Non-Goals

**Goals:**

- Per-tab hidden-id storage with a one-time migration from workspace-wide keys.
- Exhaustive `session/list` paging with a hard cap to avoid unbounded RPC.
- Hide previous wire id on `startNewSession()` for the active tab only.
- Small UI string clarifying tab timeline vs Earlier sessions list.
- JVM unit tests for keying and list merge/filter helpers.

**Non-Goals:**

- Changing checkpoint undo/redo semantics or wire resync summary injection.
- Syncing TermFold tabs with Cursor's session titles or merging timelines across tabs.
- Server-side or Cursor agent changes to `session/list` page size.
- Showing the active wire session in Earlier sessions when it is intentionally hidden after resync (tab timeline remains source of truth).

## Decisions

**1. Hidden-id prefs key shape**

- **Choice:** `deletedKey() = agent.id + "|" + workspaceGuestDir + "|" + sessionKey` (use the same `sessionKey` string as timelines and `acp_sessions`).
- **Rationale:** Matches user mental model (one tab = one hide list); minimal code surface.
- **Alternative:** Separate prefs file per tab — rejected as unnecessary.

**2. Migration from workspace-wide keys**

- **Choice:** On first `listPastSessions` or client `start()` after upgrade, if the legacy key exists and the new per-tab key does not, copy the legacy set into the per-tab key once, then leave legacy key in place (orphaned) to avoid cross-tab re-hide on read.
- **Rationale:** Users who already hid clones on one tab keep those hides on that tab only after upgrade; other tabs start with empty per-tab set unless they had their own operations post-upgrade.
- **Alternative:** Union legacy into every tab — rejected; would reproduce the bug.

**3. Pagination cap**

- **Choice:** Loop until `nextCursor` is blank or `MAX_LIST_PAGES = 32` (tunable constant), with `distinctBy { sessionId }` as today.
- **Rationale:** Removes the current hard stop at 4 pages; 32 pages is a safety valve against a broken agent cursor loop.
- **Alternative:** Unlimited loop — rejected without timeout.

**4. `startNewSession` hide**

- **Choice:** Before clearing timeline and calling `openSession()`, capture `agentSessionId` and call `hideFromPastList(oldId)` if non-blank.
- **Rationale:** Aligns list with "new chat" without deleting agent files unless user explicitly deletes.

**5. UI copy**

- **Choice:** One string resource `acp_history_explainer` shown when the dialog has loaded (empty or not).
- **Rationale:** Low noise; ADHD-friendly single sentence.

## Risks / Trade-offs

- **[Risk] Legacy migration confuses users who relied on workspace-wide hides** → Mitigation: only copy legacy → current tab on first access; document in release notes.
- **[Risk] Large session histories slow the dialog** → Mitigation: page cap; list loads once per open (existing behavior).
- **[Risk] `sessionKey` characters in prefs keys** → Mitigation: reuse existing sanitization pattern from `AcpTimelineStore` if key length limits bite (unlikely).

## Migration Plan

1. Ship app update; migration runs lazily per tab on client start or first history open.
2. No server migration; prefs-only.
3. Rollback: older app ignores new keys and reverts to legacy workspace key behavior (acceptable short window).

## Open Questions

None — cap values and exact explainer string can be tuned during implementation without spec changes.
