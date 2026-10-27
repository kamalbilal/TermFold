# Proposal

## Why

Agents and users need a **native** Network experience in TermFold’s Gecko preview—request list, headers, payloads, and responses—with Chrome-like filters and **Preserve log**, not a URL-only text dump. Today `browser_network` lists method/URL/outcome only; there is no inspectable detail, no in-app Network panel, and no token-efficient way for agents to debug APIs after navigation.

## What Changes

- **Passive capture (Gecko extension):** Observe HTTP(S) traffic for the bound session: stable entry ids, method, URL, resource type, status, request/response headers, timing, and bodies for **Fetch/XHR** (JSON/text, size-capped). Other resource types appear in the list without body capture. Capture is separate from block/override rules.
- **Session store:** In-memory ring buffer per browser session key; **no restore after app restart**. Bodies stored on disk under the session with caps; list metadata stays lean for UI performance.
- **Preserve log:** Toggle (default **off**). Off: clear capture on main navigation/reload. On: keep entries across reloads for cross-page debugging. User and agent can set the flag.
- **Native UI:** Preview **Network** entry—bottom **drawer** (virtualized list, filter chips, search, Preserve, Clear) and **detail bottom sheet** (Overview, Headers, Payload, Response, Timing v1 = total duration). Full debug visibility for headers (no redaction v1).
- **Agent tools (low tokens):** **BREAKING** upgrade `browser_network` to summary **list** with `since_cursor`, filters, and preserve control; new `browser_network_detail` for one entry/part with truncated bodies in MCP text. Skill documents summary-first workflow.
- **WebSockets:** Out of scope for v1 (future).

**Out of scope:** Eruda or other in-page DevTools inject; WebSocket frames v1; HAR file export v1 (optional later); request-header-based rules; masking Authorization/Cookie v1; persisting capture across app kill; System WebView engine.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `agent-preview-browser`: Native Network drawer/sheet, preserve log, capture-backed `browser_network` + `browser_network_detail`, token limits.
- `gecko-preview-engine`: Extension passive capture pipeline into Kotlin store (tee bodies where needed).
- `bundled-linux-guest`: Skill/MCP docs for list/detail/preserve and agent debugging flow.

## Impact

- `gecko-termfold` extension: capture listeners + native events (distinct from rule intercept)
- New Kotlin modules/types: `NetworkCaptureStore`, persistence files, integration with `GeckoTermfoldExtension`
- `PreviewPane.kt` Network UI; `BrowserDriver.kt` / `termfold-browser.js` tool shapes
- `GeckoNetworkLog` may be superseded or merged into capture store for agent `network` output
- Unit tests: store, preserve behavior, list/detail formatting, body caps
- MCP: breaking change to `browser_network` response shape; new `browser_network_detail`
