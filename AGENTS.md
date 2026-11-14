# missu

仓库里有两个工程：

| 目录 | 状态 | 说明 |
|---|---|---|
| 仓库根（`app/`、`core/`、`gradlew`…） | **现役 / 基线** | Kotlin + Jetpack Compose 原生工程；Gradle 根就是仓库根，是标准规范路径的唯一实现 |
| `legacy/expo/` | **legacy** | Expo SDK 58 的 React Native 实现，只作参照实现与设计规格，**不再构建、不再运行** |

交付包名 `app.missu`，应用名 `Missu`，仅 Android（真机验收）。设计语言见 @DESIGN.md，原生工程的构建细节见 `docs/android-native.md`。

## 命令

```powershell
# 原生工程（唯一现役构建；仓库根就是 Gradle 根，不用 cd）
.\build.ps1              # debug APK
.\build.ps1 install      # 构建 + 装到设备
.\build.ps1 release      # release（沿用 debug 签名，仅自用侧载）
```

`build.ps1` 会配好 `JAVA_HOME`（自动选 `D:\Android\jdk-N`；AS 已卸载，构建不依赖它）、`ANDROID_HOME`（`D:\Android\Sdk`）、`ANDROID_USER_HOME`、`TMP/TEMP`。自动化采集输出时加 `MISSU_NO_DAEMON=1`——Gradle daemon 会一直占着 stdout，管道等不到 EOF。

工具链：SDK `D:\Android\Sdk`、JDK = 独立 Temurin 21（`D:\Android\jdk-21`，`build.ps1` 自动发现 `jdk-*` 取最高版本）；Android Studio 已卸载，构建不依赖它。Gradle 缓存 `%USERPROFILE%\.gradle`、Gradle 发行包 9.6.0（wrapper，腾讯镜像）。

## 开发流程

- **没有 Metro / Fast Refresh**。改 Kotlin 代码 → 重新编译装机（增量约 10–30 秒）→ `adb shell am start -n app.missu/.MainActivity`。
- 两个模块：`:core` 是**纯 JVM**（取数/解析/模型，不许 `android.*` / `androidx.*`），`:app` 是 Android UI 与平台适配；`:app` 依赖 `:core`，反向禁止。解析测试在 `:core`，用 `.\gradlew.bat :core:test` 跑。
- 真机调试（无线 ADB、截图、日志、uiautomator）见 `docs/android-debugging.md`。
- 真机上**纯操作、不需要读屏判断的长链路**（如建片单→加影片→移除→复原）→ 直接把步骤交给我，别逐步调工具；需要看截图/读 UI 才能判断的（布局、文案、状态是否真的变了）才自己驱动。
- 卡巴斯基会拦 DSH 子进程与 `java.exe` 的写入：工作区 `D:\missu` 在排除列表里，构建临时目录也因此落在 `.tmp-gradle`，不要改回 `%TEMP%`。
- legacy 的 Expo 侧已归档到 `legacy/expo/`，没有 `node_modules`（已删）；需要对照或重跑时先在那个目录 `bun install`。

## 依赖选择（不许靠猜）

- 原生侧 UI 一律 **Material 3 Expressive + 自己写的 Compose**，不引入第三方 UI 组件库；需要行为可控时宁可自绘，也不要在别人的默认值上反复试参数。
- MD3E 的版本门、配方、API 签名先查 `.agents/skills/md3e`（从 `references/version-baseline.md` 起手），别靠记忆：本工程走 **material3 1.5.0-rc01** 全套 M3E，退役组件（`SegmentedButton` / `NavigationBar` / `Medium|LargeTopAppBar`）不用于新代码。
- 不用「行为/动画封在内部、只能靠猜默认值调参」的组件。遗留反面案例：Expo 侧的 `NativeTabs`（底层 RNS → Material `BottomNavigationView`）、`@expo/ui`（Compose 控件不可配，且 Expo Go 与 npm 版本错配时两次把环境搞崩）。
- 引入任何依赖前先确认：它的可配置面能否覆盖设计需求？不能就自绘或换库。
- 「社区推荐」不是引入理由：状态管理、查询缓存、绘图库当前都没有实测需求；要用先给出具体问题与实测数据。

