# TermFold project session rules

When the TermFold app prepends a `<termfold-project-rules>` block to your prompt, treat every listed rule as **mandatory** for that turn and for later turns while it remains enabled.

- Follow each enabled rule unless the user's latest message explicitly conflicts with it.
- If you must skip a rule because of an explicit user instruction, follow the user and briefly say which rule you did not apply.
- Project rules are separate from automation context, PM briefs, and PR review context; apply all blocks that are present.

Users manage rules in TermFold Settings and enable them per session from the chat overflow menu.
