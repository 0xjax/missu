# missu Native（Kotlin + Compose）

`D:\missu` 下 Expo 版的 Kotlin 重写。目标：拿回完全可控的原生实现——没有 Metro、没有 Expo Go 版本错配、Compose 代码全是自己的。

**分段迁移**：先把「网络 + 解析」跑通，再按屏幕逐块搬。Expo 版不再构建 APK，仅作参照实现。

## 当前状态

- ✅ 工程骨架可编译：`BUILD SUCCESSFUL`，产出 `app-debug.apk`（25.6 MB）
- ✅ 工具链已收口：SDK/JDK 全局唯一，NDK/CMake/重复 SDK 已删（回收约 5.6 GB）
- ✅ 数据层首块：`data/api` + `data/parse`，`getHome` 已用**真实抓取的首页**验证（4 个板块 / 56 条视频）
- ✅ **真机端到端**：vivo V2507A 上点按钮 → `getHome` 直接拿到 HTML 并解析出同样 4 板块 / 56 条（未触发人机验证）
- ✅ **首页 UI**：Compose 两列网格（板块标题 + 封面 + 时长角标 + 两行标题），Coil 3 加载封面，加载/空/错误/内容四态齐全
- ✅ **详情 + 播放**：点卡片进详情（标题/时长/8 行信息/简介），ExoPlayer 播 HLS；已验证真机出画面
- ✅ **搜索**：关键词搜索 + 分页（「加载更多」实测 12 → 24 条），结果网格与首页共用 `VideoGridCard`
- ✅ **收藏**：详情页收藏/取消收藏，状态来自 `/api/items/{id}/view`；**实测**删掉会话后点收藏会先自动重登再重试（`/api/items/{id}/save` 401 → `withAuthRetry`）
- ✅ **收藏列表**：`/saved` 分页网格；**实测**游客态会被误报成「空收藏」，现用 `reloginWithRemembered()` 确认会话后重取
- ✅ **播放器控件**：官方 Material3 组件（`media3-ui-compose-material3` 的 `PlayerDefaults`）——圆形快退/播放/快进、进度条、倍速菜单、全屏；点画面切换显隐，播放中 3 秒自动隐藏
- ✅ **设置 + 内容语言**：7 种语言（繁體/简体/英/日/韩/马来/泰），写进站点路径段；切换后首页立即重拉，重启后保持
- ✅ **观看历史 + 继续播放**：本地存进度（每 5 秒 + 退出时上报），历史页有进度条，点进去从上次位置续播（实测 0:23 → 打开后 00:42）
- ✅ **片单成员管理**：详情页「片单」底部面板，勾选即写入 `/api/playlists/add|remove`；重开面板读回服务端状态（实测 false → true → 重启后仍 true）
- ✅ **列表页**：`MissavApi.getVideos(path, page)` 通用列表 + 分页；详情页信息行里带路径的值（女优/类型/厂商…）可点，跳到对应列表（实测点「RARA」进入其作品页）
- ✅ **底部导航 + 发现**：`首页 / 发现 / 我的` 三个一级页签；发现页只有三个目录「女优 / 类型 / 厂商」（`DirectoryParser`，分页），搜索入口只在首页顶部
- ✅ **女优筛选与排序**：身高 12 档 / 罩杯 A–Q / 年龄 6 档 / 出道年份（N 年以前），排序 `影片 / 出道`（实测 161–165cm 与按出道年排序都生效）
- ✅ **搜索历史**：成功搜索后记入本地（上限 20，重复置顶），空输入时展示为可点胶囊，可清空
- ✅ **我的片单**：`/playlists` 列表（名字 · 可见性 · 更新时间）+ 片单详情网格；真实页面 fixture 测试锁住解析规则
- ✅ **新建片单**：站点只有 Blade 表单（`/playlists/create`），先 GET 读 `_token` 与字段名再 urlencoded POST；实测建出 `test` 后列表自动刷新
- ✅ **编辑 / 删除片单**：详情页页头「编辑」→ 改名/描述/公开 + 删除（二次确认）；实测改名 `test→test2`、删除后列表只剩 `精选`


