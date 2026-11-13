# Android 真机调试与自动化

使用 Android Platform-Tools 的 ADB 控制手机，无需安装完整 Android Studio。日常开发用 Expo Go + Metro；独立安装包（应用名 `Missu`，包名 `app.missu`）用于脱离 Metro 验证，构建见下文「构建独立 APK」。

## 安装 Platform-Tools

1. 下载官方 Windows 版：https://developer.android.com/tools/releases/platform-tools
2. 解压到 `D:\platform-tools`。
3. 打开 Windows PowerShell 5.1：

```powershell
cd D:\platform-tools
.\adb --version
```

PowerShell 默认不从当前目录加载命令，因此在当前目录也必须写 `.\adb`，不能只写 `adb`。本文不修改系统 PATH。

## 无线配对与连接

手机需支持无线调试（通常为 Android 11 及以上），并与电脑连接同一 Wi-Fi。

1. 手机进入“开发者选项”→“无线调试”。
2. 选择“使用配对码配对设备”，查看配对地址和六位配对码。
3. 在 PowerShell 执行：

```powershell
.\adb pair 手机IP:配对端口
```

在 `Enter pairing code:` 提示后输入配对码。配对码不得写入项目文件或提交到 Git。

4. 配对成功后，返回“无线调试”主页，查看连接地址，然后执行：

```powershell
.\adb connect 手机IP:连接端口
.\adb devices -l
```

**配对端口与连接端口通常不同。** 配对端口来自配对弹窗，一般每次配对都会变化；连接端口来自无线调试主页，重启手机或重新开启无线调试后也可能变化。不要复用上一次记录的端口。

`devices -l` 可能同时显示 IP 地址和 mDNS 名称两个条目，它们可能属于同一台手机，见下节「设备身份」。按输出中的 `transport_id` 指定设备，以下示例使用 `2`，实际替换为当前值：

```powershell
.\adb -t 2 shell input keyevent KEYCODE_WAKEUP
```

## 设备身份：一台手机可能有两个 serial

adb 按 serial（传输）记账，不按硬件去重。无线调试下同一台手机常常同时出现两条：

| serial | 来源 |
| --- | --- |
| `adb-<设备ID>-<随机后缀>._adb-tls-connect._tcp` | adb server 收到手机广播的 mDNS 服务后自动连上，无需手动操作 |
| `<IP>:<端口>` | 手动 `adb connect IP:端口` 建立 |

两条底层连的是同一个 adbd，所以 `devices -l` 里型号完全一样；任何按 `adb devices` 列设备的工具都会显示成两台。

**二选一，不要混用：**

- 只用自动发现：不要手敲 `adb connect`，日常用的就是 mDNS 那条 serial。
- 只用手动：设 `ADB_MDNS_AUTO_CONNECT=0`，adb 不再自动连 mDNS，只保留 `adb connect` 建的那条（IP 固定、mDNS 偶发掉线时更稳）。

出现重复时，删掉**你不用的那条**：

- 默认用自动发现：删手动那条 —— `.\adb disconnect <IP>:<端口>`
- 手动模式（已设 `ADB_MDNS_AUTO_CONNECT=0`）：删 mDNS 那条 —— `.\adb disconnect adb-<设备ID>-<随机后缀>._adb-tls-connect._tcp`

默认推荐保留自动发现：零配置，adb server 重启后自己会回来；手动那条只在 mDNS 被 VPN/防火墙挡住、或连远程设备时才用。反过来，如果刻意用手动模式，就别再 `adb connect` 之后又留 mDNS 那条。

## DSH 的 Android 插件

装的是 `@zseven-w/dsh-android`（第三方 bundle），提供 20 个 `android_*` 工具：`android_devices`、`android_boot`、`android_screenshot`、`android_ui_tree`、`android_tap_element`、`android_interact`、`android_logs`、`android_build_run` 等；安装后还会注册 `android-ui-automation` skill（工具用法说明，按需加载）。

它通过环境变量 `ADB` 找 adb：

```powershell
[Environment]::SetEnvironmentVariable('ADB', 'D:\platform-tools\adb.exe', 'User')
```

该变量只在 DSH 进程启动时读一次，改完必须重启 DSH；否则工具报 `adb is unavailable`。

## 安全软件会拦 DSH 的子进程

