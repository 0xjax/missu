# missav-mobile

Expo SDK 58 + React Native + TypeScript + Expo Router；源码在 `src/`，文件路由在 `src/app/`。

Design context @DESIGN.md

包管理用 **bun**，NEVER npm/npx/pnpm。

```bash
bun run start
bun run lint
bun check
bunx expo export --platform android
```

## 开发服务

- 改代码靠 Metro Fast Refresh 生效，NEVER 重启 Metro、重新扫码或 force-stop App 来「让改动生效」；只有改 `app.json` 等配置、新增原生依赖，或 Fast Refresh 已进入报错状态时才手动 reload。
- 真机调试（ADB、截图、日志、手动 reload）见 `docs/android-debugging.md`。
- 应用名 `Missu`，包名 `app.missu`；独立 APK 的构建与安装见 `docs/android-debugging.md` 的「构建独立 APK」，日常开发仍用 Expo Go。

## Anti-Over-Engineering

- Only make changes directly requested or clearly necessary; no extra features, refactors, or adjacent improvements.
- State ambiguity explicitly; NEVER silently pick one interpretation over another.
- Surface tradeoffs and push back if a simpler approach exists.
- No abstractions for single-use code or error handling for impossible scenarios.
- Match existing style; don't "improve" adjacent formatting.

## Code Constraints

- 仅适配 Android（设计与验收均以 Android 真机为准）；iOS / Web 不为交付目标，不新增平台专属实现。
- Comment tags: `CRITICAL` = errors/crashes/data-loss; `WARNING` = risks/edge cases/side effects; `TODO` = incomplete work/tech debt; `NOTE` = non-obvious design decisions.
- Round to business precision before float compare/accumulate/chain; NEVER raw `===` or epsilon.
- 代码注释用中文；提交信息用英文并遵循 Conventional Commits。

## Git Workflow

- 直接在当前分支提交；不建 PR、不建 worktree。
- git writes → 逐命令确认，批准不跨命令继承；历史改写（rebase/reset/amend 非 HEAD）→ 每次单独确认精确命令+范围。
- NEVER 把一个文件的改动拆进两个提交。

## Secrets

- MUST NOT read `.env`, `.env.*`, or any secrets file；例外：`MISSAV_TEST_EMAIL` / `MISSAV_TEST_PASSWORD`（仓库所有者标注为开发测试账号、无敏感信息），可在开发测试时直接取用，但 NEVER 回显其值。
- MUST NOT expose secrets in logs, comments, commits, or tool output.
- MUST NOT put secrets in command arguments.
- When other env var values are needed, ask the user to provide them directly.

## Tool Selection

- **Grep** over `rg`, `grep`, `findstr`, `Select-String`; **Glob** over `find`, `dir /s`, `Get-ChildItem -Recurse`.
- **Read** over `cat`, `type`, `Get-Content`, `head`, `tail`; **Edit** over `sed`, `awk`; **Write** over `echo >`, `Set-Content`, `Out-File`.
- **Bash** reserved for git, bun, and shell-only operations.
- Read/Edit long files in batches — NEVER load entire file at once.

## Verification

- 类型检查：`bun check`；Lint：`bun run lint`。
- Android 生产 JS 打包：`bunx expo export --platform android`；除非明确要求原生构建，NEVER 使用 Gradle。
- After structural changes, update stale descriptions in AGENTS.md and DESIGN.md proactively.