## 版本矩阵（实测钉死）

| 组件 | 版本 | 备注 |
|---|---|---|
| Gradle | **9.6.0**（wrapper） | AGP 9.x 硬性要求 ≥ 9.6.0；发行包走腾讯镜像 |
| AGP | **9.5.1** | 9.x 起 **Kotlin 支持内置**，不要再加 `org.jetbrains.kotlin.android` 插件 |
| Kotlin | 2.4.21 | 只通过 `org.jetbrains.kotlin.plugin.compose` 参与 |
| Compose BOM | 2026.09.00 | |
| activity / navigation / lifecycle-vm | 1.13.0 / 2.10.2 / 2.11.0 (compose 版) | |
| compileSdk | **37**（+ `compileSdkMinor = 0`） | 新 AndroidX 要求 ≥ 37 |
| targetSdk / minSdk | 36 / 26 | 编译用新 API、运行时行为按 36 走 |
| JDK | **独立 Temurin 21**（`D:\Android\jdk-21`）+ 回落 AS 自带 JBR | 目录名带版本；`build.ps1` **自动发现** `D:\Android\jdk-*` 并取 major 最高者，升级只需解压出 `jdk-25`，脚本不用改。版本信息见目录内 `MISSU-JDK-VERSION.txt` |
| Android SDK | API 37.0 + Build-Tools 37 + Platform-Tools | `D:\Android\Sdk` |
| Android Studio | **2026.2.2**（build `AI-262.10968.63.2622.16840690`，数据目录 `AndroidStudio2026.2.2`） | 只需它自带的 JDK；不需要单独装 JDK |

**SDK 与 JDK 是全局安装物**，不在项目里；项目只通过 `local.properties`（`sdk.dir=D:/Android/Sdk`，已 gitignore）和 `build.ps1` 引用它们。

## 依赖清单

`:core`（纯 JVM，只有这些）：

| 依赖 | 版本 | 声明 | 用途 |
|---|---|---|---|
| OkHttp | 5.5.1 | `api` | 取数；`api` 是因为 `:app` 的 `AndroidCookieJar` 要实现 `okhttp3.CookieJar` |
| kotlinx-coroutines-core | 1.11.0 | `api` | 挂起函数；同样被 `:app` 直接使用 |
| Jsoup | 1.24.1 | `implementation` | HTML 解析（只在 core 内部用，app 不声明） |
| kotlinx-serialization-json | 1.13.0 | `implementation` | 站点 JSON 接口；纯 JVM 实现，不依赖 Android 的 `org.json` |
| JUnit | 4.13.2 | `testImplementation` | 离线测试 |

`:app`（Android）：

| 依赖 | 版本 | 用途 |
|---|---|---|
| Compose BOM | 2026.09.00 | 统一 Compose 版本 |
| material3 / ui / ui-tooling-preview / debug ui-tooling | 由 BOM 决定 | UI |
| material-icons-core | 由 BOM 决定 | 底部导航 4 个图标（不引 `icons-extended`，体积大） |
| activity-compose | 1.13.0 | `setContent` / `enableEdgeToEdge` |
| lifecycle-viewmodel-compose | 2.11.0 | `viewModel()` |
| navigation-compose | 2.10.2 | `NavHost` 单 Activity 路由 |
| media3-exoplayer + media3-exoplayer-hls | 1.11.1 | 播放；HLS 必须单独加（见已知坑） |
| media3-ui-compose-material3 | 1.11.1 | 官方 Material3 播放器控件（`PlayerDefaults`） |
| media3-datasource-okhttp | 1.11.1 | ExoPlayer 走 OkHttp（默认 HttpURLConnection 被媒体主机 403） |
| Coil 3（coil-compose + coil-network-okhttp） | 3.6.3 | 封面图 |
| coroutines-android | 1.11.0 | 主线程调度 |
| OkHttp | 5.5.1 | 显式列出便于阅读（`:core` 已 `api` 暴露） |