卡巴斯基的「系统监控（PDM）」会把 DSH 启动 `powershell.exe`、批量改文件权限这类行为判成 `PDM:Exploit.Win32.Generic` 并直接拦截，表现为工具报 `spawn EPERM`。处理方式：

- 受信任应用程序里加 `C:\Users\user\AppData\Local\Programs\DeepSeek Harness\DeepSeek Harness.exe`，勾「不监控应用程序活动」「不监控子应用程序活动」（递归）；
- 排除项目录加 `D:\missu`、`C:\Users\user\.dsh`；
- 配好后把「系统监控」重新打开（不必长期关闭）。

## 启动应用

先在项目目录保持开发服务运行：

```powershell
cd D:\missu
bun run start
```

然后在手机 Expo Go 中扫码。也可通过 ADB 打开已安装的 Expo Go：

```powershell
.\adb -t 2 shell monkey -p host.exp.exponent -c android.intent.category.LAUNCHER 1
```

想跳过扫码，直接用 Metro 打印的局域网地址打开本项目——把 `http://<电脑局域网IP>:8081` 换成 `exp://` 前缀：

```powershell
.\adb shell am start -a android.intent.action.VIEW -d "exp://<电脑局域网IP>:8081" host.exp.exponent
```

局域网地址比 `adb reverse` + `127.0.0.1:8081` 省事：`reverse` 映射随 adb 服务重启失效（重启后 App 会卡在 Bundling），局域网地址不依赖这条隧道。

唤醒屏幕不会解除密码锁；存在锁屏密码时需由用户手动解锁。

## 热重载与手动 reload

Metro 保持运行后，`src/` 下的 JavaScript/TypeScript 改动由 Fast Refresh 自动推送到设备，通常不打断页面状态。不要为了「让改动生效」去重启 Metro、重新扫码或 force-stop 应用——这些操作会丢掉正在调试的状态（播放进度、滚动位置、导航栈），比等一秒钟的刷新代价大得多。

以下情况才需要手动 reload：

- 修改 `app.json`、`babel.config.js` 等配置文件
- 新增或升级需要重新构建原生代码的依赖
- Fast Refresh 进入报错状态，红屏已经提示需要 reload
- 极少数模块级改动没有触发刷新

手动 reload 有两种方式：

```powershell
# 在手机上打开 Expo Go 的开发菜单，再点 Reload
.\adb -t 2 shell input keyevent 82
```

也可以在 Metro 终端按 `r`，或在手机上摇一摇唤起开发菜单。

当前项目没有任何 CSS 文件（`src/` 下的样式都在 `StyleSheet.create` 里）。若日后引入 CSS 方案（如 NativeWind），纯 CSS 文件的改动不会触发 Fast Refresh，需要在 Expo Go 中手动 reload。

## 重启 Metro 与「Expo Go 找不到开发服务器」

Metro 每次重启都是新的 dev 会话，手机上正在跑的 App 连接随即失效：**重连一次是必然的**，不是故障。只在改 `app.json`、`node_modules` 变了（装/删依赖）、Fast Refresh 已进入报错状态时才重启。

### 重启的正确姿势

```powershell
# 1. 确认 8081 没被上一轮残留进程占着
Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue |
  ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }

# 2. TMP/TEMP 指到工作区，否则 metro-file-map 与 hermesc 写 %TEMP% 会 EPERM
New-Item -ItemType Directory -Force D:\missu\.tmp-metro | Out-Null
$env:TMP='D:\missu\.tmp-metro'; $env:TEMP='D:\missu\.tmp-metro'

# 3. 依赖变动过就先清缓存
cd D:\missu; bun run start -- --clear
```

`.tmp-metro/` 命中 `.gitignore` 的 `.tmp-*`。不设 `TMP` 的后果是两个同源报错：`[metro-file-map] Cache write error: EPERM ...`（冷启动每次重建 file map）和 `bunx expo export` 在 Hermes 字节码阶段的 `Failed to open file ...\Temp\....hbc: permission denied`——都是 Temp 写权限问题，不是代码错误。

### 连不上时按顺序查三件事

