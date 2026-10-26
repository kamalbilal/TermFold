# Design

## Context

See `proposal.md` for motivation and `specs/` for behavior. Preview HTTP(S) already targets Gecko (`GeckoBrowserSession`, built-in `gecko-termfold` extension, native messaging). `BrowserDriver` exposes MCP actions including `network`, but Gecko `networkText` is a stub. WebView `shouldInterceptRequest` only logs URLs and serves `files.termfold`. Rules must key off existing browser session keys (`coding-…`, `auto-…` in `PreviewUrlRouter` / `BrowserSessionRegistry`).

## Goals / Non-Goals

**Goals:**

- Chrome-like page semantics: block = cancel; override = same request URL, stub body (+ optional status/response headers).
- Blocking listeners run inside the extension (no Kotlin round-trip on each request).
- One rule store per bound session, shared by MCP and the Network rules screen.
- Restore Gecko request logging as a side effect of the same webRequest hooks.

**Non-Goals:**

- Dual intercept on System WebView (Gecko is the HTTP engine).
- Chrome Sources folder / persist override files in the project tree.
- Request-header match, throttle, replay.
- Guaranteeing the TCP connection to origin is never opened if Gecko still starts the channel after `filterResponseData` is attached (page-visible URL and body still match Chrome).

## Decisions

### 1. Intercept in the Gecko WebExtension, not page JS

**Choice:** Add `webRequest`, `webRequestBlocking`, `webRequestFilterResponse`, and `<all_urls>` (or equivalent host permission) to `gecko-termfold`. Background `onBeforeRequest` (blocking): cancel on block; on override, `filterResponseData` and write the stub, then close. `onHeadersReceived` (blocking) applies optional response-header overrides (CORS).

**Alternatives:** `window.fetch` patch (rejected — not real intercept); WebView `shouldInterceptRequest` (wrong engine); mitmproxy (not in-app).

**Rationale:** Mozilla’s GeckoView answer for intercept is webRequest. Page URL stays the original request URL, unlike `redirectUrl` to `moz-extension:` or `data:` (CORS break).

### 2. Rules live in Kotlin; a copy is pushed to the extension

**Choice:** `NetworkRuleStore` keyed by browser `session_key`. JSON on disk under app files (`network-rules/<sessionKey>.json`). On add/remove/clear and on session register, native-message the **full ordered list** to that session’s extension background. Blocking path never waits on Android.

**Alternatives:** Match only in Kotlin (too slow / racy for blocking); store only in extension (UI/MCP and session delete become messy).

**Delete session:** hook the same path that unregisters `BrowserSessionRegistry` entries and delete that file.

### 3. URL glob in one shared matcher

**Choice:** Chrome-like `*` / `?` on the full URL string (`*` → `.*`, `?` → `.`, other regex metacharacters quoted). Same algorithm in Kotlin (unit-tested) and a small copy in `background.js`. First matching rule in list order wins (block vs override are one list). New rules append.

**Alternatives:** Full URL Pattern API (unnecessary); request-header match (out of spec).

### 4. One driver action / MCP tool for rules

**Choice:** Driver action `network_rules` with `op`: `list` | `add_block` | `add_override` | `remove` | `clear`. MCP name `browser_network_rules`. CLI: `termfold-browser network-rules …`. `session_key` already merged in `termfold-browser.js` `call()`.

`add_override` args: `url_pattern`, `body` (string), optional `status` (default 200), optional response header map. `add_block`: `url_pattern`. `remove`: rule `id`. Body cap (e.g. 256 KiB) with a clear error.

**Alternatives:** Separate MCP tools per op (noisier); agent writes files the extension watches (extra moving parts).

### 5. Network log from webRequest

**Choice:** Extension reports request start/finish/error/blocked/overridden over the native port; Kotlin appends entries used by existing `network` / `health`. Marks: `blocked:devtools` and overridden. Replaces the Gecko stub in `BrowserSessionRegistry`.

**Alternatives:** GeckoView NavigationDelegate only (misses XHR/fetch).

### 6. UI is a full-screen overlay, not a new app destination

**Choice:** Preview ⋮ → Network rules. Compose screen covering the preview pane (back returns to the page), bound to the **currently attached** session key. Two sections: Blocked, Body overrides (pattern + truncated body). Delete icon per row; Clear all. Empty copy when unbound or no rules. Creating rules is agent-only.

**Alternatives:** Console-style bottom drawer (too small for bodies); settings (global, wrong).

### 7. Persistence vs Chrome DevTools

**Choice:** Rules persist for the TermFold session id (survive preview hide and process-friendly session restore). They do **not** apply when that session is gone. Unbound casual preview has no rules.

**Alternatives:** App-global prefs (rejected); wipe on every preview close (fights “reopen same session”).

## Risks / Trade-offs

- **[Risk] Gecko still contacts origin on override** → Page still sees stub at original URL; document as engine limit; prefer `filterResponseData` without forwarding bytes.
- **[Risk] CORS / preflight** → Optional response headers on override; OPTIONS may need a pass-through or stub if browsers preflight; test a cross-origin fetch in apply.
- **[Risk] Service workers** → Add `webRequestFilterResponse.serviceWorkerScript` if SW bypasses; if still bypassed, document.
- **[Risk] Rule/extension desync** → Always push the full list; on port reconnect, push again.
- **[Risk] Large stub bodies** → Hard cap; truncated preview in UI/MCP list.
- **[Trade-off] Duplicate glob in JS and Kotlin** → Keep the Kotlin tests as source of truth; add a few JS-facing fixture strings if a mismatch shows up.

## Migration Plan

Ship with the next debug APK. Existing MCP clients ignore the new tool. Empty rule files are a no-op. Rollback: revert extension permissions and driver action; leftover JSON files are inert.

## Open Questions

None that change specs. Service-worker permission can be added during apply if a spike shows SW bypass.
