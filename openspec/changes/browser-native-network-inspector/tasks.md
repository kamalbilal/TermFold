# Tasks

## 1. Capture store and extension events

- [x] 1.1 Add `NetworkCaptureStore` (entry ids, cursor, preserve flag, ring limit, body files under session key) and unit tests for clear-on-nav when preserve off
- [x] 1.2 Extend `gecko-termfold` with passive capture listeners and native `network_capture` messages; tee XHR/fetch bodies with pass-through; verify blocked/overridden still merge into entries
- [x] 1.3 Wire `GeckoTermfoldExtension` ingest into store; remove or delegate old `GeckoNetworkLog` agent list path; verify headless session sees same list text

## 2. Agent tools

- [x] 2.1 Implement `BrowserDriver` network ops: list (summary, since_cursor, filters, limit 40), preserve on/off/status; verify unit tests for formatting and truncation constants
- [x] 2.2 Add `network_detail` action with parts and 6 KiB MCP body cap; verify driver tests for invalid id and truncated body message
- [x] 2.3 Update `termfold-browser.js` MCP tools + CLI (`network list`, `network detail`, `network preserve`); verify `termfold-browser mcp` lists new/updated tools

## 3. Native UI

- [x] 3.1 Add Preview Network toolbar entry, drawer (LazyColumn, chips: All/XHR/Failed, search, Preserve, Clear) subscribed to store; verify manual open on device/emulator
- [x] 3.2 Add detail bottom sheet tabs (Overview, Headers, Payload, Response, Timing); lazy-load bodies from disk; verify tap row shows headers for a test fetch page
- [x] 3.3 Add strings and theme consistent with Console drawer; verify no regression to Network rules overlay route

## 4. Docs and skill

- [x] 4.1 Update `termfold-browser-skill.md` with summary-first network debugging, preserve log, detail tool, token guidance; verify skill mentions breaking list shape
- [x] 4.2 Sync bundled asset copy used by guest setup if separate from skill path

## Workflow follow-up

- Archive after review alongside or after `browser-session-network-rules` if still open.
- On-device verify preserve on keeps entries across reload and preserve off clears on navigation.
