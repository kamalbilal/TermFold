# Tasks

## 1. Rule store and URL matching

- [x] 1.1 Add `NetworkRuleStore` keyed by browser `session_key` (JSON under app files) with add_block, add_override (body cap), remove, list, clear, and delete-on-session-end; verify unit tests for isolation (A vs B), restore by same key, and delete
- [x] 1.2 Implement shared URL glob (`*` / `?`, quoted metacharacters) and first-match-wins on an ordered rule list; verify unit tests for exact URL, `https://api.foo.com/v1/*`, and block-before-override order

## 2. Gecko webRequest intercept

- [x] 2.1 Extend `gecko-termfold` manifest with webRequest permissions and host access; verify the extension still loads (`ensureBuiltIn` / native port connects)
- [x] 2.2 Implement `background.js` blocking listeners (cancel block; `filterResponseData` stub at original URL; optional response headers) plus glob copy; verify a local test page fetch is cancelled vs stubbed without wrapping `window.fetch`
- [x] 2.3 Push the full rule list over native messaging on store change, session register, and port reconnect; verify changing rules in Kotlin updates intercept without restarting Gecko

## 3. Network log and driver/MCP

- [x] 3.1 Report Gecko webRequest start/finish/error/blocked/overridden to Kotlin and replace the Gecko `networkText` stub; verify `browser_network` lists a fetch and marks blocked/overridden entries
- [x] 3.2 Add driver action `network_rules` (`list` | `add_block` | `add_override` | `remove` | `clear`) scoped to `session_key`; verify ops mutate only that session’s store
- [x] 3.3 Wire MCP `browser_network_rules` and CLI `network-rules` in `termfold-browser.js`; verify `mcp` tools/list includes the tool
- [x] 3.4 Document session isolation, URL wildcards, and “no fetch patch” in bundled skill (`termfold-browser-skill.md` / assets); verify the skill text covers block, override, and list

## 4. Network rules screen

- [x] 4.1 Add Preview More-menu item **Network rules** opening a full-screen overlay for the attached session; verify empty state when unbound or no rules
- [x] 4.2 List blocked patterns and body overrides (truncated body), per-row delete, and clear all against `NetworkRuleStore`; verify delete/clear update the list and the next page request is unblocked/un-stubbed
- [x] 4.3 Add strings for the menu, screen title, sections, empty copy, and clear all; verify they appear in the overlay

## 5. Integration

- [x] 5.1 Two bound sessions: different override bodies for the same pattern; verify each page only sees its own stub
- [x] 5.2 Agent add_block then reload; verify fetch fails and the UI list shows the pattern; user delete; verify fetch succeeds
- [x] 5.3 Cross-origin fetch override with CORS response headers if a fixture needs it; verify the page can read the stub or document the preflight limitation