**镜像**：`settings.gradle.kts` 里把阿里云（gradle-plugin / google / central / public）放在 `google()`、`mavenCentral()` 之前，换机器也能用；Gradle 发行包走腾讯镜像（`gradle-wrapper.properties`）。本机 `GRADLE_USER_HOME/init.d/mirrors.gradle` 还会注入一次阿里云镜像，所以 `dependencyResolutionManagement` **不能**设 `FAIL_ON_PROJECT_REPOS`。

## 构建

```powershell
cd D:\missu\android-native
.\build.ps1            # debug APK
.\build.ps1 install    # 构建 + 装到设备
.\build.ps1 release    # release（沿用 debug 签名，仅自用侧载）
```

自动化场景（需要采集输出）加环境变量 `MISSU_NO_DAEMON=1`：**Gradle daemon 会一直占着 stdout，管道等不到 EOF，命令看起来"卡死"**——构建其实早就结束了。

- **首次**构建要下载 Gradle 9.6.0（腾讯镜像）+ 全部依赖（阿里云），冷启动约几分钟；之后增量约 10–30 秒。
- `build.ps1` **不跑测试**：测试用 `.\gradlew.bat :core:test`（见「测试」）。
- 如果 PowerShell 报「禁止运行脚本」（Restricted 执行策略）：用 `powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1`，不要去改全局策略。
- **`install` 会装到所有在线设备**：本机同时存在无线 serial 与 mDNS serial（同一台手机两条），日志会显示 `Installed on 2 devices`。只想装一次就设 `ANDROID_SERIAL=<serial>`。

`build.ps1` 里固定了这些环境变量（**每次构建都自动设，不依赖系统环境**）：

| 变量 | 值 | 为什么 |
|---|---|---|
| `JAVA_HOME` | 自动选 `D:\Android\jdk-N`（N 最大），没有则 AS 的 `jbr` | 独立 JDK 让构建不依赖 AS；两条路都实测可构建 |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | `D:\Android\Sdk` | 与 `local.properties` 的 `sdk.dir` 一致 |
| `ANDROID_USER_HOME` | `.local/android-home` | `~/.android` 被拒写 |
| `TMP` / `TEMP` | `.tmp-gradle` | `%TEMP%` 拒写，`generateDebugGlobalSynthetics` 会 `AccessDeniedException` |

**只在本地、不进 Git 的东西**（`.gitignore` 已覆盖）：`local.properties`（sdk.dir）、`.local/`（android-home、截图、诊断输出）、`.tmp-gradle/`（构建临时目录）、`**/build/`、`.gradle/`、`.kotlin/`、`.idea/`。换机器时这些都要重新生成本地副本。

## 构建不依赖 Android Studio

命令行构建只需要三样东西：**JDK 21+**、**Android SDK**（`platform-tools` + `platforms/android-37.0` + `build-tools/37.0.0`）、以及项目自带的 **Gradle wrapper**（首次构建自动下载）。
`Android Studio` 只是 IDE，外加目前**被当作 JDK 来源**（`build.ps1` 的 `JAVA_HOME` 指向它的 `jbr`）。AS 的 SDK Manager / sdkmanager 任选其一即可装 SDK 组件，`licenses` 也是独立目录。

所以完全可以不装 AS，前提是：

1. 装一个独立 JDK 21+（Temurin / Corretto 都行），把 `build.ps1` 里的 `JAVA_HOME` 指过去；放在 `D:\Android\jdk-<major>` 这类带版本的目录里（`build.ps1` 会自动挑最高版本，升级不影响脚本）；
2. **该 JDK 的 `java.exe` 必须被安全软件放行**——本机踩过：`D:\Android\jdk-17\bin\java.exe` 能读不能写、而 AS 的 JBR 正常，是卡巴斯基按可执行文件的拦截，不是 JDK 版本问题；
3. SDK 组件用 `sdkmanager` 装（在 `D:\Android\Sdk\cmdline-tools\bin` 下），不需要 AS 的图形界面。

