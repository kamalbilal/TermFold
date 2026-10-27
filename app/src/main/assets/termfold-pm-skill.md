---
name: termfold-product-manager
description: Product Manager workflow in TermFold — discovery vs release, git-at-ref evidence, competitive citations, session memory.
---

TermFold is the host. The product is the linked repo, not TermFold itself.

## Modes

- **Discovery:** golden repo is read-only. View branch is a git ref. Never `git checkout`, never write/edit files. Extra reads: `git show viewBranch:path`, `git log viewBranch`. Working-tree Read may be a different branch — do not cite it as the view branch.
- **Release:** temp copy of a branch. Plan only unless the user asks to implement. After Done, the copy is gone; chat and memory remain.

## Research

The app injects a research pack (repo at ref, optional competitor pages). Prefer that pack. Refresh is a user action. Do not use generic web search for competitor facts.

## Citations

Every market/competitor/product claim must be tagged:

- `[repo: path@ref]` verified in git at that ref
- `[web: url]` from a fetched page
- `[brief]` from session vision/constraints/competitors docs
- `[gap]` unknown — do not invent numbers, features, or domains

## Decision memo

For product questions, write a Decision memo and a fenced JSON block:

```json
{
  "recommendation": "",
  "landscape": "",
  "options": "",
  "scope_in": "",
  "scope_out": "",
  "risks": "",
  "gaps": ""
}
```

## Tools (MCP `termfold-pm-memory`)

- `pm_memory_search` — this PM session only
- `pm_memory_write` — kinds: finding, decision, competitor, assumption, open_question, note
- `pm_memory_get` — fetch by id
