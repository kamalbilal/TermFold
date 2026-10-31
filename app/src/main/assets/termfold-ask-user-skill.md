---
name: termfold-ask-user
description: Ask the user structured multiple-choice questions in the TermFold chat UI (Agent, Plan, or Ask mode). Use when you need a clear decision, not free-text back-and-forth.
---

# TermFold interactive questions (mandatory)

When you need the user to pick from options (or a short "Other" answer), you **must** call the MCP tool **`ask_user`** from server **`termfold-ask-user`**. **Never** ask the same thing in plain chat.

TermFold injects `<termfold-interactive-ask>` on every user message in Cursor sessions (projects, automation, workflows). Treat it as non-negotiable.

The app shows a card under the current chat turn with radio/checkbox options, **Continue**, and **Skip**. The tool **blocks** until they respond (they may take a long time). **Do not** retry `ask_user` or run other tools until it returns; if you see a timeout error, ask the user to tap **Continue** on the open card instead of sending a second card.

## When to use

- Ambiguous requirements with 2–4 sensible options
- "Which approach should I take?" before a large change
- Confirming scope, priority, or risk — instead of a wall of text in chat

## When not to use

- Simple yes/no you can infer from context
- Purely informational replies

## Payload shape

```json
{
  "title": "Pick a direction",
  "questions": [
    {
      "id": "approach",
      "prompt": "How should we fix the layout?",
      "allowMultiple": false,
      "options": [
        { "id": "a", "label": "Minimal CSS tweak (Recommended)" },
        { "id": "b", "label": "Refactor the composable tree" }
      ]
    }
  ]
}
```

The tool returns JSON with an `outcome` field (`answered`, `skipped`, or `cancelled`) and `answers` when answered.

## Rules

- Prefer **`ask_user`** over typing numbered lists in chat when options would help.
- One card at a time; wait for the result before sending another ask.
- After **Skip**, continue with reasonable defaults and say what you assumed.
