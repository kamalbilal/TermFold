# Proposal

## Why

Agents and users need Chrome DevTools-style request blocking and response-body overrides in TermFold’s in-app browser so they can test missing APIs and mocked JSON without `fetch` hacks. Today `browser_network` is a URL log only (empty on Gecko), with no intercept, no per-session rules, and no screen to inspect or delete them.

## What Changes

- **Engine intercept (Gecko):** Built-in WebExtension `webRequest` / `webRequestBlocking` / `webRequestFilterResponse` so the engine can cancel requests or serve a local body (and optional response headers) at the **original URL** — not page JS overrides.
- **Chrome-like block:** URL pattern (`*` / `?`); cancel; page `fetch` fails; Network log status like `(blocked:devtools)`; origin is not used for the resource.
- **Chrome-like override body:** Same URL-pattern matching (exact URL or base like `https://api.foo.com/v1/*`); page receives the stub body (optional status + response headers, e.g. CORS). Semantics: local resource instead of the network resource; same request URL for the page.
- **Session-scoped rules:** Rules keyed by the bound browser session (`coding-…` / `auto-…`). Session A’s blocks/overrides never apply to session B. Same session reopened keeps its rules; deleting the session deletes them. No app-global rule set.
- **Agent control:** MCP/CLI to add, list, and clear block and override-body rules for the **current** session only. Agents pick URLs from a working network log (Gecko must record requests, including blocked/overridden).
- **Human UI:** Preview **More (⋮)** menu → **Network rules** full screen: blocked patterns, body overrides (pattern + truncated body), per-rule delete, clear all. Users do not create rules here; agents do.
- **Docs:** Bundled `termfold-browser` skill/MCP descriptions for the new tools.

**Out of scope:** Request-header matching (Chrome does not); request-header override; throttling; Edit and Resend; Sources folder picker; WebSocket frame rewrite; System WebView as the intercept engine; automated CAPTCHA; HAR export.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `agent-preview-browser`: Session-scoped network block/override rules, agent tools, network log markers, and the Network rules screen.
- `gecko-preview-engine`: Gecko WebExtension intercepts HTTP(S) with `webRequest` so block/override apply to the bound Gecko profile.
- `bundled-linux-guest`: Skill material documents network rules tools and session isolation.

## Impact

- `gecko-termfold` extension (`manifest.json`, `background.js`), native messaging to push per-session rules
- `BrowserDriver.kt` / `BrowserBridge.kt` / `termfold-browser.js` new actions; Gecko `networkText` no longer a stub
- Rule store keyed by browser session key; persist with session lifetime
- `PreviewPane.kt` ⋮ menu + Network rules screen
- Unit tests for URL pattern matching, session isolation, first-match-wins; extension/driver tests where feasible
- MCP tool list grows (backward compatible)
