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
- 界面 UI **自己用 RN 视图 + Reanimated 画**，原语只在 `src/components/ui/` 增加（`Sheet` / `Menu` / `Pill` / `UiButton` / `Checkbox` / `SearchField` / `Toast`，职责见 DESIGN.md 的「组件框架」）；页面里 NEVER 就地发明控件。原生只留给系统级能力：Stack 转场与返回手势、播放管线、WebView、通知、权限、键盘、`Switch`/`Alert`。
- 引入任何依赖前先确认：它的可配置面能否覆盖设计需求？如果不能，就自绘或换库，不要在别人的默认值上反复试参数。
- 已被移除、`oxlint` 会拦住重新引入：`@expo/ui`（Compose 控件）、`react-native-paper`、`expo-symbols`、`expo-linking`、`react-native-web`、`react-dom`。前两个的动画与选中态封在 Material / Compose 内部，且曾两次把 dev 环境搞崩（白屏、`Cannot set prop 'modifiers'` 红条）；后四个与 Android 交付无关。
- 「社区推荐」不是引入理由：状态管理（Zustand / Legend State）、查询缓存（TanStack Query）、存储（MMKV）、绘图（Skia）、样式引擎（NativeWind）当前都**没有**实测需求，因此都没有引入。要用它们，先给出本仓库里的具体问题与实测数据。
- 加原生模块（Expo Go 内置不到的）等于放弃 Expo Go 直接调试：必须同时更新 `docs/android-debugging.md` 的调试流程，并明确告知用户。

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

- 类型检查：`bun check`（= `tsc --noEmit`）；Lint：`bun run lint`。
- Android 生产 JS 打包：`bunx expo export --platform android`；除非明确要求原生构建，NEVER 使用 Gradle。
- 依赖禁令由 `.oxlintrc.json` 的 `no-restricted-imports` 强制：移除一个依赖要同时收掉引用和豁免，`bun run lint` 通过才算收干净。
- `expo export` 在 Hermes 字节码阶段可能报 `Failed to open file …\Temp\….hbc…: permission denied`（Windows 上 Temp 写权限或安全软件拦截）：把 `TMP`/`TEMP` 指到工作区（如 `$env:TMP="<repo>\.tmp"`）再跑，这不是代码错误。
- After structural changes, update stale descriptions in AGENTS.md and DESIGN.md proactively.