```powershell
# ① Metro 活着吗
(Invoke-WebRequest http://localhost:8081/status -UseBasicParsing).Content   # packager-status:running

# ② 手机能不能连到这台电脑（手机 IP：.\adb shell ip -4 addr show wlan0）
& D:\platform-tools\adb.exe shell 'printf "GET /status HTTP/1.0\r\n\r\n" | nc -w 4 192.168.1.10 8081'

# ③ 服务端发给客户端的地址对不对
$h = @{ 'expo-platform'='android'; Accept='application/expo+json,application/json' }
$m = (Invoke-WebRequest http://192.168.1.10:8081 -Headers $h -UseBasicParsing).Content | ConvertFrom-Json
$m.extra.expoClient.hostUri
```

- ① 失败 → Metro 挂了，重启。
- ① 成功、② 失败 → 网络/防火墙问题。
- ①② 都成功 → **直接在 Expo Go 打开 `exp://<电脑局域网IP>:8081`，不要等自动检测**。Expo Go 的自动检测靠 mDNS 组播（UDP 5353），WLAN 处于「公用网络」配置、或有 VPN/代理虚拟网卡（如 FlClash 的 `198.18.0.1`）时它可能到不了手机；而 TCP 8081 仍可能是通的，所以「自动检测不到」不等于「连不上」。
- ③ 必须是电脑局域网 IP；若为 `localhost:8081`，说明只绑了回环，手机永远打不开。

### 最容易复发的一种：电脑 IP 变了

`192.168.1.10` 是 DHCP 分配的（`PrefixOrigin: Dhcp`）。路由器重新分配或换过网络后地址会变，`exp://192.168.1.10:8081` 与 Expo Go 的「最近项目」一起失效，表现和「自动检测坏了」一模一样。根治二选一：路由器里按 MAC 把电脑绑成固定 IP；或 `adb reverse tcp:8081 tcp:8081` + `exp://127.0.0.1:8081`。后者不依赖局域网和 mDNS，但映射随 adb 服务重启失效（见上文「启动应用」的取舍）。

## 构建独立 APK

需要脱离 Metro 验证图标、启动图或网络行为时使用。首次构建或修改 `app.json` 后先执行 `bun run prebuild -- --platform android` 生成 `android/`（走 `package.json` 的 `prebuild` 脚本，它会带上 `EXPO_NO_TYPESCRIPT_SETUP=1`——项目没有安装 `typescript`，直接 `bunx expo prebuild` 会报 `CommandError: ... Install typescript`）。本机工具链不在系统 PATH，位于 `.local/toolchain`（JDK 17、Android SDK、Gradle、Node），并已 gitignore。

```bash
cd D:/missu/android
export JAVA_HOME=/d/missu/.local/toolchain/jdk-tmp/jdk-17.0.2
export ANDROID_HOME=/d/missu/.local/toolchain/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="/d/missu/.local/toolchain/node-v22.20.0-win-x64:$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/d/missu/.local/gradle-home
/d/missu/.local/toolchain/gradle-9.4.1/bin/gradle assembleRelease --no-daemon \
  -PreactNativeArchitectures=arm64-v8a
```

- `android/` 是 `expo prebuild` 生成目录，已 gitignore。重新 prebuild 后必须在 `android/gradle.properties` 补回 `expo.android.cmakeObjectPathMax=240`，否则 Windows 260 字符限制会让 ninja 在 gesture-handler 的 codegen 阶段报 `Filename longer than 260 characters`。
- 产物：`android/app/build/outputs/apk/release/app-release.apk`。
- 留档与上传：按版本复制到 `.local/`（已 gitignore），版本号直接从 `app.json` 读，不要写死——写死的版本迟早过时：

  ```powershell
  $version = (Get-Content app.json -Raw | ConvertFrom-Json).expo.version
  Copy-Item android\app\build\outputs\apk\release\app-release.apk ".local\Missu-$version-arm64-v8a.apk"
  ```

  GitHub Release 的附件用同一个文件名（`Missu-<版本>-arm64-v8a.apk`），用户下载后能直接看出是哪个版本。
- release 变体沿用模板的 debug 证书，仅适合自用侧载；换成正式 keystore 后，已安装的旧包必须先卸载才能覆盖安装。
- 安装：`.\adb -t 2 install -r ".local\Missu-$version-arm64-v8a.apk"`。

## 常用自动化命令

> Agent 侧优先用 DSH 的 `android_*` 工具（`android_screenshot` / `android_ui_tree` / `android_tap_element` / `android_logs`），一次调用完成定位、点击并返回截图；下面这些 adb 命令是人工兜底。

