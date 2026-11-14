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
- ✅ **片单内移除影片（批量）**：右下角「整理」`ToggleFloatingActionButton` 进入多选 → M3E `HorizontalFloatingToolbar` 里「全选 / 移出 N 部」（连体 `ButtonGroup`），少见动作「取消选择 / 完成」放进 `AppBarRow` 溢出容器 → 二次确认 → 逐条 `DELETE /api/playlists/{片单id}/{影片id}`；实测 8→7 条且重进仍是 7 条（服务端生效），批量弹窗计数正确
- ✅ **我的片单操作**：`FloatingActionButtonMenu` 收纳「新建片单 / 刷新列表」，替代原来的顶栏「新建」按钮（M3E 里主操作用 FAB）
- ✅ **详情页动作条**：底部 `FlexibleBottomAppBar` + `SplitButtonLayout`（左半「收藏/已收藏」、右半「加入片单」），内容区不再被一行按钮打断
- ✅ **片单整理入口**：右下角 `ToggleFloatingActionButton`（编辑图标 ⇄ ×）；进整理模式后「取消选择 / 完成」走 `AppBarRow` 溢出容器（放不下自动收进菜单）
- ✅ **搜索栏**：M3E slot + `SearchBarState`，**就地展开**——首页顶部直接是这条真 `SearchBar`，点一下在当前页展开成全屏 `ExpandedFullScreenSearchBar`（放最近搜索），提交后同一屏收成带返回箭头的折叠态显示结果。搜索不再是独立路由：之前首页那只 44dp/10dp 圆角的假框跳到一个 56dp 胶囊的搜索页，是"割裂感"的来源
- ✅ **新建片单**：站点只有 Blade 表单（`/playlists/create`），先 GET 读 `_token` 与字段名再 urlencoded POST；实测建出 `test` 后列表自动刷新
- ✅ **编辑 / 删除片单**：详情页页头「编辑」→ 改名/描述/公开 + 删除（二次确认）；实测改名 `test→test2`、删除后列表只剩 `精选`
- ✅ **M3E 主题与组件**：`MaterialExpressiveTheme` + `MotionScheme.expressive()`；二级页统一 `PageTopBar`（up 图标 + Tooltip）；底栏换 `ShortNavigationBar`；转圈换 `LoadingIndicator`；首页第一个板块用 `HorizontalMultiBrowseCarousel` 当推荐位；首页/发现支持下拉刷新（`PullToRefreshBox`）；女优排序换连通 `ToggleButton` 组（`SegmentedButton` 在 M3E 已退役）
- ✅ **桌面图标**：五套 adaptive icon（纯色底 + 白色播放三角，共用一个前景）+ 五个 `activity-alias`；设置里「图标跟随主题色」默认关，开启后**离开应用才生效**。实测 vivo 桌面/启动动画/概览都跟着换，应用信息页保持品牌粉（机制边界，见「桌面图标」）


## 版本矩阵（实测钉死）

| 组件 | 版本 | 备注 |
|---|---|---|
| Gradle | **9.6.0**（wrapper） | AGP 9.x 硬性要求 ≥ 9.6.0；发行包走腾讯镜像 |
| AGP | **9.5.1** | 9.x 起 **Kotlin 支持内置**，不要再加 `org.jetbrains.kotlin.android` 插件 |
| Kotlin | 2.4.21 | 只通过 `org.jetbrains.kotlin.plugin.compose` 参与 |
| Compose BOM | **alpha 2026.11.00** | 走 alpha 线：全套 M3E 组件只在 material3 **1.5.0-rc01**；稳定线 1.4.0 只有部分 Expressive（主题/动效/ShortNavigationBar 有，FloatingToolbar/ButtonGroup/LoadingIndicator/柔性顶栏没有） |
| activity / navigation / lifecycle-vm | 1.13.0 / 2.10.2 / 2.11.0 (compose 版) | |
| compileSdk | **37 + `compileSdkMinor = 1`** | Compose 1.13-rc 要求 ≥ 37.1；新 AndroidX 要求 ≥ 37 |
| targetSdk / minSdk | 36 / 26 | 编译用新 API、运行时行为按 36 走 |
| JDK | **独立 Temurin 21**（`D:\Android\jdk-21`）+ 回落 AS 自带 JBR | 目录名带版本；`scripts/build.ps1` **自动发现** `D:\Android\jdk-*` 并取 major 最高者，升级只需解压出 `jdk-25`，脚本不用改。版本信息见目录内 `MISSU-JDK-VERSION.txt` |
| Android SDK | API **37.1** + **Build-Tools 36.0.0** + Platform-Tools 37.0.1 | `D:\Android\Sdk`（AGP 9.5.1 默认用 build-tools 36，与 compileSdk 37.1 无关；37.0 已卸载，只留 37.1 一个平台） |
| Android Studio | **已卸载**（曾用 2026.2.2 / `AI-262.10968.63.2622.16840690`） | **构建不需要它**：JDK、SDK、Gradle wrapper 都是独立的。装它只是为了 IDE，不装也能构建、装机、跑测试 |

