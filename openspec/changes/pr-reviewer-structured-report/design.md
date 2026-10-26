# Design

## Context

See `proposal.md` for motivation. PR Reviewer already provisions an isolated git copy and injects `PrReviewPrompt.prefix` on each turn (`AcpClient.extraPromptPrefix`). GitHub metadata today is title + head ref + url (`GitHubPrApi`). ACP `session/new` can attach MCP servers; TermFold has no other host-side tool registry. The agent’s review text is ordinary ACP `agent_message_chunk` items.

## Goals / Non-Goals

**Goals:**

- Give the agent a deterministic in-diff boundary (facts pack) without dirtying the review copy’s git status.
- Force a parseable report over ACP (the chat stream), then render cards.
- Keep wait-then-fix, with stable finding ids.

**Non-Goals:**

- New MCP server or Cursor MCP catalog entry.
- Line-by-line live checklist UI.
- Automatic web/docs verification.
- Running unit tests or lint as a review preflight.
- Pushing fixes or posting GitHub review comments.

## Decisions

### 1. ACP stream, not MCP (checked)

**Choice:** The agent puts one fenced JSON object in its ACP assistant message:

````text
```pr-review-report
{ ... }
```
````

`PrReviewAgentSessionScreen` (or a small helper used there) scans completed agent text for that fence, parses it, and shows cards. Prompt text describes the schema. No `submit_pr_review` MCP tool.

**Why:** TermFold’s ACP client does not register arbitrary host tools. Extra agent tools today are **MCP** via `session/new` `mcpServers`. A dedicated MCP server would work but is a second process, catalog row, and guest Node dependency. The user asked to stay on ACP; the assistant message *is* ACP. Parsing a labeled fence is enough to drive cards.

**Alternative considered:** MCP `submit_pr_review` — stricter schema, worse fit for “ACP only.” Revisit only if agents routinely omit the fence.

**Fallback:** If the fence is missing or JSON fails validation, keep the raw chat and show a “structured report missing” state. Do not invent findings from prose.

### 2. Facts pack lives in the copy but is git-excluded

**Choice:** After provision or branch switch, write:

- `.termfold-pr-review/pr.diff` — `git diff <merge-base>...HEAD`
- `.termfold-pr-review/files.txt` — `--name-only` list
- `.termfold-pr-review/pr.md` — title, url, body
- `.termfold-pr-review/comments.md` — existing issue + review comments (empty file if none)

Add `.termfold-pr-review/` to that copy’s `.git/info/exclude` so `git status` stays clean (Done / switch-PR dirty checks keep working).

Merge-base: `git merge-base origin/<default> HEAD` when the remote default exists, else `git merge-base HEAD origin/main` / `master` / GitHub `base.ref` from the PR API (prefer `base.ref` from fetch).

**Why:** The agent cwd is the copy; it can `read` those files. Putting them outside cwd makes them easy to miss. Putting them in-tree without exclude makes the copy always dirty.

**Alternative:** Prompt-only (no files) — PR diffs exceed prompt size. Exclude-dir is the compromise.

### 3. Extend GitHub fetch, not a second HTTP stack

**Choice:** Extend `GitHubPrApi` / `gh pr view --json` to include `body` and `baseRefName`. Fetch comments with `gh api` when `gh` works, else GitHub REST (`issues/{n}/comments` and `pulls/{n}/comments`). Unauthenticated HTTP already used as fallback; same error style as today.

**Why:** One bind path already talks to GitHub. Body + comments are the “intent” the structured review needs.

### 4. Report JSON schema (app-owned)

**Choice:** Versioned object, e.g. `schemaVersion: 1`:

- `summary` (string)
- `risk`: `low` | `medium` | `high`
- `findings`: array of `{ id, surface, severity, title, location, inDiff, evidence, suggestion }`
  - `surface`: `android` | `linux-guest` | `shared` | `ci-docs` | `ios`
  - `severity`: `blocker` | `should-fix` | `nit` | `question`
  - `inDiff`: boolean (`false` → out-of-diff card group)
  - `id`: stable token like `A-1`, `G-2`
- `coverage`: `{ reviewed: string[], skipped: string[] }` (paths)
- `missingTests` / `openQuestions`: string arrays

Prompt rules: one surface per finding; omit `ios` unless `files.txt` has iOS paths; do not use browser/web unless the user asked; after the JSON, ask which ids to fix; do not edit files until they answer.

**Why:** Cards map 1:1 to fields. Ids make “fix A-1” unambiguous.

### 5. Cards on the PR Reviewer screen, persist last report

**Choice:** Render cards above (or pinned in) the PR Reviewer chat, not as a generic `AcpItem` in all coding sessions. Persist the last valid parsed report on `PrReviewSession` (DataStore) so rotation / Done stub cwd still shows the last report.

**Why:** Coding chats should not grow a PR-only item type. Review sessions already have a dedicated screen.

**Alternative:** New `AcpItem.ReviewReport` in the shared timeline — more invasive for little gain.

### 6. Prompt is the protocol

**Choice:** Expand `PrReviewPrompt.prefix` with facts-pack paths, schema, surfaces, wait-for-ids, and “no web unless asked.” Keep injecting via existing `extraPromptPrefix`.

**Why:** v1 already used prompt-as-protocol; this only tightens the contract plus a parser.

## Risks / Trade-offs

- **[Agent omits the fence]** → Missing-report UI + prompt reminder; no silent prose parse.
- **[Huge diffs]** → Write full diff to the file; prompt says read the file, do not paste the diff into the prefix.
- **[merge-base wrong on shallow copies]** → Prefer GitHub `base.ref`; if diff empty, facts pack records that and the prompt says so.
- **[Comments truncated]** → Cap comment body length in `comments.md`; note truncation in the file header.
- **[JSON in a thought block]** → Parse only non-thought agent message text.

## Migration Plan

Existing review sessions keep working; new turns get the richer prefix. Old chats have no stored report until a new parse succeeds. Rollback is prefix + unused facts dir + unused card composable.

## Open Questions

None that change specs or tasks. Fence vs MCP can be revisited later if parse miss rate is high.