反过来，如果保留 AS：它约 3.3 GB，好处是 IDE（跳转/调试/Layout Inspector/Profiler）；坏处只有一个——同步时容易把 NDK / system-image 自动拉回来（见下面的设置项）。

**本机现状**：`D:\Android\jdk-21`（Temurin 21.0.13）已装好并被 `build.ps1` 自动选中，`assembleDebug` / `installDebug` / `:core:test` 都实测通过。所以 AS 现在**只剩 IDE 作用**，卸载后可回收约 3.3 GB。

## Android Studio 设置（一次性）

1. **Open** → 选 `D:\missu\android-native`（不要选父目录 `D:\missu`，那是 Expo 工程）。
2. Settings → Languages & Frameworks → **Android SDK** → Android SDK Location = `D:\Android\Sdk`
   - 只应有：Android API 37.0、Build-Tools 37、Platform-Tools
   - 不要装 NDK、CMake、system-image、Emulator、旧 API（本工程无原生代码，只跑真机）
3. **Gradle JDK**：Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK = Embedded JDK**（即 AS 自带 JBR）。不要指向 `D:\Android\jdk-17`（已删）。
4. 缓存走标准位置 `%USERPROFILE%\.gradle`，不要自定义 Gradle 主目录。
5. **关掉「自动下载缺失的 SDK 组件」**（Settings → Build Tools → Gradle → 关闭 "Auto-install missing components"，或至少别在有 NDK/Emulator 的勾选下同步），
   否则打开工程时 AS 会自动拉回 NDK / system-image（各 1–2 GB）。

### 精简安装（省空间，实测）

本工程**没有一行原生代码**，所以这些东西全都不需要：

| 组件 | 为什么不要 |
|---|---|
| NDK（~2.2 GB） | 无 C/C++ 代码 |
| CMake / LLDB | 同上 |
| Emulator + system-image（每个 1–2 GB） | 只跑真机 |
| 旧 API level（< 37） | `compileSdk = 37` 固定 |
| 重复的 Android SDK 副本 | SDK Manager 里只能有一个 location（`D:\Android\Sdk`） |

清理时顺便删：Studio 安装包（~1.5 GB）、`%USERPROFILE%\.gradle\wrapper\dists` 里的旧 Gradle（~150 MB）、旧 JDK（~300 MB）、legacy Expo 的 `android/`（~1 GB）与 `node_modules`。实测累计回收约 **10 GB**（其中 NDK/CMake/重复 SDK 约 5.6 GB）。

## 模块结构

```
core/          纯 JVM 库（非 UI 层）——**不许出现 android.* / androidx.***
  src/main/kotlin/app/missu/data/
    api/    SourceConfig（语言前缀 /dm 前缀）、MissavClient（OkHttp + 挑战翻译 + JSON 请求）、
            MissavApi（getHome / getVideoDetail / searchVideos / getSaved / getVideos /
            getDirectory / login / 收藏 / 片单）、ContentLanguages、CredentialsProvider、Errors
    model/  VideoSummary · HomeSection · VideoDetail · VideoList · DirectoryList · DirectoryItem ·
            PlaylistSummary · PlaylistDetail · VideoPlaylist · AccountUser · DetailInfoRow ·
            FilterOption · FilterSection
    parse/  HomeParser · DetailParser（含 Dean Edwards 解包）· DirectoryParser · ApiJson ·
            PlaylistParser（片单列表/详情）· PlaylistFormParser（新建/修改页表单）
  src/test/  29 个离线测试 + 7 份真实页面 fixture

app/           Android 应用（UI + 平台适配）
  src/main/java/app/missu/
    MainActivity.kt        壳层：Scaffold + 底部导航 + NavHost（所有路由集中在这里）
    ui/home · ui/detail · ui/player · ui/search · ui/discover · ui/saved · ui/playlists ·
    ui/history · ui/settings · ui/account · ui/list · ui/components · ui/theme · ui/challenge
    data/api/              AndroidCookieJar（CookieManager 适配）· ChallengeHost · AndroidApi
    data/store/            ProfileStore（邮箱）· CredentialStore（Keystore 加密的账号密码）·
                           SettingsStore（内容语言）· HistoryStore（观看进度）·
                           SearchHistoryStore（最近搜索）
```