**SDK 与 JDK 是全局安装物**，不在项目里；项目只通过 `local.properties`（`sdk.dir=D:/Android/Sdk`，已 gitignore）和 `scripts/build.ps1` 引用它们。

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
| Compose BOM | **alpha 2026.11.00** | 统一 Compose 版本；material3 解析成 **1.5.0-rc01**、core 解析成 1.13.0-rc01（预发布栈） |
| material3 / ui / ui-tooling-preview / debug ui-tooling | 由 BOM 决定 | UI；M3E 组件（Carousel / ShortNavigationBar / LoadingIndicator / ToggleButton 组）也在这里 |
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
cd D:\missu
.\scripts\build.ps1            # debug APK
.\scripts\build.ps1 install    # 构建 + 装到设备
.\scripts\build.ps1 release    # release（沿用 debug 签名，仅自用侧载）
```

自动化场景（需要采集输出）加环境变量 `MISSU_NO_DAEMON=1`：**Gradle daemon 会一直占着 stdout，管道等不到 EOF，命令看起来"卡死"**——构建其实早就结束了。

- **首次**构建要下载 Gradle 9.6.0（腾讯镜像）+ 全部依赖（阿里云），冷启动约几分钟；之后增量约 10–30 秒。
- `scripts/build.ps1` **不跑测试**：测试用 `.\gradlew.bat :core:test`（见「测试」）。
- 如果 PowerShell 报「禁止运行脚本」（Restricted 执行策略）：用 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1`，不要去改全局策略。
- **`install` 会装到所有在线设备**：本机同时存在无线 serial 与 mDNS serial（同一台手机两条），日志会显示 `Installed on 2 devices`。只想装一次就设 `ANDROID_SERIAL=<serial>`。

`scripts/build.ps1` 里固定了这些环境变量（**每次构建都自动设，不依赖系统环境**）：

| 变量 | 值 | 为什么 |
|---|---|---|
| `JAVA_HOME` | 自动选 `D:\Android\jdk-N`（N 最大） | 独立 JDK；AS 已卸载，构建不依赖它（脚本仍保留 AS `jbr` 作为回落，装回 AS 时可用） |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | `D:\Android\Sdk` | 与 `local.properties` 的 `sdk.dir` 一致 |
| `ANDROID_USER_HOME` | `.local/android-home` | `~/.android` 被拒写 |
| `ANDROID_USER_HOME` | `.local/android-home` | `~/.android` 被拒写 |

`TMP` / `TEMP` **不再覆盖**。2026-11-14 实测：`gradlew :app:assembleDebug --rerun-tasks --no-build-cache` 在系统默认 `%TEMP%` 下 `BUILD SUCCESSFUL`，Gradle 不需要工作区临时目录。唯一还需要可写临时目录的是 `adb server`（见「已知坑」）。

**只在本地、不进 Git 的东西**（`.gitignore` 已覆盖）：`local.properties`（sdk.dir）、`.local/`（android-home、截图、诊断输出）、`**/build/`、`.gradle/`、`.kotlin/`、`.idea/`。换机器时这些都要重新生成本地副本。

## 构建不依赖 Android Studio

命令行构建只需要三样东西：**JDK 21+**、**Android SDK**（`platform-tools` + `platforms/android-37.1` + `build-tools/36.0.0`）、以及项目自带的 **Gradle wrapper**（首次构建自动下载）。
`Android Studio` 只是 IDE。**本机已卸载 AS**：JDK 用 `D:\Android\jdk-21`，SDK 组件用 `sdkmanager`（`D:\Android\Sdk\cmdline-tools\bin`）装，`licenses` 也是独立目录——卸载后 `assembleDebug` / `installDebug` / `:core:test` 全部实测通过。

### 从零装环境（无 AS，就是本机现在的样子）

只需要装两样东西，第三样由项目自带。

**1. JDK 21**（免安装 zip，不需要管理员）

```powershell
# 官方 API，直接给最新 21 GA 的 zip
$zip = 'D:\temp\temurin21.zip'
Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
Expand-Archive $zip -DestinationPath 'D:\Android'
# 解压出来的目录形如 jdk-21.0.13+9，重命名成 jdk-21（build.ps1 会自己找 jdk-*）
Rename-Item 'D:\Android\jdk-21.0.13+9' 'jdk-21'
D:\Android\jdk-21\bin\java.exe -version
```

**2. Android SDK**：用 command-line tools 装，不需要 AS。

```powershell
# cmdline-tools 从 https://developer.android.com/studio#command-tools 下 Windows 版 zip
# 解压后必须落在 <sdk>\cmdline-tools\latest（目录名就叫 latest，否则 sdkmanager 不认）
$sdk = 'D:\Android\Sdk'
$env:JAVA_HOME = 'D:\Android\jdk-21'          # sdkmanager 也要 JDK
$sm = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
& $sm --sdk_root=$sdk --licenses                  # 全部 y
& $sm --sdk_root=$sdk "platform-tools" "platforms;android-37.1" "build-tools;36.0.0"
```

只要这三个包（注意 **API 是 37.1**、**build-tools 是 36.0.0**，两者都是实测结论：Compose 1.13-rc 要求 compileSdk ≥ 37.1，而 AGP 9.5.1 默认只用 build-tools 36）：

| 包 | 本机版本 | 用途 |
|---|---|---|
| `platform-tools` | 37.0.1 | `adb` |
| `platforms;android-37.1` | rev 1 | `compileSdk = 37` + `compileSdkMinor = 1` 的编译目标 |
| `build-tools;36.0.0` | 36.0.0 | aapt2 / d8 / zipalign；**AGP 9.5.1 不写 `buildToolsVersion` 时的默认值**（依据：`builder-9.5.1.jar` 里 `com.android.builder.core.ToolsRevisionUtils` 的静态初始化加载字面量 `36.0.0`） |

> `platforms;android-37.0` 已在升到 37.1 后卸载（`sdkmanager --uninstall "platforms;android-37.0"`），
> 本机现在只装 **37.1 一个平台**。删掉它就少一份不会被引用的 copy。

**不要装**：NDK、CMake、LLDB、emulator、system-image、旧 API（见下方「精简安装」）。

**SDK 目录里每一项是什么**（回答「哪些能删」）：

