# missu Native（Kotlin + Compose）

`D:\missu` 下 Expo 版的 Kotlin 重写。目标：拿回完全可控的原生实现——没有 Metro、没有 Expo Go 版本错配、Compose 代码全是自己的。

**分段迁移**：先把「网络 + 解析」跑通，再按屏幕逐块搬。Expo 版不再构建 APK，仅作参照实现。

## 当前状态

- ✅ 工程骨架可编译：`BUILD SUCCESSFUL`，产出 `app-debug.apk`（11.4 MB）
- ✅ 工具链已收口：SDK/JDK 全局唯一，NDK/CMake/重复 SDK 已删（回收约 5.6 GB）
- ✅ 数据层首块：`data/api` + `data/parse`，`getHome` 已用**真实抓取的首页**验证（4 个板块 / 56 条视频）
- ✅ **真机端到端**：vivo V2507A 上点按钮 → `getHome` 直接拿到 HTML 并解析出同样 4 板块 / 56 条（未触发人机验证）
- ✅ **首页 UI**：Compose 两列网格（板块标题 + 封面 + 时长角标 + 两行标题），Coil 3 加载封面，加载/空/错误/内容四态齐全
- ✅ **详情 + 播放**：点卡片进详情（标题/时长/8 行信息/简介），ExoPlayer 播 HLS；已验证真机出画面
- ✅ **搜索**：关键词搜索 + 分页（「加载更多」实测 12 → 24 条），结果网格与首页共用 `VideoGridCard`
- ⬜ 播放器控件（全屏、倍速、快进退、锁横屏）——目前用 Media3 自带控件
- ⬜ 登录 / 发现 / 片单 / 收藏 / 历史 / 设置
- ⬜ 详情 / 播放 / 搜索 / 登录
- ⬜ 发现 / 片单 / 收藏 / 历史 / 设置

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
| JDK | **Android Studio 自带 JBR 21** | `C:\Program Files\Android\Android Studio\jbr`（`D:\Android\jdk-17` 被卡巴斯基按程序拦截，见已知坑） |
| Android SDK | API 37.0 + Build-Tools 37 + Platform-Tools | `D:\Android\Sdk` |

**SDK 与 JDK 是全局安装物**，不在项目里；项目只通过 `local.properties`（`sdk.dir=D:/Android/Sdk`，已 gitignore）和 `build.ps1` 引用它们。

## 构建

```powershell
cd D:\missu\android-native
.\build.ps1            # debug APK
.\build.ps1 install    # 构建 + 装到设备
.\build.ps1 release    # release（沿用 debug 签名，仅自用侧载）
```

自动化场景（需要采集输出）加环境变量 `MISSU_NO_DAEMON=1`：**Gradle daemon 会一直占着 stdout，管道等不到 EOF，命令看起来"卡死"**——构建其实早就结束了。

`build.ps1` 里固定了 5 个环境变量：

| 变量 | 值 | 为什么 |
|---|---|---|
| `JAVA_HOME` | `C:\Program Files\Android\Android Studio\jbr` | 自带 JBR 21；另一个 JDK 17 被安全软件按程序拦截 |
| `ANDROID_HOME` | `D:\Android\Sdk` | 与 `local.properties` 一致 |
| `ANDROID_USER_HOME` | `.local/android-home` | `~/.android` 被拒写 |
| `TMP` / `TEMP` | `.tmp-gradle` | `%TEMP%` 拒写，`generateDebugGlobalSynthetics` 会 `AccessDeniedException` |

## Android Studio 设置（一次性）

1. **Open** → 选 `D:\missu\android-native`（不要选父目录 `D:\missu`，那是 Expo 工程）。
2. Settings → Languages & Frameworks → **Android SDK** → Android SDK Location = `D:\Android\Sdk`
   - 只应有：Android API 37.0、Build-Tools 37、Platform-Tools
   - 不要装 NDK、CMake、system-image、Emulator、旧 API（本工程无原生代码，只跑真机）
3. Gradle 相关不用改：缓存走标准位置 `%USERPROFILE%\.gradle`，JDK 用 AS 自带的 JBR（构建脚本里也是它）

## 模块结构

```
core/          纯 JVM 库（非 UI 层）——**不许出现 android.* / androidx.***
  src/main/kotlin/app/missu/data/
    api/    SourceConfig（语言前缀/dm 前缀）、MissavClient（OkHttp + 挑战翻译）、
            MissavApi（getHome + 挑战重试）、Challenge（ChallengeHandler 契约）、Errors
    model/  VideoSummary / HomeSection
    parse/  HomeParser（Jsoup，选择器对照 legacy）
  src/test/  解析测试 + 真实首页 fixture（resources/home-cn.html）

app/           Android 应用（UI + 平台适配）
  src/main/java/app/missu/
    MainActivity.kt        当前是数据层验证屏，将来换成首页
    ui/theme/              Material 3 主题
    ui/challenge/          Cloudflare 人机验证页（WebView）
    data/api/              AndroidCookieJar（CookieManager 适配）
                           ChallengeHost（Activity 交接）
                           AndroidApi（装配：OkHttp + 共享 Cookie + 验证页）
```

