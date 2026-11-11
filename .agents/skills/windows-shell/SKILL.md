---
name: windows-shell
description: Use when writing shell commands on Windows and the correct syntax depends on whether the active shell is Git Bash or PowerShell
---

| | Git Bash | PowerShell |
|---|---|---|
| Shell | default Bash tool (MSYS2/MINGW64) | `pwsh` / `powershell` |
| Env var | `$VAR` | `$env:VAR` |
| Cross-shell call | `pwsh -Command '$items.Count'` (single-quote to prevent `$` expansion) | — |

Use `pwsh` from Bash only for registry, COM, or WMI.