| 路径 | 要否 | 谁创建的 | 说明 |
|---|---|---|---|
| `platforms\android-37.1` | **必需** | `sdkmanager install "platforms;android-37.1"` | `compileSdk = 37` + `compileSdkMinor = 1` 的编译目标，AGP 直接读它 |
| `build-tools\36.0.0` | **必需** | `sdkmanager install "build-tools;36.0.0"`（或 AGP 构建时自动装） | aapt2 / d8 / zipalign；AGP 9.5.1 默认选 36.0.0（删了它构建时会**自动装回**） |
| `platform-tools` | **必需** | `sdkmanager install platform-tools` | `adb`（也用于安装/调试） |
| `licenses` | **必需** | `sdkmanager --licenses`（或 AGP 首次构建时自动接受） | 8 个 42 字节的授权哈希；删了 AGP/sdkmanager 会认为许可未接受 |
| `cmdline-tools` | 装包时才用 | 你手动解压的官方 zip | 构建不需要它；`sdkmanager` 在这里 |
| `.knownPackages` | 可删 | sdkmanager 自建 | 包清单缓存，下次跑 sdkmanager 自动重建 |
| `.temp` | 可删 | sdkmanager 自建 | sdkmanager 的临时目录，同样是自建 |

> 换句话说：**`platforms` 与 `licenses` 不是你手动建的**，是第一次跑 `sdkmanager --licenses` 与 `sdkmanager install ...` 时自动生成的；它们此前不存在。装第二个平台/工具时，会在这个目录里多出对应的子目录。

> NOTE: **build-tools 版本与 compileSdk 无关，由 AGP 钉死**。AGP 解析的是它自己那个精确 revision
> （9.5.1 与最新的 9.6.0-alpha03 都是 `36.0.0`），SDK 里没有就**自动下载装回**——不会因为装了 37 就改用 37。
> 想换必须显式 `buildToolsVersion = "37.0.0"`。另外 `aapt2` 其实不走 SDK：AGP 从 Maven 取
> `com.android.tools.build:aapt2:9.5.1-…`，SDK 的 build-tools 只剩 d8/zipalign/apksigner 等 + 版本下限校验。
> 本机实测：只留 `build-tools;36.0.0` 时 `.\.build.ps1` 正常；删 36 会被装回，删 37 毫无影响（省 ~137 MB）。

**3. 环境变量（用户级，一次性）**

| 变量 | 值 |
|---|---|
| `PATH` 追加 | `D:\Android\Sdk\platform-tools`（这样 `adb` 才能从 PATH 解析到） |
| `ANDROID_HOME` | `D:\Android\Sdk` |

**4. Gradle 不用装**：`gradlew.bat` 首次运行会自动下载 wrapper 指定的 9.6.0（腾讯镜像），之后走 `%USERPROFILE%\.gradle` 缓存。

**5. 验证**：`.\scripts\build.ps1 install` → `BUILD SUCCESSFUL` + App 装到手机。

> WARNING: **本机只能用 cmdline-tools 19.0；新版 Android CLI 在 SAC 强制下跑不了**。实测根因：
>
> 1. 23.0 的 `sdkmanager` 已废弃，实际入口是 `android.exe`，而它只是**引导器**：运行时会下载一个 bundle
>    （约 174 MB 的 `main.jar` + **自带的 jre**）到 `$ANDROID_USER_HOME\cli\bundles\<hash>`，再启动那个 jre。
> 2. 那个自带 jre 的 `jvm.dll` **没有 Authenticode 签名**（`NotSigned`），Windows 11 Smart App Control
>    （`VerifiedAndReputablePolicyState = 1`）拒绝加载未签名代码 → 报「应用程序控制策略阻止了文件」。
> 3. 对照：19.0 用**系统的** `jvm.dll`（Temurin，签名者 Eclipse Foundation，状态 Valid），SAC 放行，所以 19.0 能跑。
>
> 因此这不是 Google 与 Windows 不兼容，而是 SAC 拦未签名二进制；`Unblock-File` 无效（SAC 看签名，不看 MOTW），
> 卡巴斯基的受信任/排除项也无效（不是它在拦）。**不关 SAC 就没有正路**——除非把 bundle 里那个未签名 jre 换掉（见下，未验证）。
>
> 本机决定：**留在 19.0**。它的能力对本项目足够：

```powershell
$env:JAVA_HOME = 'D:\Android\jdk-21'
$sm = 'D:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat'
& $sm --sdk_root='D:\Android\Sdk' --list_installed
& $sm --sdk_root='D:\Android\Sdk' --uninstall "build-tools;37.0.0"
```

> 唯一缺陷：列「全量可用包」时看不懂 API 37 的 v4 XML（会警告），装/卸**指定版本**不受影响。
> 另外 AGP 在构建时会用**签名 JDK** 自动补装缺失的 SDK 包（实测自动装过 build-tools 36），所以日常根本不需要手动跑 sdkmanager。
>
> 真要用新版 CLI 的三条路（都不推荐）：① 关 Smart App Control（注册表 + 重启，削弱系统防护）；② 用 WSL2/Docker 里的 Linux 版
> cmdline-tools（本机未装 WSL）；③ 把 bundle 里的未签名 jre 换成签名 JDK（未验证，且每次 CLI 更新都要重做）。
### 升级 SDK 环境依赖前，先查 Android CLI 的最新情况

Google 正在把 cmdline-tools 从「Java 版 sdkmanager」迁到「原生 Android CLI（`android sdk`）」，
本机就是因为新版只带**未签名的自带 JRE** 而卡在 Smart App Control 上。所以每次要升级 SDK 环境依赖时，按顺序确认：

