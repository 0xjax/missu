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
- Debug 构建的「测试登录」按钮靠 `bun scripts/dev-test-login.js` 供凭据（读 `.env`，经 `adb reverse`，不进命令行参数），见 `docs/android-debugging.md` 的「测试登录（Debug）」。
- 应用名 `Missu`，包名 `app.missu`；独立 APK 的构建与安装见 `docs/android-debugging.md` 的「构建独立 APK」，日常开发仍用 Expo Go。
- Windows 沙箱下子进程写工作区报 EPERM / 拒绝访问（典型：`bun run start` 写 `expo-env.d.ts` 失败）时，运行 `bun run acl:fix` 一次性补齐 ACE 与 Low 完整性标签（幂等）。脚本须无沙箱运行，受限 shell 里改不了 ACL。

## 依赖选择（不许靠猜）

- 不用「行为/动画封在内部、只能靠猜默认值调参」的组件。典型反面案例：`expo-router` 的 `NativeTabs` —— 底层是 RNS `TabsContainer` → Material `BottomNavigationView`，托盘（active indicator）、按压波纹、图标交叉淡入全在 Material 内部，既没有过渡参数，也关不掉「旧图标先消失、新图标再淡入」的空档。
- 需要控制观感的 UI **自己用 RN 视图 + Reanimated 画**（例：`src/components/tab-header.tsx`；tab 头部与底部栏都不再包原生组件）；原生只留给系统级能力：Stack 转场与返回手势、播放管线、通知、权限。
- 引入任何依赖前先确认：它的可配置面能否覆盖设计需求？如果不能，就自绘或换库，不要在别人的默认值上反复试参数。

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

- MUST NOT read `.env`, `.env.*`, or any secrets file.
- MUST NOT expose secrets in logs, comments, commits, or tool output.
- MUST NOT put secrets in command arguments.
- When env var values are needed, ask the user to provide them directly.

## Tool Selection

- **Grep** over `rg`, `grep`, `findstr`, `Select-String`; **Glob** over `find`, `dir /s`, `Get-ChildItem -Recurse`.
- **Read** over `cat`, `type`, `Get-Content`, `head`, `tail`; **Edit** over `sed`, `awk`; **Write** over `echo >`, `Set-Content`, `Out-File`.
- **Bash** reserved for git, bun, and shell-only operations.
- Read/Edit long files in batches — NEVER load entire file at once.

## Verification

- 类型检查：`bun check`；Lint：`bun run lint`。
- Android 生产 JS 打包：`bunx expo export --platform android`；除非明确要求原生构建，NEVER 使用 Gradle。
- After structural changes, update stale descriptions in AGENTS.md and DESIGN.md proactively.