```powershell
# 返回
.\adb -t 2 shell input keyevent KEYCODE_BACK

# 点击，坐标单位是设备像素
.\adb -t 2 shell input tap 630 1200

# 向上滑动
.\adb -t 2 shell input swipe 630 2200 630 900 500

# 输入英文或数字；input text 不适合直接输入中文
.\adb -t 2 shell input text "SONE"
```

PowerShell 5.1 的原生输出重定向可能破坏 PNG 二进制内容，截图使用设备端生成再拉取，不使用 `>`：

```powershell
New-Item -ItemType Directory -Force D:\missu\.local
.\adb -t 2 shell screencap -p /sdcard/missav-debug.png
.\adb -t 2 pull /sdcard/missav-debug.png D:\missu\.local\missav-debug.png
```

查看 React Native JavaScript 日志：

```powershell
.\adb -t 2 logcat ReactNativeJS:V "*:S"
```

## 截图黑屏时读取界面文本

> Agent 侧优先 `android_ui_tree`（结构化节点）；`uiautomator dump` 是人工兜底。

部分 React Native 页面/报错状态截图为全黑（安全窗口或渲染层在截屏时不可见），但视图层级仍然可读。用 `uiautomator` 导出后按 `text` 字段查看当前页面内容，例如登录错误：

```powershell
.\adb -t 2 shell uiautomator dump /sdcard/ui.xml
.\adb -t 2 shell cat /sdcard/ui.xml
```

在输出中筛 `text="..."` 即可拿到按钮、输入框和错误提示文字；`bounds="[x1,y1][x2,y2]"` 可直接用于计算点击坐标。比反复截图更可靠。

## 测试登录（Debug）

登录页在 Debug 构建里多一个「测试登录（Debug）」按钮：点一下就用手头 `.env` 里的测试账号登录，不必手输，也不用把密码写进任何命令。

```powershell
# 终端 A：读 .env 并起本地服务，同时把设备 8787 端口反向映射到电脑
$env:ADB = 'D:\platform-tools\adb.exe'
bun scripts/dev-test-login.js
```

脚本只在 127.0.0.1 上监听 `8787`，凭据不出现在命令行参数、日志或提交里；Ctrl+C 结束，`adb reverse --remove tcp:8787` 可手动撤销映射。

这条链路要**同时**成立：本机服务在跑 + `adb reverse tcp:8787 tcp:8787` 生效。点按钮报下面两种错，都是其中之一不在了：

- `fetch failed: java.net.ConnectException: Failed to connect to /127.0.0.1:8787` —— 脚本没在跑，或 reverse 映射掉了（adb server 重启、换设备、手机断连都会让映射失效）。重新跑一次脚本即可，它会重建映射。
- 服务不跨会话：DSH 重启会收掉后台任务，需要重新运行；有多台设备/多个传输条目时用 `ADB_SERIAL=<adb devices 里的 serial>` 指定目标。

## 已知坑：快速连按返回后底部导航栏错位

- **现象**：从二级页面连按两次返回（间隔 ≤600ms）回到 tab 页时，底部导航栏落到内容末尾（屏幕中间），约 2 秒后自愈。
- **成因**：tab 页的 `useFocusEffect` 在焦点到达时就 `setState` 重新拉数据；这次重渲染发生在原生 Stack 转场进行中，`(tabs)` 屏被重新测量一次，底部栏跟着内容高度走。没有焦点刷新行为的页面（例如 设置 → 内容语言）不会出现。
- **修法**：焦点刷新延后到转场结束（400ms）再执行，见 `src/app/playlists/index.tsx`、`src/app/history.tsx`、`src/app/saved.tsx`；以后新增「焦点时拉数据」的页面也照这个模式写。
- **验收**：连按两次返回（50ms 间隔最容易触发），在 400ms 内截图看底部栏位置；修复后应与静止状态像素一致。
- **教训**：复现和验收必须用报告里的原始路径。用「相似路径」验证会得出错误结论——设置/语言页不触发，片单/详情页触发。
## 结束调试

```powershell
.\adb disconnect 手机IP:连接端口
```

调试完成后可关闭手机的无线调试。不要记录或提交真实配对码；`.local/` 已被 Git 忽略，可保存本地截图和诊断输出。
