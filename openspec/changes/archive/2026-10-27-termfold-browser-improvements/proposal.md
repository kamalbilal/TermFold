# Proposal

## Why

Agents drive TermFold’s preview WebView through `termfold-browser` / MCP. Modern sites (shadow DOM, iframes, custom comboboxes) and flaky timing still cause silent failures; agents also lack structured “page health,” safer navigation bounds, and UX that helps humans trust or override automation. We should close those gaps while keeping viewport-first, token-cheap snapshots as the product advantage—not chasing full desktop CDP parity.

## What Changes

- **DOM reach**: Snapshot and act inside **open shadow roots** (depth-capped) and **named iframes** (same-origin first; clear errors for cross-origin).
- **Waits**: `wait` gains **URL pattern**, **network idle** (no in-flight fetch for N ms), and optional **reload settled** after HMR/full navigation.
- **Agent ergonomics**: **`browser_status`** (or extended `status`) returns structured JSON: URL, title, viewport mode, WebView/engine summary, console error count, failed request count, visible control count.
- **Token efficiency**: Document and stabilize **`snapshot --changed`**; optional **`browser_health`** one-shot summary for post-action checks; encourage element-scoped screenshots in skill/MCP text.
- **Refs**: Snapshot lines include a **stable hint** (role+name+region) in the full `snapshot.txt` file; refs still reset per navigation but agents can re-find via hint/`--find`.
- **Comboboxes**: Snapshot marks **expanded/collapsed** for `[role=combobox]` and listbox patterns; skill guidance for multi-step pick.
- **Dev workflow**: Detect common dev ports in guest and surface **“Open preview”** / remember last preview URL per project; optional auto-open when agent starts a known dev command (heuristic, not magic).
- **Human UX**: Preview toolbar **action log** (last N agent steps); **shared control** (user input without full pause when idle); clearer **Open in system browser** when WebView limits hit.
- **Security**: Settings **navigation allowlist** (localhost, project file host, user-approved hosts); block agent `open` to other origins with a clear error; password-on-remote-site warning unchanged.
- **Privacy**: **Clear site data** action for current origin (cookies/storage) from preview menu.
- **Observability**: Network log **`--since`** action index or timestamp; optional **export failed requests** as JSON; correlate last console errors with last failed request in `browser_health`.
- **Visual checks**: **`screenshot --compare`** against a project baseline path (perceptual hash / existing `pictureHash`); report match/mismatch without failing the tool.
- **Performance**: MCP screenshots use **JPEG size/quality cap**; defer snapshot until WebView **resumed** after minimize/pause.
- **Multiple tabs**: Tab strip in preview; agent tools to **list, open, switch, and close** tabs; `target=_blank` and `window.open` open a **new in-app tab** (within a cap); snapshots and actions target the **active tab** unless a tab id is given.
- **CAPTCHAs**: **Detect** common challenge UI (reCAPTCHA/hCaptcha/Cloudflare-style widgets and challenge copy); **pause** agent commands and **prompt the user** to solve on device; **`wait` for challenge cleared** before continuing. No third-party solvers and no AI “solve CAPTCHA” automation.
- **Documentation**: Update bundled **`termfold-browser-skill.md`** and MCP tool descriptions for all of the above.

**Out of scope**: External Chrome/CDP bridge, automated CAPTCHA bypass (solvers, model guessing, farming tokens), persisting agent allowlist across uninstall, full HAR export to disk.

## Capabilities

### New Capabilities

- `agent-preview-browser`: Agent-driven preview browser automation, waits, security, observability, and human override UX.

### Modified Capabilities

- `bundled-linux-guest`: Bundled agent skill material SHALL document the expanded browser automation surface and phone-oriented testing flow.

## Impact

- `BrowserDriver.kt`, `BrowserBridge.kt`, `PreviewPane.kt` (tab strip, per-tab WebView lifecycle), `Preview.kt` / file host
- `app/src/main/assets/termfold-browser.js`, `termfold-browser-skill.md`
- `SettingsScreen.kt` (navigation allowlist)
- Unit tests: snapshot helper behavior (shadow/iframe) where testable; driver wait/health JSON; allowlist normalization
- No change to ACP wire protocol; MCP tool list grows (backward compatible for agents that ignore new tools)
