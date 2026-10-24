---
name: termfold-browser
description: Use TermFold's built-in browser to open, look at, click through and test web pages — local dev servers (localhost), HTML files in the project, or any site. Use it after building or changing anything that renders in a browser, to check it really works, to reproduce a UI bug, or when the user asks to see, test or screenshot a page.
---

# TermFold browser

TermFold (the Android app this Linux environment runs in) has a real browser beside the chat.
You can drive it; the user watches you do it. Use it to check your web work instead of guessing.

With MCP tools available (`browser_open`, `browser_snapshot`, `browser_click`, ...) use those.
Otherwise use the `termfold-browser` command in the shell:

```sh
termfold-browser open 5173                 # a dev server on localhost:5173 (or a URL, or ./index.html)
termfold-browser snapshot                  # numbered buttons, links, fields on screen: [12] button "Sign up"
termfold-browser snapshot --find "sign up" # one control, wherever it is on the page
termfold-browser snapshot --changed        # only what differs from the last snapshot
termfold-browser outline                   # just the headings (the shape of a long page)
termfold-browser click 12                  # tap by number (or: click "Sign up")
termfold-browser fill 15 "ada@example.com" # replace a field's text; add --submit to press Enter
termfold-browser select 18 "India"         # choose in a dropdown
termfold-browser check 21                  # tick a checkbox (--off to untick)
termfold-browser press Enter               # keys: Tab, Escape, ArrowDown, Control+A ...
termfold-browser scroll down               # or: up, top, bottom, or an element number
termfold-browser wait "Order confirmed"    # wait for text to appear
termfold-browser screenshot                # temporary file under /tmp; prints the path (--save keeps it in the project)
termfold-browser screenshot 12 --if-changed # one element; skipped if the page looks the same
termfold-browser console --errors          # JavaScript errors
termfold-browser network --failed          # failed requests (404, 500, CORS...)
termfold-browser viewport phone            # phone | desktop | fit
termfold-browser eval "document.title"     # run JavaScript in the page
```

## How to work

1. Start the dev server in the background first (`npm run dev > /tmp/dev.log 2>&1 &`), then
   `open` its port. Static sites can be opened as files directly (`open ./index.html`); they
   reload by themselves when files change.
2. `snapshot` before acting; the numbers come from it. Afterwards use `snapshot --changed` rather than a full one.
   Every snapshot is short on purpose (on screen only). The full list is saved to
   `/tmp/termfold-browser/snapshot.txt`: `grep -i word` it, or use `--find`, instead of asking for everything.
   For reading content use `text --selector <css>` or `outline`, not the whole page.
3. After each change you make, look: `console --errors` first (it is nearly free), then `screenshot --if-changed`
   (then read the image file, only if it made one). Fix what is wrong and check again. Screenshots are temporary;
   use `--save` only when the user wants one kept in the project.
4. Test like a user: click through the flow, fill forms with realistic test data, submit, and
   check the result. Try `viewport phone` for layouts.
5. Tell the user what you checked and what you saw.

## Phone performance (lint and npm)

TermFold runs Debian under PRoot on the device CPU — not the GPU. Repeated `npm run lint` is
much faster when ESLint keeps a cache and when type-checking rules are not in the daily script.

- Add `--cache` (and a stable `--cache-location` under `node_modules/.cache` if you like) to
  `npm run lint`.
- Keep a fast `lint` for day-to-day work; run type-aware ESLint or `tsc --noEmit` as `lint:ci`
  before you push.
- In monorepos on the phone, try lower concurrency (`turbo run lint --concurrency=1`).
- Run `termfold-bench` in the shell to see node spawn time and GuestCompat migration status.

## Rules

- For testing, use obvious test values, not real card numbers or personal data.
- You may type a password the user gives you. When it is for a real website, tell the user it
  passed through you (the AI) and recommend they change it after this session.
- CAPTCHAs: ask the user to solve on screen; use `browser_wait` with captcha_cleared or wait until health shows captcha_pending false. Never use automated solvers.
- If a command says the user paused agent control, stop and ask them.
- Screenshots are of the visible area; scroll to see more.