**边界规则**：`:app` 依赖 `:core`，反向禁止。取数、解析、模型都只在 `:core`，因此它们能在纯 JVM 上跑测试，将来也能被服务端复用。

## 记住账号

- 第一次手动登录后，邮箱 + 密码用 **Android Keystore 里的密钥做 AES-GCM 加密**再落盘（`data/store/CredentialStore.kt`）。
- 启动时**乐观恢复**：本地有资料就直接显示已登录，不去猜服务端会话是否还有效。
- 会话失效的**真实信号只在受保护接口上**（401 / 302 回首页）。这些调用走 `MissavApi.withAuthRetry`：
  401 → 用记住的凭据重登一次 → 重试原请求；重登被拒（改过密码）则清掉凭据并抛原错误。
- **实测结论（重要）**：当前站点对游客访问 `/playlists` 返回 **200** 而不是 302 回首页，
  所以 legacy 那条「302 = 会话失效」的判据在这里不成立——启动时据此判断只会永远得到「会话有效」。
  `checkSession()` 保留但不再用于决定是否重登。
- **同类坑（实测）**：`/saved` 对游客返回 **200 + 空列表**。只显示「还没有收藏」会把「会话丢了」
  错报成「收藏没了」。所以列表为空时先用 `reloginWithRemembered()` 确认会话再重取。
  **未来任何「登录才可见」的列表页都要按这个模式处理**，不能只依赖 401/302。
- **自动重登已实测**：删掉设备上的 Cookie 后进详情点收藏 → `POST /api/items/{id}/save` 返回 401
  → 用记住的凭据重登（会话 Cookie 在 05:54 重建）→ 重试成功 → 按钮变「已收藏」。
- `AndroidManifest` 里 `allowBackup="false"`：密文不随云备份离开设备。
- 因此不再需要 `.env` / `scripts/dev-test-login.js` / `adb reverse` 那条传凭据的长链路，已整套删除。

## 现状（都经过真机验证）

- 首页 4 板块、详情（信息行里带路径的值可点进列表）、播放（全屏 / 倍速 / ±10 秒 / 进度，Material3 控件）
- 搜索（分页）、收藏 + 收藏列表、片单成员、观看历史 + 继续播放、设置 + 7 种内容语言
- 底部导航：首页 / 发现（女优 · 类型 · 厂商）/ 我的；片单支持列表、详情、成员勾选、新建、编辑（含删除）

## 测试

```powershell
cd android-native
.\gradlew.bat :core:test      # 29 个离线测试（另有 2 个联网测试默认跳过）
```

| 测试 | 覆盖 |
|---|---|
| `HomeParserTest` / `HomeFixtureTest` | 合成用例 + 真实首页 fixture（4 板块 56 条） |
| `DetailFixtureTest` | 真实详情页：标题、8 行信息、`internalId`、打包脚本解出的 m3u8 |
| `SearchFixtureTest` | 真实搜索页：12 条 + `nextPage` 信号 |
| `DirectoryParserTest` | 计数后缀跨语言切分、占位链接去重、`/ranking` 排除、真实厂商页（含 `/dmNNN` 前缀归一化） |
| `PlaylistParserTest` | 真实 `/playlists` 页：路径/名字/可见性/更新时间切分 |
| `PlaylistFormParserTest` | 真实新建页与修改页：更新表单（无 `_method`）与删除表单（`_method=delete`）的字段读取，**不打印 token** |
| `ApiJsonTest` | 收藏状态与片单 JSON 的字段缺失/类型异常兜底 |

