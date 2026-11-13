# missu Native（Kotlin + Compose）

`D:\missu` 下 Expo 版的 Kotlin 重写。目标：拿回完全可控的原生实现——没有 Metro、没有 Expo Go 版本错配、Compose 代码全是自己的。

**分段迁移**：先把「网络 + 解析」跑通，再按屏幕逐块搬。Expo 版在这期间保持可运行，作为参照实现和验收标准。

## 当前状态

- ✅ 工程骨架可编译：`BUILD SUCCESSFUL`，产出 `app-debug.apk`（11.4 MB）
- ✅ 单 Activity + Compose 深色主题 + 占位屏
- ⬜ 网络层 + Jsoup 解析（下一步）
- ⬜ 首页 / 详情 / 播放 / 搜索 / 登录
- ⬜ 发现 / 片单 / 收藏 / 历史 / 设置

## 版本矩阵（实测钉死）

| 组件 | 版本 | 备注 |
|---|---|---|
| Gradle | **9.6.0** | wrapper 指向腾讯镜像；AGP 9.x 硬性要求 ≥ 9.6.0 |
| AGP | **9.5.1** | 9.x 起 **Kotlin 支持内置**，不要再加 `org.jetbrains.kotlin.android` 插件 |
| Kotlin | 2.4.21 | 只通过 `org.jetbrains.kotlin.plugin.compose` 参与 |
| Compose BOM | 2026.09.00 | |
| activity-compose / navigation-compose / lifecycle-vm-compose | 1.13.0 / 2.10.2 / 2.11.0 | |
| compileSdk | **37**（+ `compileSdkMinor = 0`） | 新 AndroidX 要求 ≥ 37；本机装的是 `android-37.0` |
| targetSdk / minSdk | 36 / 26 | 编译用新 API、运行时行为按 36 走 |
| JDK | 17 | `D:\missu\.local\toolchain\jdk-tmp\jdk-17.0.2` |

## 构建

```powershell
cd D:\missu\android-native
.\build.ps1            # debug APK
.\build.ps1 install    # 构建 + 装到设备
.\build.ps1 release    # release（沿用 debug 签名，仅自用侧载）
```

`build.ps1` 里把 5 个环境变量一次性配好，它们都不是可选的：

| 变量 | 值 | 为什么 |
|---|---|---|
| `JAVA_HOME` | `.local/toolchain/jdk-tmp/jdk-17.0.2` | 不在 PATH |
| `ANDROID_HOME` | `.local/toolchain/sdk` | 不在 PATH；与 `local.properties` 的 `sdk.dir` 一致 |
| `GRADLE_USER_HOME` | `.local/gradle-home` | 用户目录的 `~/.gradle` 里 `native-platform.dll` 加载失败 |
| `ANDROID_USER_HOME` | `.local/android-home` | `~/.android` 被拒写（analytics.settings） |
| `TMP` / `TEMP` | `.tmp-gradle` | `%TEMP%` 拒写，`generateDebugGlobalSynthetics` 会 `AccessDeniedException` |

## Android Studio 设置（一次性）

1. **Open** → 选 `D:\missu\android-native`（不要选父目录 `D:\missu`，那是 Expo 工程）。
2. Settings → Build, Execution, Deployment → Build Tools → Gradle：
   - **Gradle user home** 改成 `D:\missu\.local\gradle-home`（否则 AS 会踩 `native-platform.dll` 那个坑）
   - **Gradle JDK** 用 AS 自带的 JBR 21 即可
3. 系统镜像（Emulator）不必装：本工程只跑真机。

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

1. **网络层 + 解析**（唯一有不确定性的部分）：`getHome` 拿到板块 → 先写 JVM 单测跑通，再上 UI。
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

## 已知坑（构建期踩过并已解决）

- **`native-platform.dll` 加载失败**：`GRADLE_USER_HOME` 指到工作区（`.local/gradle-home`）即可，用户目录那份是坏的。
- **`AccessDeniedException: %TEMP%\global_synthetics_*`**：`TMP`/`TEMP` 指到 `.tmp-gradle`。
- **`FAIL_ON_PROJECT_REPOS` 冲突**：本机 `GRADLE_USER_HOME/init.d/mirrors.gradle` 会在 project 级注入阿里云镜像（国内加速），所以 `settings.gradle.kts` 不能设那个模式。
- **AGP 9 内置 Kotlin**：再加 `org.jetbrains.kotlin.android` 会直接报错——插件自己会告诉你删掉。
- **compileSdk 必须是 37**：Compose BOM 2026.09 的 AndroidX 组件要求 ≥ 37，36 会被依赖元数据拒绝。

## 运行时注意（从 Expo 版带过来的教训）

- **底部栏高度**：M3 `NavigationBar` 自己处理窗口 inset，外面不要再加一次 `insets.bottom`。
- **Cloudflare**：`cf_clearance` 必须让 OkHttp 与 WebView 用同一份 Cookie，否则验证过了仍 403。
- **语言前缀**：站点接口是 `/cn/api/...`，但片单接口挂在根路径 `/api/playlists/...`。
- **播放器生命周期**：详情页留在回退栈里时 ExoPlayer 实例会一直挂着，离开必须 `release()`。