## 站点取数（API 优先，解析是最后手段）

取数方式有明确优先级，**不能跳过前一级**：

1. **站点自己的 JSON 接口**（首选）。证据来源：站点页面内联脚本与 JS bundle 里的 `axios/fetch` 调用，或归档实现 `legacy/expo/src/api/missav.ts` 的请求构造。例：`GET /api/items/{id}/view`、`POST /api/playlists/add`。
2. **HTML 解析**（次选，仅在确认没有 JSON 接口时）。必须先确认第 1 级不存在，并在代码里写明依据。
3. **Blade 表单 POST**（最后手段）。要读 `_token`、字段名、跳转语义，脆弱面最大。

强制约束：

- **不许猜端点或参数**。参数名与取值必须来自：站点脚本/表单、legacy 代码、或真机抓到的响应；三者都没有就说明「无法确认」，不要实现。
- **不许靠抓页面推断存在哪些接口**。筛选参数这类由前端脚本拼 query 的能力，页面上根本没有普通链接（女优筛选就是实例）——必须读站点脚本或 legacy 的请求构造。
- 走到第 2/3 级时必须三件事齐全：① 代码里 `NOTE`/`WARNING` 写明「站点无 JSON 接口，只能解析」+ 依据；② 用真实或真实结构的 fixture 写离线测试锁住规则；③ 在 `docs/android-native.md`「已知坑 · 站点契约」记录结论。
- 判断「要不要做」看**脆弱面 × 使用频率**，不看其他功能是否高效：低频 + 依赖解析的操作（如新建片单），优先考虑不做。

## WebView（只用于人机验证）

- 唯一用途：站点要求人机验证时由 `ChallengeActivity` 打开网页，拿到 `cf_clearance` 后自动重试原请求。
- 不再用它兜「表单操作」：新建片单已经走原生表单（读 `_token` + urlencoded POST）。
- 必须复用系统 `CookieManager`（与 OkHttp 同一份 Cookie），否则验证过了请求仍然 403。
- 不做通用浏览模式；这条链路坏掉不能影响其余原生功能。

## Anti-Over-Engineering

- Only make changes directly requested or clearly necessary; no extra features, refactors, or adjacent improvements.
- State ambiguity explicitly; NEVER silently pick one interpretation over another.
- Surface tradeoffs and push back if a simpler approach exists.
- No abstractions for single-use code or error handling for impossible scenarios.
- Match existing style; don't "improve" adjacent formatting.

## Code Constraints

- 仅适配 Android；iOS / Web 不是交付目标。
- Comment tags: `CRITICAL` = errors/crashes/data-loss; `WARNING` = risks/edge cases/side effects; `TODO` = incomplete work/tech debt; `NOTE` = non-obvious design decisions.
- Round to business precision before float compare/accumulate/chain; NEVER raw `==` on floats.
- 代码注释用中文；提交信息用英文并遵循 Conventional Commits。
- Kotlin 侧包名 `app.missu`，源码在 `app/src/main/java/app/missu/`。

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
- Read/Edit long files in batches — NEVER load entire file at once.

## Verification

- 原生工程：`.\build.ps1`（或 `install`）必须 `BUILD SUCCESSFUL`；这是唯一的验收命令。
- `legacy/expo/` 不参与验证（不构建、不跑 lint）；它只是参照实现。
- 构建环境的已知坑（`native-platform.dll`、`%TEMP%` 拒写、JVM 被按程序拦截、PS 5.1 需要 BOM）见 `docs/android-native.md` 的「已知坑」。
- **升级 SDK / JDK 等环境依赖前，先查 Android CLI 最新情况**：Google 正在把 `sdkmanager` 迁到原生的 `android sdk`，新版 bundle 自带的 JRE 可能未签名，而本机 Windows 11 Smart App Control 处于强制模式（`VerifiedAndReputablePolicyState = 1`）会拒绝加载未签名代码。按 `docs/android-native.md`「升级 SDK 环境依赖前」的检查清单确认后再动，并把结论写回那里。
- After structural changes, update stale descriptions in AGENTS.md / DESIGN.md / docs/android-native.md proactively.