fixture 都是**真机抓的真实页面**（`home-cn.html` / `detail.html` / `search.html` / `playlists.html` / `playlist-create.html` / `playlist-edit.html` / `directory-makers.html`）。

联网测试默认跳过，加 `MISSU_LIVE=1` 才会跑（`LiveHomeTest` / `LiveDiagnosticsTest`）。

## Expo 版对应关系

| Expo 版 | Kotlin 版 |
|---|---|
| `expo-router` | Navigation Compose（单 Activity） |
| `expo-video` | Media3 ExoPlayer（`media3-ui-compose-material3` 控件） |
| `react-native-webview` | 系统 `WebView` + `CookieManager` |
| `node-html-parser` | Jsoup |
| `expo-sqlite/kv-store` | SharedPreferences（`ProfileStore` / `SettingsStore` / `HistoryStore`） |
| `expo-image` | Coil 3 |
| `src/api/missav.ts` 的 22 个函数 | 同名 Kotlin 函数，签名对齐便于对照 |

## 已知坑（都踩过并已解决）

**构建环境**

- **JVM 写某个目录被拒，但 PowerShell 能写** → 这是安全软件按**可执行文件**拦截，不是文件权限。
  实测：AS 自带 JBR 能写 `D:\gradle-home`，`D:\Android\jdk-17` 的 java 被拒，同一文件、同一用户。
  所以构建 JDK 固定用 JBR；`D:\Android\jdk-17` 已闲置。
- **`native-platform.dll` 加载失败**（历史问题）→ 当时把 `GRADLE_USER_HOME` 挪出了用户目录；现在标准位置
  `%USERPROFILE%\.gradle` 实测可用（缓存已迁回，约 4.6 GB），不要再自定义 Gradle 主目录。
- **`AccessDeniedException: %TEMP%\global_synthetics_*`** → `TMP`/`TEMP` 指到 `.tmp-gradle`。
- **`FAIL_ON_PROJECT_REPOS` 冲突** → 本机 `GRADLE_USER_HOME/init.d/mirrors.gradle` 会注入阿里云镜像，`settings.gradle.kts` 不能设那个模式（镜像已同时写进工程，换机器也能用）。
- **AGP 9 内置 Kotlin** → 再加 `org.jetbrains.kotlin.android` 会直接报错。
- **compileSdk 必须 37** → Compose BOM 2026.09 的 AndroidX 组件要求 ≥ 37。
- **`build.ps1` 必须是 UTF-8 带 BOM** → Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，中文全角字符会吞掉行尾换行，把下一行代码并进注释（曾导致 `JAVA_HOME` 从未生效）。**用工具/AI 改这个文件后一定要重新写回 BOM**（`[System.Text.UTF8Encoding]::new($true)`），否则一改就坏。
- **Gradle daemon 占住 stdout** → 自动化采集输出时加 `MISSU_NO_DAEMON=1`，否则管道永远等不到 EOF。
- **KDoc 里不能出现 `/*` 序列**（例如写 `` `/api/playlists/*` ``）：Kotlin 支持嵌套块注释，这会开启一个
  新注释，把后面的代码整段吃掉，最后在**文件末尾**报 `Syntax error`——错误位置和真正原因完全无关，很难查。
  注释里引用路径通配时写 `…` 或 `/api/playlists/all` 代替。

**站点契约（最容易安静出错的地方）**

- **游客态不报错**：`/saved` 对游客返回 200 + 空列表，`/playlists` 对游客返回 200。所以「空结果」不能直接当成
  「真的没有」，登录可见的列表页必须先用 `reloginWithRemembered()` 确认会话（已实测）。
- **401 是会话失效、不是权限不足**：写接口（收藏 / 片单）用 401 表示需要重新登录，统一走 `withAuthRetry`。
  **例外**：登录接口自己的 401 表示账号密码错，要转成 `InvalidCredentialsException`，文案不能混。
