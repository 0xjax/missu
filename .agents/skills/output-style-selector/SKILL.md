---
name: output-style-selector
description: Use when the user wants to change response verbosity or collaboration style
disable-model-invocation: true
---

# Output Style Selector

Map user intent → load file as system prompt:

| Intent | File |
|---|---|
| no preference / "get it done" | `output-styles/default.md` |
| "just do it" / "don't ask" | `output-styles/proactive.md` |
| "lean" / "concise" / "terse" | `output-styles/lean.md` |
| "explain why" / "teach me" | `output-styles/explanatory.md` |
| "let me practice" / "learn by doing" | `output-styles/learning.md` |