```powershell
# 1) 官方仓库里 cmdline-tools 当前最新构建号（换掉 URL 里的版本号即可下载）
$xml = (Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-3.xml' -UseBasicParsing).Content
[regex]::Matches($xml, 'commandlinetools-win-(\d+)_latest\.zip') | ForEach-Object { $_.Groups[1].Value } |
  Sort-Object { [long]$_ } -Descending | Select-Object -First 1

# 2) 本机 Smart App Control 状态（1=强制，会拦未签名代码）
(Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\CI\Policy').VerifiedAndReputablePolicyState

# 3) 新版 zip 里是否还有 Java 版 sdkmanager（有 sdklib jar = 有纯 Java 退路）
Get-ChildItem <解压目录>\lib -Filter 'sdklib*.jar'
```

判断口径：

| 情况 | 结论 |
|---|---|
| zip 里 `bin\sdkmanager.bat` 仍调用 `com.android.sdklib...`，且 `lib` 有 `sdklib*.jar` | 可用，直接升级（走签名 JDK，SAC 不拦） |
| 只剩 `android.exe` + `lib` 无 sdklib jar | 要先跑一次，然后验它下载的 `%ANDROID_USER_HOME%\cli\bundles\<hash>\jre\bin\server\jvm.dll` 的签名 |
| 那个 `jvm.dll` 是 `NotSigned` 且 SAC=1 | **升级会失败**，留在 19.0，并把结论记到下方「已知坑」 |

