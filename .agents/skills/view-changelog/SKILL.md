---
name: view-changelog
description: Use when comparing changes between npm package versions, investigating breaking changes before upgrading, or reviewing dependency updates
---

# View Changelog

## Overview

Multi-source fallback chain for npm package changelogs. No single source covers all packages.

## Quick Reference

**Metadata discovery:**

```bash
bun pm view <pkg> versions --json   # version list
bun pm view <pkg> repository.url    # repo URL
```

Parse `owner/repo` from URL — handle `https://...git`, `git+https://...git`, `github:owner/repo` variants.

**Release tag detection** (monorepos often differ from git refs — e.g. biome uses `cli/v*` in git refs but `@biomejs/biome@*` in releases):

```bash
# Check release tag format (preferred — this is what you filter against)
gh api repos/{owner}/{repo}/releases --jq '.[].tag_name' | head -5
# Or git refs (may differ from release tags)
gh api repos/{owner}/{repo}/git/matching-refs/tags --jq '.[].ref | ltrimstr("refs/tags/")' | head -5
```

## Source Fallback Chain

Try in order; stop when content is found:

### 1. GitHub Releases (preferred)

```bash
# Single version
gh api repos/{owner}/{repo}/releases/tags/v{version} --jq '{tag_name, published_at, body}'

# Range — paginate + filter
gh api repos/{owner}/{repo}/releases --paginate \
  --jq '.[] | select(.tag_name | test("v?1[.](2|3|4)[.]")) | {tag_name, published_at, body}'
```

### 2. CHANGELOG.md in repo

```bash
gh api repos/{owner}/{repo}/contents/CHANGELOG.md \
  -H "Accept: application/vnd.github.raw+json"
```

1MB API limit. Common filenames: `CHANGELOG.md`, `HISTORY.md`, `CHANGES.md`

### 3. GitHub Compare (commit-level, last resort)

```
FetchURL url=https://github.com/{owner}/{repo}/compare/v{from}...v{to}
```

## Output Format

`## pkg from → to` heading, then per version: **Breaking Changes**, **Features**, **Fixes** (omit empty sections). Summarize patches; detail minors/majors. **End with a "Highlights" section**: plain-language summary of changes that may actually affect the user's project (new warnings, behavior changes, important fixes) — skip noise (deps, typos, docs).

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| FetchURL for GitHub data | `gh api` — authenticated, structured JSON |
| `base64 -d` for file content | Use raw media type header instead |
| Assuming `v` tag prefix | Detect actual format first |
| Assuming single-package repo | Check monorepo tag patterns |
| `\\.` in `--jq` regex | Use `[.]` bracket class — `\\.` fails in jq string literals |
| Git refs ≠ release tags | Monorepos may use different formats; check `releases` not just `matching-refs` |
| Only checking GitHub releases | Fall through: releases → CHANGELOG.md → compare |
