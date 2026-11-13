# missu

仓库里有两个工程：

| 目录 | 状态 | 说明 |
|---|---|---|
| `android-native/` | **现役** | Kotlin + Jetpack Compose 原生工程，唯一交付目标 |
| 仓库根（`src/`、`app.json`…） | **legacy** | Expo SDK 58 的 React Native 实现，只作参照实现与设计规格，**不再构建、不再运行** |

交付包名 `app.missu`，应用名 `Missu`，仅 Android（真机验收）。设计语言见 @DESIGN.md，原生工程的构建细节见 `android-native/README.md`。

## 命令

```powershell
# 原生工程（唯一现役构建）
cd android-native
.\build.ps1              # debug APK
.\build.ps1 install      # 构建 + 装到设备
.\build.ps1 release      # release（沿用 debug 签名，仅自用侧载）
```

`build.ps1` 会配好 `JAVA_HOME`（AS 自带 JBR 21）、`ANDROID_HOME`（`D:\Android\Sdk`）、`ANDROID_USER_HOME`、`TMP/TEMP`。自动化采集输出时加 `MISSU_NO_DAEMON=1`——Gradle daemon 会一直占着 stdout，管道等不到 EOF。

工具链：SDK `D:\Android\Sdk`、JDK = Android Studio 自带 JBR 21、Gradle 缓存 `%USERPROFILE%\.gradle`、Gradle 发行包 9.6.0（wrapper，腾讯镜像）。

## 开发流程

- **没有 Metro / Fast Refresh**。改 Kotlin 代码 → 重新编译装机（增量约 10–30 秒）→ `adb shell am start -n app.missu/.MainActivity`。
- 两个模块：`:core` 是**纯 JVM**（取数/解析/模型，不许 `android.*` / `androidx.*`），`:app` 是 Android UI 与平台适配；`:app` 依赖 `:core`，反向禁止。解析测试在 `:core`，用 `.\gradlew.bat :core:test` 跑。
- 真机调试（无线 ADB、截图、日志、uiautomator）见 `docs/android-debugging.md`。
- 卡巴斯基会拦 DSH 子进程与 `java.exe` 的写入：工作区 `D:\missu` 在排除列表里，构建临时目录也因此落在 `.tmp-gradle`，不要改回 `%TEMP%`。
- legacy 的 Expo 侧没有 `node_modules`（已删）；需要对照或重跑时先 `bun install`。

## 依赖选择（不许靠猜）

- 原生侧 UI 一律 **Material 3 + 自己写的 Compose**，不引入第三方 UI 组件库；需要行为可控时宁可自绘，也不要在别人的默认值上反复试参数。
- 不用「行为/动画封在内部、只能靠猜默认值调参」的组件。遗留反面案例：Expo 侧的 `NativeTabs`（底层 RNS → Material `BottomNavigationView`）、`@expo/ui`（Compose 控件不可配，且 Expo Go 与 npm 版本错配时两次把环境搞崩）。
- 引入任何依赖前先确认：它的可配置面能否覆盖设计需求？不能就自绘或换库。
- 「社区推荐」不是引入理由：状态管理、查询缓存、绘图库当前都没有实测需求；要用先给出具体问题与实测数据。

## 站点取数（API 优先，解析是最后手段）

取数方式有明确优先级，**不能跳过前一级**：

1. **站点自己的 JSON 接口**（首选）。证据来源：站点页面内联脚本与 JS bundle 里的 `axios/fetch` 调用，或 legacy `src/api/missav.ts` 的请求构造。例：`GET /api/items/{id}/view`、`POST /api/playlists/add`。
2. **HTML 解析**（次选，仅在确认没有 JSON 接口时）。必须先确认第 1 级不存在，并在代码里写明依据。
3. **Blade 表单 POST**（最后手段）。要读 `_token`、字段名、跳转语义，脆弱面最大。

强制约束：

- **不许猜端点或参数**。参数名与取值必须来自：站点脚本/表单、legacy 代码、或真机抓到的响应；三者都没有就说明「无法确认」，不要实现。
- **不许靠抓页面推断存在哪些接口**。筛选参数这类由前端脚本拼 query 的能力，页面上根本没有普通链接（女优筛选就是实例）——必须读站点脚本或 legacy 的请求构造。
- 走到第 2/3 级时必须三件事齐全：① 代码里 `NOTE`/`WARNING` 写明「站点无 JSON 接口，只能解析」+ 依据；② 用真实或真实结构的 fixture 写离线测试锁住规则；③ 在 `android-native/README.md`「已知坑 · 站点契约」记录结论。
- 判断「要不要做」看**脆弱面 × 使用频率**，不看其他功能是否高效：低频 + 依赖解析的操作（如新建片单），优先考虑不做。

## WebView 兜底（受限使用）

站点网页可以作为长尾功能的逃生口，但必须收敛：

- 只用于**明确的、我们不打算移植**的操作（如新建片单、账号设置），并在 UI 上标明「此操作在网页中完成」。
- 不做通用浏览模式（那就是浏览器，没有产品价值）。
- 必须复用系统 `CookieManager`（与 OkHttp 同一份 Cookie）；返回后**显式触发**受影响页面的刷新——WebView 与原生之间没有事件通道。
- 兜底坏了不能影响原生功能：它是补充，不是依赖。

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
- Kotlin 侧包名 `app.missu`，源码在 `android-native/app/src/main/java/app/missu/`。

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

- 原生工程：`cd android-native; .\build.ps1`（或 `install`）必须 `BUILD SUCCESSFUL`；这是唯一的验收命令。
- legacy Expo 代码不做验证（不再构建）；`bun check`（bun 原生检查器）只在需要检查 legacy TS 时用。
- 构建环境的已知坑（`native-platform.dll`、`%TEMP%` 拒写、JVM 被按程序拦截、PS 5.1 需要 BOM）见 `android-native/README.md` 的「已知坑」。
- After structural changes, update stale descriptions in AGENTS.md / DESIGN.md / android-native/README.md proactively.
