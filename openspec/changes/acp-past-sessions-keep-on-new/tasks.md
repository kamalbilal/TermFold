# Tasks

## 1. Client behavior

- [x] 1.1 Remove `hideFromPastList` (and `wireSessionToHideBeforeNew` call) from `AcpClient.startNewSession()`; leave `retireWireSession` / resync hides unchanged
- [x] 1.2 Confirm `openSession()` after new session does not hide the new wire id except in `wireResync` path

## 2. UI

- [x] 2.1 Update `acp_history_explainer` in `strings.xml` per spec (local tab vs list; New session keeps prior threads listable)
- [x] 2.2 Refresh past sessions when wire id changes: e.g. `LaunchedEffect(currentId)` in `PastSessionsDialog` calling `listPastSessions()` so list updates after New session

## 3. Tests

- [x] 3.1 Update/remove tests that expected hide-on-new-session; add test that `startNewSession` does not add outgoing id to hidden set (extract test hook or prefs inspection as today)
- [x] 3.2 Keep existing resync/delete visibility tests green

## 4. Verify

- [x] 4.1 Manual: two chats in same folder tab — New session — open Sessions dialog — prior title visible and reopenable
- [x] 4.2 Manual: undo/wire resync still does not pollute list with summary clone (existing behavior)