> 官方参考：[sdkmanager 文档（含废弃说明与 `android sdk` 用法）](https://developer.android.google.cn/tools/sdkmanager)、
> [Android CLI 下载页](https://developer.android.com/tools/agents)。
> NOTE: 日常其实不需要手动升级：**AGP 会在构建时用签名 JDK 自动补装缺失的 SDK 包**（本项目的 `build-tools;36.0.0` 就是它自动装的）。

**本机现状**：`D:\Android\jdk-21`（Temurin 21.0.13）已装好并被 `scripts/build.ps1` 自动选中；AS 已卸载（回收 3.3 GB）；`assembleDebug` / `installDebug` / `:core:test` 在无 AS 的状态下全部实测通过。

## Android Studio 设置（仅当你重新安装 AS 时）

1. **Open** → 选 `D:\missu`（仓库根就是 Gradle 根；`legacy/expo/` 是归档的 Expo 工程，不要当 Android 工程打开）。
2. Settings → Languages & Frameworks → **Android SDK** → Android SDK Location = `D:\Android\Sdk`
   - 只应有：Android API 37.1（37.0 已卸载）、Build-Tools 36、Platform-Tools
   - 不要装 NDK、CMake、system-image、Emulator、旧 API（本工程无原生代码，只跑真机）
3. **Gradle JDK**：仍用 **Embedded JDK**（AS 自带的）即可——它与 `scripts/build.ps1` 用的不是同一个 JDK，不影响命令行构建；也可以指到 `D:\Android\jdk-21` 保持一致。
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

**AS 本身也已卸载**（程序目录 3.32 GB）。卸载后只剩两处设置残留、共约 0.3 GB，可手动删：
`%LOCALAPPDATA%\Google\AndroidStudio2026.2.2`、`%APPDATA%\Google\AndroidStudio2026.2.2`。

## 模块结构

```
core/          纯 JVM 库（非 UI 层）——**不许出现 android.* / androidx.***
  src/main/kotlin/app/missu/data/
    api/    SourceConfig（语言前缀 /dm 前缀）、MissavClient（OkHttp + 挑战翻译 + JSON 请求）、
            MissavApi（getHome / getVideoDetail / searchVideos / getSaved / getVideos /
            getDirectory / login / 收藏 / 片单）、CredentialsProvider、Errors
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
                           SettingsStore（语言 / 明暗模式 / 主题色 / 纯黑）· HistoryStore（观看进度）·
                           SearchHistoryStore（最近搜索）
```

**边界规则**：`:app` 依赖 `:core`，反向禁止。取数、解析、模型都只在 `:core`，因此它们能在纯 JVM 上跑测试，将来也能被服务端复用。

## 桌面图标

设置里的「图标跟随主题色」（默认关）会让桌面图标跟当前色板换色。机制是 **五个 `activity-alias` + 五个 adaptive icon**，任意时刻只启用一个 alias。

- **入口在 alias 上，不在 `MainActivity` 上**：桌面枚举的是「带 MAIN/LAUNCHER 的组件」，`<application android:icon>` 只决定读 `ApplicationInfo` 的界面（应用信息、权限弹窗、分享面板）。所以启动画面会跟着 alias 一起变（`ComponentInfo.getIconResource()` 取的就是启动组件的图标），而应用信息永远是品牌粉——这是机制边界，不是 bug。
- **五套图标共用一个前景**：`drawable-nodpi/ic_launcher_foreground.png` 是从 legacy 的 `android-icon-foreground.png` 里裁出播放三角、放大到画布 52% 得到的（108dp 画布里 56dp，仍在 66dp 安全区内）。背景色在 `values/colors.xml`，与 `Palettes.kt` 的深色 `primary` 手工同步。
- **不提供 monochrome 层**：图标的身分就是背景色，主题化图标会把它抹成单色；Android 16 QPR 2 起系统会强制给没有 monochrome 的应用自动上色，那是系统的选择。

### CRITICAL：`getComponentEnabledSetting` 返回 DEFAULT **不等于**启用

真机上踩过最坏情况——切色后**应用从桌面彻底消失**，`pm enable` 也救不回来（Shell 无权改本包组件状态），只能从应用内启动触发修复。原因：

```
getComponentEnabledSetting() 只返回「显式设置过」的状态；没设过 → COMPONENT_ENABLED_STATE_DEFAULT
DEFAULT ≠ 已启用：这些 alias 在清单里是 android:enabled="false"，必须读 ActivityInfo.enabled
```

误把 DEFAULT 当启用，`apply()` 就会「以为目标已启用 → 跳过启用 → 把其余全部禁用」。两条硬约束因此写进代码：

1. `isEnabled()` 的 DEFAULT 分支读 `pm.getActivityInfo(name, MATCH_DISABLED_COMPONENTS).enabled`（不带这个 flag 会抛 NameNotFound）；
2. `apply()` 里「**目标没启用成功就绝不去关别的**」，直接 return false。

### 切换时机与兜底

- **只能在应用完全不可见之后切**：禁用「启动时经过的那个 alias」会结束它所在的 task。前台切的表现是被弹回桌面，或者正在跑的人机验证页被一起干掉。所以用 `ProcessLifecycleOwner` 的 `ON_STOP`（最后一个 Activity 停掉之后才发）触发，见 `MissuRoot`。
- 启动时只做 `ensureOne()`：**保证至少还有一个入口**，不在前台做取舍；「多余的那个」留到下次进后台由 `apply()` 一并收拾。
- 「跟随壁纸」没有静态图标可用（系统色是运行时算的），落到品牌粉。

### 怎么验证（不靠肉眼）

```powershell
# 当前启用的是哪个入口
adb shell dumpsys package app.missu | Select-String -Pattern 'disabledComponents|enabledComponents' -Context 0,5
# 桌面图标颜色：截桌面图，找 180×180 的色块
adb shell screencap -p /sdcard/home.png; adb pull /sdcard/home.png
# 从某个 alias 冷启动（验证启动画面/启动动画用的是哪个图标）
adb shell am start -n app.missu/.LauncherTeal
```

实测结论（vivo V2507A / Android 16）：桌面图标、启动动画、概览里的图标都跟着 alias 变；应用信息页保持品牌粉；切换后桌面立刻刷新，不需要重启桌面。**没能在这台机器上抓到 AOSP 启动画面那一帧**——OriginOS 用自己的「图标放大」动画盖住了它，而那段动画读的也是 alias 图标（已测）。另外「把图标固定到桌面」的快捷方式可能因为指向旧 alias 而失效（未实测）。

## 主题色板

五套静态色板（品牌粉 / 紫 / 蓝 / 青绿 / 琥珀）加系统壁纸取色，是同一条轴上的六个选项（`ColorSource`）。

- **`Palettes.kt` 是生成的，不要手改**：`scripts/gen-theme.py` 从种子色推导，一次写全五套。它固定色相、按角色取 M3 的 tone（`L*`）、超 sRGB 时二分降饱和。品牌色板的历史产物写死在脚本的 `BRAND_SNAPSHOT` 里，公式漂了脚本会直接报错退出（允许单通道 ±1 的取整差）。
- **换色相不动 tone 阶梯**：各家族（primary / secondary / tertiary / error / neutral / neutral-variant）的色度与每一档 tone 完全相同，所以五套色板的对比度逐位一致——深色 `primary` 压白字 3.72:1（够图形与大字的 3:1），浅色 6.4~6.5:1。要加一套色板，改脚本 `SEEDS` + `ColorSource` 两项，重跑即可。
- **纯黑背景不是第六套色板**：`withPureBlack()` 只覆盖表面那八档。它们是色度 2 的近黑，任何色相在 tone < 10 处都看不出差别，所以五套共用一份色值。
- 壁纸取色不是开关而是一个选项：Android 12+ 走 `dynamicColorScheme`，低版本设置页直接不出现这块。旧的 `dynamicColor` 布尔只在 `SettingsStore.colorSource()` 里做一次性迁移。

色板验证不需要屏摄目测：`adb shell screencap` 后取色块中心的像素，应当与该色板 `Dark.primary` 完全相等（品牌粉 `#D85A76`、紫 `#AF6ABE`、蓝 `#5083DE`、青绿 `#029584`、琥珀 `#B9752D`）；页面底色则是该色相 neutral tone 6 的值（粉 `#171213`、青 `#101413`）。

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
- 底部导航：首页 / 发现（女优 · 类型 · 厂商）/ 我的；片单支持列表、详情、成员勾选、单条移除、新建、编辑（含删除）

## 测试

```powershell
cd D:\missu
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

**adb 只有一份**：在 SDK 里（`D:\Android\Sdk\platform-tools\adb.exe`），用户级 `ADB` 与 `ANDROID_HOME` 都指向 SDK。

**adb server 起不来，报 `cannot open ...\Temp\adb.log: Permission denied`** —— 原因是 **Windows 完整性级别（Mandatory Integrity Control），不是杀软**（2026-11-14 定案）：

- `adb.exe` 以 **Low IL** 运行（同一个 shell 里 `cmd /c whoami /groups` 是 **Medium**）；
- `%TEMP%` 是 Medium 标签，Low 写 Medium 属于 **write-up，一律拒绝**，所以连空文件都创建不出来；
- `D:\missu` 之所以能写，是因为 `scripts/fix-workspace-acl.ps1` 把整个工作区打成了 **Low** 标签（不是"杀软排除列表"）。

判定实验（都对得上）：`%TEMP%`、`C:\Users\user\Documents`、`D:\adb-tmp` 全部拒绝；`icacls <dir> /setintegritylevel L` 之后同一个目录立刻可写。所以早前「卡巴斯基拦 adb」的结论是误判，给 adb 加杀软例外没有效果。谁把 adb 降成 Low 没有继续追（杀软的应用控制规则最可疑），但不影响修法。

**修法（一次性，普通权限，不需要临时目录）**：在系统临时目录里预建这个日志文件并打 Low 标签——Low 写 Low 是允许的：

```powershell
New-Item -ItemType File -Force "$env:TEMP\adb.log" | Out-Null
icacls "$env:TEMP\adb.log" /setintegritylevel L
```

之后 `adb start-server` 直接用系统 `%TEMP%`，`TMP/TEMP` 不需要任何覆盖。**注意**：`%TEMP%\adb.log` 被清理掉（磁盘清理、Storage Sense）后 adb 会再次报同样的错，重跑上面两行即可。

**构建环境**

- **JVM 写某个目录被拒，但 PowerShell 能写** → 这是安全软件按**可执行文件**拦截，不是文件权限。
  实测：当时 AS 自带 JBR 能写 `D:\gradle-home`，`D:\Android\jdk-17` 的 java 被拒，同一文件、同一用户。
  现在构建用独立 Temurin（`D:\Android\jdk-21`），写入正常；换任何 JDK 前都先验证它的 `java.exe` 能正常跑。
- **`native-platform.dll` 加载失败**（历史问题）→ 当时把 `GRADLE_USER_HOME` 挪出了用户目录；现在标准位置
  `%USERPROFILE%\.gradle` 实测可用（缓存已迁回，约 4.6 GB），不要再自定义 Gradle 主目录。
- **`AccessDeniedException: %TEMP%\global_synthetics_*`**（历史问题）→ 当时把 `TMP`/`TEMP` 指到 `.tmp-gradle`；2026-11-14 用默认 `%TEMP%` 全量重建通过，已不需要，`scripts/build.ps1` 里的覆盖也删了。
- **`FAIL_ON_PROJECT_REPOS` 冲突** → 本机 `GRADLE_USER_HOME/init.d/mirrors.gradle` 会注入阿里云镜像，`settings.gradle.kts` 不能设那个模式（镜像已同时写进工程，换机器也能用）。
- **AGP 9 内置 Kotlin** → 再加 `org.jetbrains.kotlin.android` 会直接报错。
- **compileSdk 必须是 37 + `compileSdkMinor = 1`** → `compose-bom-alpha:2026.11.00`（Compose 1.13-rc）要求 ≥ 37.1；只装 37.0 会解析失败。
- **`scripts/build.ps1` 必须是 UTF-8 带 BOM** → Windows PowerShell 5.1 对无 BOM 的 UTF-8 按 ANSI 解码，中文全角字符会吞掉行尾换行，把下一行代码并进注释（曾导致 `JAVA_HOME` 从未生效）。**用工具/AI 改这个文件后一定要重新写回 BOM**（`[System.Text.UTF8Encoding]::new($true)`），否则一改就坏。
- **Gradle daemon 占住 stdout** → 自动化采集输出时加 `MISSU_NO_DAEMON=1`，否则管道永远等不到 EOF。
- **Windows 11 Smart App Control 会拦新 exe，且报错像是杀毒软件**：`VerifiedAndReputablePolicyState = 1` 时，没有 Microsoft 信誉背书的新二进制一律被拦，
  提示是「应用程序控制策略阻止了文件」。本机表现：cmdline-tools 23.0 的 `android.exe`（Google 有效签名）跑不起来，
  而**在卡巴斯基里加受信任/排除项毫无作用**——排查时先看注册表那个值，别在杀软里绕圈。关 SAC 不可逆（需重装 Windows 才能恢复）。
  被拦时连 `sdkmanager` 都不可用（报 `Unable to load native library ... 应用程序控制策略阻止了文件`）。
  要么放行该 exe/目录，要么继续用旧的纯 Java 版 19.0。
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
- **片单有两种移除入口，别混用**：详情页面板是 `POST /api/playlists/remove`（body 要 `{dvdId, key}`，
  `key` 就是片单 id）；片单详情页是 `DELETE /api/playlists/{片单id}/{影片id}`（页面内联脚本
  `deletePlaylistItem()` 里的原始调用）。片单页只有片单 id，走前者需要先查一次 `key`，没必要。
- **首页 4 个板块来自同一次请求**：`GET /` 的 HTML 里就是 4 个兄弟 `<h2>`（新作上市 / 最近更新 / 無碼影片 / 隨機），不是 4 个接口——`getHome()` 只发一次请求。真正各自独立的是各板块「更多」指向的列表页（`/cn/new`、`/cn/release`、`/cn/uncensored-leak`、`/cn/random`），打开时才各发一次。
- **空结果页仍带 `.thumbnail.group` 外壳**：搜不到东西时页面残留一个没有 `<img>` 的卡片壳、外加一个指向域名/`#` 的锚点。按 `.thumbnail.group` 直接取卡片会解析出一张「标题是 missav.ai、没有封面」的幽灵卡片，UI 上就是一块空缩略图。规则：**id 必须含字母或数字，且必须有 `<img>`**，否则丢弃——这样空结果才会走到空态。
- **Cloudflare 挑战会真的落在手机上，且链路已验证可用**（2026-11-14 真机实测）：冷启动加载首页时被 403 挑战，
  App 自动跳到 `ChallengeActivity` 打开验证页（logcat 可证：`MainActivity` 起来后 1.3 s 就 `Displayed
  app.missu/.ui.challenge.ChallengeActivity`），页内状态文案依次是「等待站点放行…（第 N 次检查）」「验证通过，
  正在读取页面…」，通过后自动返回首页继续加载，**全程无需用户手动操作**（只在 40 次轮询仍不通时给「取消」）。
  PC 的 JVM 请求同样会被挑战（`cf-mitigated: challenge`），所以这条链路不是"未验证的兜底"：
  改任何 Cookie 相关代码都必须保证 OkHttp 与 WebView 共用系统 `CookieManager`，否则验证过了仍然 403。

**多语言（i18n）**

- **英文是 base**（`res/values/`），简体 `values-zh/`、繁體 `values-b+zh+Hant/`（BCP-47 script 限定符，zh-TW/HK/MO 都命中）、日文 `values-ja/`。没匹配的语言自动回退英文——「自动检测系统语言 + 回退英语」不用写代码，是资源机制的默认行为。
- **繁體用 `b+zh+Hant` 而不是 `values-zh-rTW`**：后者只命中台湾，香港/澳门会掉回简体。条款文案按台湾用语（設定 / 登入 / 影片 / 片單 / 儲存 / 桌布 / 網路）。
- **一个设置项同时决定界面与内容语言**（`AppLanguage` + `uiLocale()` / `contentLanguage()`）。别拆成两个开关：详情页的信息行标签是站点按内容语言返回的，拆开会得到「中文界面 + 英文标签」的混排。选项只列**有 UI 翻译**的语言。
- **`:core` 不放界面文案**（纯 JVM 没有资源系统）：筛选项的**值**留在 `ActressFilterOptions`，文案在 app 侧按 `section.key` 组合（`optionLabel()`）；异常消息只当类型用，界面文案由 `UiMessage` 映射。
- **ViewModel 不存拼好的文案**：状态里放 `UiMessage`（类型 + 参数），显示时 `@Composable UiMessage.text()` 取字符串。否则切换语言后，屏幕上留着的还是旧语言的字符串。
- **切换语言不重建 Activity**：`MissuRoot` 用 `createConfigurationContext` 生成带目标 locale 的 Context，再用 `CompositionLocalProvider(LocalContext / LocalConfiguration)` 罩住整棵树——改设置即重组生效，`stringResource` 读的就是这个 Context。
- 需要按内容语言重载的数据，用**内容语言**做 key：`LaunchedEffect(contentLanguage) { homeViewModel.load() }`。
- **不要用 PowerShell 的 `.Replace()` + `Set-Content` 改源码**：本仓库已经踩过两次，结果是文件里**字母被换成别的字母**（`import`→`impore`、`Experimental`→`rxperimental`、`compose`→`coepose`），编译器报一堆莫名其妙的"未解析引用"。改代码只用编辑工具；批量替换脚本一旦跑过，必须用 `git diff` 逐行确认。PS 5.1 的 `Set-Content -Encoding UTF8` 还会加 BOM。

**UI 与交互**

- **底部栏高度**：`ShortNavigationBar` 自己处理窗口 inset，外面不要再加一次 `insets.bottom`，否则底栏高度翻倍（Expo 版踩过）。
- **系统栏内边距只在壳层处理一次**（`Scaffold` + `WindowInsets.safeDrawing`）；页面里各自加 `statusBarsPadding`
  会变成"每加一个页面都要记得加"，而且极易漏。需要贴边的页面单独 opt-out。
- **列表页的空/错状态要区分**：网络上失败不能显示成"内容为空"，否则用户以为数据丢了。
- **列表 key 必须在数据里唯一，跨页拼接时按 key 去重**：`LazyVerticalGrid(items, key = …)` 的 key 重复会直接抛 `IllegalArgumentException: Key "…" was already used`，崩掉整个页面（真机踩过：发现页女优目录翻页后重复了一个 `/actresses/…`，位置在 2026-11-14 的 `adb logcat -b crash`）。站点的分页会**重叠**（下一页开头又出现上一页的条目），所以 `current + page` 之后必须 `distinctBy { 与 key 相同的表达式 }`；单页来源（片单列表/片单详情）同理——站点可能把同一项列两次。
- **整屏页面的转场不能用 alpha**：交叉淡入 = 两页文字叠在一起；顺序淡出再淡入 = 中间整屏空底色（"闪一下"）。用 shared axis 整幅横滑（两页全程不透明），方向按底栏顺序定。选型判据见 `.agents/skills/md3e` 的运动系统（有无持续容器 → container transform / shared axis / fade through / fade），本工程各场景的落点与时长见 `DESIGN.md` 的「Motion」小节。
  **例外是卡片 → 详情**：那里有持续容器（封面 → 播放器框），所以走 container transform + fade through（`ui/components/HeroMotion.kt`），页面横滑只在其余路由用。
- **共享元素的三个硬条件**（缺一个就静默不播，看起来像"没做"）：
  ① 两个页面在转场期间必须同时在组合里——导航转场的时长不能是 0，否则退场页立刻被移除；
  ② 共享元素动画的时长必须**等于**导航转场时长（容器用 `tween` 而不是空间 spring），否则转场结束时动画会被硬切成终点；
  ③ 目标一侧的元素必须在**首帧**就存在（详情页 Loading 时也要有那个框），所以封面靠路由参数 `detail/{id}?img=` 传过去，等详情数据回来再换。
- **共享元素覆盖层默认按父布局裁剪**（`ParentClip`）：卡片那一格会把长大的元素切掉，必须显式给 `clipInOverlayDuringTransition = OverlayClip(shape)`。作用域用 `CompositionLocal` 传（`LocalSharedTransitionScope` + 目的地自己的 `AnimatedVisibilityScope`），不要一路加参数。
- **静帧转动态画面的接缝**：容器长大到位时视频通常还没出画，所以**封面要留到 `Player.Listener.onRenderedFirstFrame`**才撤（提前撤就是"封面突然消失 + 黑框"），控件也只在出画后才显示（`PlayerOverlay(visible = ready && controlsVisible)`）——加载中把进度条和播放键摆出来，等于在还没东西可控的时候先给一堆控件。撤回用封面 200ms 淡出（封面画在播放器之上，所以是"画面显影"而不是"画面替换封面"）。等待中的指示器延迟 600ms 出现，秒开时不闪。
- **两种等待要用两套指示器**：页面取数用 `LoadingIndicator`（形变团块），视频缓冲用 `CircularWavyProgressIndicator`（波浪环）。官方选择表只按"等待时长 + 能否算进度"分，但同一屏上两种等待混用同一个团块，看起来就像"整个 App 都在转"；形状家族是成本最低的场景区分手段。
- **封面虚焦用两层静态图交叉淡入**，不要逐帧改 `Modifier.blur` 半径——后者每帧重建 RenderEffect，中端机会掉帧。清晰层 + 虚焦层各画一次，只动 alpha，成本恒定；`Modifier.blur` 在 API < 31 是空操作，那里只剩压暗也算降级。另外**封面画在播放器之上会把 `PlayerDefaults.ErrorOverlay` 挡住**，所以播放失败必须回调上层把封面撤掉，否则用户看到的是"虚焦封面 + 永远在转的环"。
- **`remember(key)` 的 key 不能是会中途变化的派生值**：曾经把虚化的 `Animatable` 按 `playbackUrl` 做 key，而它会在详情返回时从 `null` 变成真地址 → 状态被重建、正在跑的动画作用在旧实例上被丢弃，表现是"虚化一小段 → 弹回清晰封面 → 一直清晰"。按**稳定身份**（路由 id）做 key；视频/详情数据都是这个身份下的产物。
- **"要离开了"用 `isCurrent`（目的地还是不是栈顶）判断，不要拦截返回键**：`popBackStack` 是同步的，转场**第一帧** `currentBackStackEntry` 就已经变，比目的地被销毁（`onDispose`）早整整一个转场。拿它来停音频、把共享容器换回封面，既不参与者返回决策，也不影响预测性返回的手势预览；用 `BackHandler` 抢返回则会把这个预览吃掉。
- **窗口底色必须跟主题同步**：`res/values/themes.xml` 的 `windowBackground` 是静态值，浅色下任何透明帧都会露黑；`MainActivity.onCreate` 先按持久化主题设一次，`MissuTheme` 里继续同步。
- **页签级 ViewModel 放 Activity 作用域**：放在 `composable()` 里会随页签销毁，切回来变成「新建 VM → Loading → 重新请求」——用户看到先一帧旧画面再转圈。同理，`LaunchedEffect(语言)` 这类重载不要放在目的地里，否则每次切回都触发。

**M3E / Compose 1.13-rc（预发布栈）**

- **全套 M3E 只在 1.5.0 线**：稳定线 material3 1.4.0 只剩主题 + 动效 + `ShortNavigationBar`；`FloatingToolbar` /
  `ButtonGroup` / `LoadingIndicator` / `ToggleButton` / 柔性顶栏都要 1.5。换 BOM 时先看 `.agents/skills/md3e/references/version-baseline.md`。
- **`compose-bom-alpha:2026.11.00` 要求 `compileSdk ≥ 37.1`**：只装 `android-37.0` 会在解析依赖时失败，
  装 37.1 后可以把 37.0 卸掉（`sdkmanager --uninstall "platforms;android-37.0"`），本机已这么做。
- **实验 API 逐文件 opt-in**：`LoadingIndicator` 是 `@ExperimentalMaterial3ExpressiveApi`，`TopAppBar`/`Tooltip` 是
  `@ExperimentalMaterial3Api`；用文件级 `@file:OptIn(...)`，别在每处调用点写。
- **Carousel 的真实签名和网上示例不一样**：`rememberCarouselState(itemCount = { … })`（**lambda**，不是 Int），
  宽度参数是 `preferredItemWidth` 而不是 `itemWidth`（beta01 起改名）。照示例写必然编译失败。
- **退役但还能编译**：`SegmentedButton` / `NavigationBar` / `Medium|LargeTopAppBar` 在 M3E 里已被替代
  （`ToggleButton` 连通组 / `ShortNavigationBar` / 柔性顶栏）。编译不报错，但写出来就是「M3 味」而不是「M3E 味」。
- **整页转圈统一 `LoadingIndicator`**：基线 `CircularProgressIndicator` 已经全换掉；极小尺寸（14–16dp）也用它，
  注意它**没有** `strokeWidth` 参数。
- **`ToggleFloatingActionButton` 会吞掉 `Modifier.align`**：直接给它挂 `align(BottomEnd)` 会被摆到左上角（实测），
  必须在外面包一层 `Box(Modifier.align(...))`。
- **`containerColor` 参数是普通 lambda**（`(进度) -> Color`，不是 `@Composable`）：主题色要先在组合作用域里读出来再捕获。
- **带 FAB 的 `HorizontalFloatingToolbar` 重载没有 `trailingContent`**：想要溢出容器就用普通重载，
  溢出项放进 `trailingContent = { AppBarRow { clickableItem(..., icon = ...) } }`——`clickableItem` 的 `icon` 是必填。
- **`TextFieldState` 在 foundation 1.13 只剩 `edit { }`**：`setTextAndPlaceCursorAtEnd` / `clearText` 都没了，
  改写成 `edit { replace(0, length, value) }`。
- **搜索的 `SearchBarState` 方法都是 suspend**：`animateToExpanded/Collapsed` 必须放进 `scope.launch`；
  输入框获得焦点会自动展开，不要手动调。

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