**边界规则**：`:app` 依赖 `:core`，反向禁止。取数、解析、模型都只在 `:core`，因此它们能在纯 JVM 上跑测试，将来也能被服务端复用。

## 迁移顺序

1. **网络层 + 解析**（唯一有不确定性的部分）：`getHome` 拿到板块 → 先写 JVM 验证跑通，再上 UI。
2. **首页列表**：`getHome` / `getVideos` → 两列卡片网格。
3. **详情 + 播放**：`getVideoDetail` → Media3 ExoPlayer。
4. **搜索**：`searchVideos` + 搜索历史。
5. **登录 / 人机验证**：`CookieManager` 与 OkHttp 共享 Cookie，第二个坑点。
6. **发现 / 片单 / 收藏 / 历史 / 设置 / 语言**：纯 UI 体力活。

## Expo 版对应关系

| Expo 版 | Kotlin 版 |
|---|---|
| `expo-router` | Navigation Compose（单 Activity） |
| `expo-video` | Media3 ExoPlayer |
| `react-native-webview` | 系统 `WebView` + `CookieManager` |
| `node-html-parser` | Jsoup |
| `expo-sqlite/kv-store` | DataStore Preferences |
| `expo-image` | Coil（待引入） |
| `src/api/missav.ts` 的 22 个函数 | 同名 Kotlin 函数，签名对齐便于对照 |

## 已知坑（都踩过并已解决）

**构建环境**

- **JVM 写某个目录被拒，但 PowerShell 能写** → 这是安全软件按**可执行文件**拦截，不是文件权限。
  实测：AS 自带 JBR 21 能写 `D:\gradle-home`，`D:\Android\jdk-17` 的 java 被拒，同一文件、同一用户。
  所以构建 JDK 固定用 JBR；`D:\Android\jdk-17` 已闲置。
- **`native-platform.dll` 加载失败**（历史问题）→ 当时把 `GRADLE_USER_HOME` 挪出了用户目录；现在标准位置
  `%USERPROFILE%\.gradle` 实测可用（缓存已迁回，约 4.6 GB），不要再自定义 Gradle 主目录。
- **`AccessDeniedException: %TEMP%\global_synthetics_*`** → `TMP`/`TEMP` 指到 `.tmp-gradle`。
- **`FAIL_ON_PROJECT_REPOS` 冲突** → 本机 `GRADLE_USER_HOME/init.d/mirrors.gradle` 会注入阿里云镜像，`settings.gradle.kts` 不能设那个模式（镜像已同时写进工程，换机器也能用）。
- **AGP 9 内置 Kotlin** → 再加 `org.jetbrains.kotlin.android` 会直接报错。
- **compileSdk 必须 37** → Compose BOM 2026.09 的 AndroidX 组件要求 ≥ 37。
- **`build.ps1` 必须是 UTF-8 带 BOM** → Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，中文全角字符会吞掉行尾换行，把下一行代码并进注释（曾导致 `JAVA_HOME` 从未生效）。
- **Gradle daemon 占住 stdout** → 自动化采集输出时加 `MISSU_NO_DAEMON=1`，否则管道永远等不到 EOF。

**运行时（从 Expo 版带过来的教训）**

- **底部栏高度**：M3 `NavigationBar` 自己处理窗口 inset，外面不要再加一次 `insets.bottom`，否则底栏高度翻倍（Expo 版踩过）。
- **Cloudflare**：PC 上的 JVM 请求实测被 403 挑战（`cf-mitigated: challenge`），而**真机上的 OkHttp 本轮直接放行**。因此 `ChallengeActivity` 目前只是兜底：仅在收到 `ChallengeException` 时弹出，尚未被真实触发验证过。若哪天手机端也开始被挑战，这条链路就是唯一出路——`cf_clearance` 必须让 OkHttp 与 WebView 用同一份 Cookie（已用系统 `CookieManager` 打通）。
- **语言前缀**：站点接口是 `/cn/api/...`，但片单接口挂在根路径 `/api/playlists/...`。
- **播放器生命周期**：详情页留在回退栈里时 ExoPlayer 实例会一直挂着，离开必须 `release()`。
- **Media3 1.x 不含 HLS**：只加 `media3-exoplayer` 会在 `setMediaItem` 时抛
  `ClassNotFoundException: HlsMediaSource$Factory`，必须同时加 `media3-exoplayer-hls`。
- **ExoPlayer 默认数据源会被媒体主机 403**：`HttpURLConnection`（JDK TLS 指纹）被 `surrit.com` 拒绝；
  换成 `media3-datasource-okhttp`（`OkHttpDataSource.Factory`）就能正常取流。这是本项目第二次踩到
  「同一个人在浏览器里能过，换 HTTP 栈就 403」。
