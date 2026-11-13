# missu Native（Kotlin + Compose）

`D:\missu` 下 Expo 版的 Kotlin 重写。目标：拿回完全可控的原生实现——没有 Metro、没有 Expo Go 版本错配、Compose 代码全是自己的。

**分段迁移**：先把「网络 + 解析」跑通，再按屏幕逐块搬。Expo 版不再构建 APK，仅作参照实现。

## 当前状态

- ✅ 工程骨架可编译：`BUILD SUCCESSFUL`，产出 `app-debug.apk`（11.4 MB）
- ✅ 工具链已收口：SDK/JDK 全局唯一，NDK/CMake/重复 SDK 已删（回收约 5.6 GB）
- ✅ 单 Activity + Compose 深色主题 + 占位屏
- ⬜ 网络层 + Jsoup 解析（下一步）
- ⬜ 首页 / 详情 / 播放 / 搜索 / 登录
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
| `GRADLE_USER_HOME` | `D:\gradle-home` | 用户目录的 `~/.gradle` 里 `native-platform.dll` 加载失败（已加杀软排除） |
| `ANDROID_USER_HOME` | `.local/android-home` | `~/.android` 被拒写 |
| `TMP` / `TEMP` | `.tmp-gradle` | `%TEMP%` 拒写，`generateDebugGlobalSynthetics` 会 `AccessDeniedException` |

## Android Studio 设置（一次性）

1. **Open** → 选 `D:\missu\android-native`（不要选父目录 `D:\missu`，那是 Expo 工程）。
2. Settings → Languages & Frameworks → **Android SDK** → Android SDK Location = `D:\Android\Sdk`
   - 只应有：Android API 37.0、Build-Tools 37、Platform-Tools
   - 不要装 NDK、CMake、system-image、Emulator、旧 API（本工程无原生代码，只跑真机）
3. Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle user home** = `D:\gradle-home`
   （不改就踩 `native-platform.dll` 那个坑；这个目录已加入杀软排除）

## 目录规划

```
app/src/main/java/app/missu/
  MainActivity.kt          单 Activity，Compose 宿主
  ui/theme/                颜色/间距/圆角令牌（对齐 Expo 版 src/constants/theme.ts）
  ui/home/  ui/latest/  ui/discover/  ui/account/
  ui/detail/  ui/player/  ui/playlists/  ui/settings/  ui/login/
  data/api/                OkHttp + 会话 Cookie + 语言前缀
  data/parse/              Jsoup 解析（从 src/api/parse.ts 逐条搬）
  data/store/              DataStore（profile / language / preferences / history / search-history）
```

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
- **`native-platform.dll` 加载失败** → `GRADLE_USER_HOME` 指到 `D:\gradle-home`（全局位置，已排除杀软）。
- **`AccessDeniedException: %TEMP%\global_synthetics_*`** → `TMP`/`TEMP` 指到 `.tmp-gradle`。
- **`FAIL_ON_PROJECT_REPOS` 冲突** → 本机 `GRADLE_USER_HOME/init.d/mirrors.gradle` 会注入阿里云镜像，`settings.gradle.kts` 不能设那个模式（镜像已同时写进工程，换机器也能用）。
- **AGP 9 内置 Kotlin** → 再加 `org.jetbrains.kotlin.android` 会直接报错。
- **compileSdk 必须 37** → Compose BOM 2026.09 的 AndroidX 组件要求 ≥ 37。
- **`build.ps1` 必须是 UTF-8 带 BOM** → Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，中文全角字符会吞掉行尾换行，把下一行代码并进注释（曾导致 `JAVA_HOME` 从未生效）。
- **Gradle daemon 占住 stdout** → 自动化采集输出时加 `MISSU_NO_DAEMON=1`，否则管道永远等不到 EOF。

**运行时（从 Expo 版带过来的教训）**

- **底部栏高度**：M3 `NavigationBar` 自己处理窗口 inset，外面不要再加一次 `insets.bottom`，否则底栏高度翻倍（Expo 版踩过）。
- **Cloudflare**：`cf_clearance` 必须让 OkHttp 与 WebView 用同一份 Cookie，否则验证过了仍 403。
- **语言前缀**：站点接口是 `/cn/api/...`，但片单接口挂在根路径 `/api/playlists/...`。
- **播放器生命周期**：详情页留在回退栈里时 ExoPlayer 实例会一直挂着，离开必须 `release()`。