- **`checkSession()` 的 302 判据失效**：本项已实测不成立，启动时不要据此判断登录态（详见「记住账号」）。
- **根路径接口**：片单与收藏挂在 `/api/...`，带 `/cn` 前缀会 404（`requestJson(rawPath = true)`）。
- **表单 `action` 可能是绝对 URL**：片单删除表单的 `action` 是 `https://missav.ai/cn/playlists/{id}`，
  再走「拼语言前缀」的构造函数会变成 `/cn/https://…` → **404**（实测踩过）。`postForm` 必须识别绝对 URL 原样使用。
- **更新与删除的表单形态不同**：更新是 POST 到编辑页自身（`action` 为空、没有 `_method`）；
  删除是 POST 到片单自身 URL 且带 `_method=delete`（Laravel 方法伪装）。两者都要先 GET 页面拿 `_token`。
- **Cloudflare 目前只在 PC 上触发**：PC 的 JVM 请求被 403 挑战（`cf-mitigated: challenge`），而真机上的
  OkHttp 直接放行。所以 `ChallengeActivity` 是兜底链路、**尚未被真实触发验证过**；若哪天手机端也被挑战，
  这是唯一出路——`cf_clearance` 必须让 OkHttp 与 WebView 共用系统 `CookieManager`（已打通）。

**UI 与交互**

- **底部栏高度**：M3 `NavigationBar` 自己处理窗口 inset，外面不要再加一次 `insets.bottom`，否则底栏高度翻倍（Expo 版踩过）。
- **系统栏内边距只在壳层处理一次**（`Scaffold` + `WindowInsets.safeDrawing`）；页面里各自加 `statusBarsPadding`
  会变成"每加一个页面都要记得加"，而且极易漏。需要贴边的页面单独 opt-out。
- **列表页的空/错状态要区分**：网络上失败不能显示成"没有内容"，否则用户以为数据丢了。

**播放器（Media3）**

- **播放器生命周期**：详情页留在回退栈里时 ExoPlayer 实例会一直挂着，离开必须 `release()`。
- **Media3 1.x 不含 HLS**：只加 `media3-exoplayer` 会在 `setMediaItem` 时抛
  `ClassNotFoundException: HlsMediaSource$Factory`，必须同时加 `media3-exoplayer-hls`。
- **ExoPlayer 默认数据源会被媒体主机 403**：`HttpURLConnection`（JDK TLS 指纹）被 `surrit.com` 拒绝；
  换成 `media3-datasource-okhttp`（`OkHttpDataSource.Factory`）就能正常取流。这是本项目第二次踩到
  「同一个人在浏览器里能过，换 HTTP 栈就 403」。
- **画面输出不能用 SurfaceView**：`PlayerView` 默认的 SurfaceView 直接画在窗口最上层，
  Compose 自绘控件会被它整个盖住（实测：控件完全不显示）。用 `TextureView` + `player.setVideoTextureView`。
- **interop View 会吃掉触摸**：画面上的「点一下切换控件」不能靠 Compose 层的 `clickable`——事件到不了，
  必须挂在 `TextureView.setOnTouchListener` 上。
- **弹层不能放在会被自动隐藏的控件树里**：倍速菜单挂在控件层内时，3 秒自动隐藏会把整棵树销毁，
  菜单一闪就关。菜单本体要由播放器持有，并在菜单展开期间暂停自动隐藏。

**调试**

- **vivo 只保留 W/E 级应用日志**：`Log.i` 完全看不到（实测），排查时用 `Log.w`，并且别把凭据写进日志。
- **`uiautomator dump` 是单行 XML**：用正则取 `text="…"` 与 `bounds`，不要指望 `Select-String` 按行筛。
- **Compose 的无障碍树只在控件可见时才有节点**：自动隐藏的控件 dump 不到，先点一下让它们出现。