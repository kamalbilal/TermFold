# Design

## Context

See `proposal.md` (Why). Preview HTTP(S) uses Gecko with built-in `gecko-termfold` (`webRequest`, native messaging). `browser-session-network-rules` added block/override and a thin `GeckoNetworkLog` (method, URL, outcome). Agents still cannot read headers or bodies; users have no Network panel. Console drawer + `GeckoConsoleLog` pattern exists for a similar UI/subscribe model.

## Goals / Non-Goals

**Goals:**

- Passive capture per bound session into one Kotlin store (UI + MCP share it).
- Performant mobile UI: virtualized list, lazy-loaded bodies, disk-backed payloads.
- Chrome-like filters (XHR/Fetch focus, type chips, failed, search) and **Preserve log** (default off).
- Token-efficient agent API: summary list + on-demand detail with caps.

**Non-Goals:**

- Eruda / in-page DevTools; WebSocket v1; HAR export v1; header redaction v1; capture survive app restart; WebView engine.

## Decisions

### 1. Observe path in extension (parallel to rules)

**Choice:** Add non-blocking `webRequest` listeners that emit `type: "network_capture"` events with `requestId`, phases (headers, complete, error), and optional body chunks. For XHR/Fetch (and `xmlhttprequest` / `fetch` resource types), attach `filterResponseData` as a **tee**: write bytes to native in chunks, then `filter.write` unchanged to the page.

**Alternatives:** Page JS hooks (rejected — incomplete headers, CSP); devtools.network API (requires DevTools UI, not GeckoView-embedded).

**Body policy:** Capture request/response bodies only for XHR/Fetch when content-type is text/json or missing (treat as text). Cap per body (e.g. 256 KiB store, 6 KiB MCP detail). Images/fonts/etc.: list row only.

### 2. `NetworkCaptureStore` replaces agent-facing log

**Choice:** Single store keyed by `sessionKey` with monotonic `entryId` and `cursor` for `since_cursor`. Merge rule outcomes (blocked/overridden/failed) into entries. Ring limit ~500 entries; drop oldest. Bodies in `filesDir/network-capture/<sessionKey>/<entryId>/`.

**Preserve log:** Boolean per session. When **false**, clear store on `main_frame` navigation start (same session). When **true**, keep entries across reloads. **App process death** always clears (no disk restore of capture index).

**Alternatives:** Keep `GeckoNetworkLog` separate (rejected — duplicate rows for agent).

### 3. Native UI (drawer + sheet)

**Choice:** Preview toolbar Network icon → bottom drawer (LazyColumn, filter chips, search field, Preserve toggle, Clear). Row tap opens Material3 bottom sheet with tabs: Overview, Headers (req/resp), Payload, Response, Timing (duration ms v1). Subscribe to store on main thread like console listener.

**Alternatives:** Full-screen only (rejected — user chose drawer + detail sheet).

### 4. Agent tools

**Choice:**

- `browser_network` — `op`: `list` (default) | `preserve` | `preserve_status`. List args: `since_cursor`, `failed_only`, `xhr_only`, `url_contains`, `limit` (default 40, max 80). Returns compact text/JSON lines: id, method, status, path, type, ms, size.
- `browser_network_detail` — `entry_id`, `part`: `overview` | `headers` | `request_body` | `response_body`. Truncate body in MCP output to 6 KiB with total size note.

**BREAKING:** Old plain URL list becomes summary list; skill updated.

**Preserve:** `browser_network` with `op: preserve` and `enabled: true|false`; read `preserve_status`.

### 5. Performance

- Extension: do not tee bodies for non-XHR types.
- Kotlin: parse JSON for UI on background dispatcher; list holds metadata only.
- Clear body files when entry evicted or session cleared.

## Risks / Trade-offs

- **[Risk] `filterResponseData` cost on busy pages** → XHR-only tee; cap bodies; evict old entries.
- **[Risk] CSP / SW bypass** → Document limits; same as network rules.
- **[Risk] Preserve on + long sessions** → Agent skill enforces filters/cursor; UI search.
- **[Risk] Full headers in MCP leak tokens** → User chose full debug; skill still avoids bulk dump.
- **[Trade-off] Breaking `browser_network` shape** → Version skill + one release note.

## Migration Plan

1. Ship capture + store + extension events behind existing preview sessions.
2. Switch `BrowserDriver` `network` to new list; add `network_detail`.
3. Add UI drawer; deprecate reading-only network text from old log format in docs.
4. Rollback: revert driver tools; extension can stop emitting capture events without breaking rules.

## Open Questions

- Exact filter chip set for v1 (Doc/CSS/JS/Img) — implement minimum XHR + All + Failed + search; add chips incrementally if needed.
