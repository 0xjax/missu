---
name: catch-me-up
description: Use when the user lost track of AI-generated changes and asks for a plain-language explanation — triggers like "人话版改动说明", "解释工作区修改", "我看不懂这批改动", "catch me up", "what changed". Applies to workspace diffs, commit ranges, or a just-finished task.
---

# Catch Me Up

## Overview

The user's mental model fell behind the code. They are missing **premises and structure**, not words — so do not retell the diff longer; re-pitch it shorter and layered. Cognitive budget is the constraint: the default output must fit one screen, with detail available on request.

**Output language follows the conversation language.** Use the repo's own vocabulary (this repo: AGENTS.md terms, `CRITICAL`/`WARNING`/`NOTE` tags) instead of inventing terms.

## Scope

Default: current workspace changes (`git status` / `git diff`). If the user names a range (commits, a PR, "what you just did"), use that instead.

## Output Contract

Produce exactly these parts, in this order:

1. **TL;DR** — 2–3 sentences: what is this batch of changes doing overall.
2. **Semantic groups table** — one row per *intent*, not per file or per commit. AI-generated batches are tangled; group by what the change is *for*. Columns: `组 | 改了什么 | 为什么改 | 涉及文件` (max 5 columns; localize headers to conversation language). One sentence per cell.
3. **风险与意外** — only what deserves attention: behavior changes, removed checks, money/rule semantics, irreversible ops. If nothing qualifies, write "无". Do not spend words on parts that are fine.
4. **假设与待确认** — what you inferred or assumed that the user never stated. Label inference as inference; never present it as fact.
5. **One closing question** — "想展开哪一组？" Offer drill-down; expand one group at a time when asked.

## Rules

- Default depth is *simple*. Details (line refs, edge cases, test coverage) belong in drill-down answers, not the first response.
- Short sentences. One idea per sentence. Define or avoid jargon the user's question suggests they won't know.
- If a group is a pure reorder/format/no-op change, say so in one clause and move on.
- Never restate the diff hunk-by-hunk — that is the failure mode this skill exists to replace.

## Common Mistakes

| Mistake | Fix |
|---|---|
| Grouping by file/directory/commit | Group by intent; one commit may split, several may merge |
| First response longer than one screen | Cut detail to drill-down; keep table cells to one sentence |
| Listing everything that is fine | Section 3 is for risks only; "无" is a valid answer |
| Stating inferred "why" as fact | Move it to 假设与待确认 |
| Answering with a wall of prose | The table is the payload; prose is TL;DR + risks + questions |
