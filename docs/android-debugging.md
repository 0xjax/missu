# Android 真机调试与自动化

用 Android Platform-Tools 的 ADB 控制手机，无需完整 Android Studio。**现役应用是 Kotlin 工程（仓库根；Gradle 根 = 仓库根）**：改代码 → Gradle 编译 → `adb install` → 重启 Activity，没有 Metro 与 Fast Refresh。文末的「legacy Expo」一节只记录不再使用的旧流程与仍然成立的结论。

## adb 在哪（只有一份）

**adb 就在 Android SDK 里**：`D:\Android\Sdk\platform-tools\adb.exe`。不要再单独解压一份 platform-tools——曾经就是 `D:\platform-tools` 与 SDK 各一份（16.7 MB 重复）。

Android 插件按这个顺序解析 adb：**`ADB` 环境变量 → PATH 上的 `adb` → `ANDROID_HOME`/`ANDROID_SDK_ROOT` + `/platform-tools/adb`**。本机只设这两项，都指向 SDK：

| 设置（用户级） | 值 | 作用 |
|---|---|---|
| **PATH** | `D:\Android\Sdk\platform-tools` | 插件按 PATH 找到 `adb`；终端里也能直接敲 `adb` |
| `ANDROID_HOME` | `D:\Android\Sdk` | 给 Gradle/SDK 工具用，也是插件的第三级回落 |

不再单独设 `ADB` 变量——PATH 已覆盖同样作用，少一个真相来源。

**不要再建第二份**。`D:\platform-tools` 已删除（原来是 SDK 那份的重复拷贝）。改完后需要**重启一次宿主程序**（DSH/终端）才会生效——PATH 也是进程启动时读取的，和之前的环境变量一样，不是终端的特例。

```powershell
adb --version
```

注意 PowerShell 默认不从**当前目录**加载命令：在 `platform-tools` 目录里仍要写 `.\adb`；PATH 只让它在其它目录可用。这里改的是**用户级** PATH，不是系统级。

**已知坑**：如果 adb server 起不来并报 `cannot open ...\Temp\adb.log: Permission denied`，那是 **Windows 完整性级别**问题——adb 以 Low IL 运行，而 `%TEMP%` 是 Medium 标签，Low 写 Medium 被拒（不是杀软，加杀软例外没用；诊断见 `docs/android-native.md` 的「已知坑」）。修法是给日志文件打 Low 标签，然后用系统默认临时目录：

```powershell
New-Item -ItemType File -Force "$env:TEMP\adb.log" | Out-Null
icacls "$env:TEMP\adb.log" /setintegritylevel L
D:\Android\Sdk\platform-tools\adb.exe start-server
```

`%TEMP%` 被清理后重跑一次即可；不需要给 `TMP/TEMP` 做任何覆盖。

## 无线配对与连接

手机需支持无线调试（通常为 Android 11 及以上），并与电脑连接同一 Wi-Fi。

1. 手机进入“开发者选项”→“无线调试”。
2. 选择“使用配对码配对设备”，查看配对地址和六位配对码。
3. 在 PowerShell 执行：

```powershell
.\adb pair 手机IP:配对端口
```

在 `Enter pairing code:` 提示后输入配对码。配对码不得写入项目文件或提交到 Git。

4. 配对成功后先看设备列表——**通常不需要再 `adb connect`**：

```powershell
.\adb devices -l
```

配对信息（电脑的 `~/.android/adbkey` 与手机记下的公钥）是一次性的。之后只要无线调试开着，adb server 就会通过 mDNS
自动发现并连上，serial 形如 `adb-<设备ID>-<随机后缀>._adb-tls-connect._tcp`。只有 mDNS 不可用时（见下文「重开无线调试为什么能自动恢复」）
才需要按“无线调试”主页的地址手动 `.\adb connect 手机IP:连接端口`。

**配对端口与连接端口通常不同。** 配对端口来自配对弹窗，一般每次配对都会变化；连接端口来自无线调试主页，**每次重新开启无线调试都会变**。不要复用上一次记录的端口。

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

## 重开无线调试为什么能自动恢复

手机每次打开“无线调试”都会重新在 mDNS 上注册 `_adb-tls-connect._tcp`：**端口每次都变**（实测例：`192.168.1.4:41953`），
实例名后缀（serial 里最后一段）也可能跟着变。adb server 常驻并持续浏览这个服务类型，看到即连——所以关掉再打开
**不需要任何重连操作**：重连是 server 做的，不是人做的。配对键一直有效，也不会再要求配对码。

实测（2026-11-14/15，platform-tools 37.0.1 / adb 1.0.41）：

- `adb kill-server` 之后只跑 `adb start-server`、**不发任何 `connect`**，1.5 秒内设备自己回来，`transport_id` 换成新的（20 → 1）；
- 想看手机到底广播了什么地址：`adb mdns services`（比在手机上翻 IP 快）；
- 端口每次都变 ⇒ **不要在任何脚本、笔记、命令历史里记端口**，用 `adb devices` 现查 serial。

自动发现失效时只能回到手动 `adb connect 手机IP:端口`，**此时不会自愈**：路由器/系统防火墙挡了组播、VPN 虚拟网卡抢了路由、
删了 `~/.android/adbkey` 或在手机上撤销了调试授权（后者要重新配对）。主动设 `ADB_MDNS_AUTO_CONNECT=0` 是关掉自动连，不是故障。

**`adb devices` 显示 `device` ≠ 链路真的通**：长时间 idle 后 transport 可能还挂在 server 里但已经死了。判断链路要做一次真实往返：

```powershell
.\adb -s adb-<设备ID>-<随机后缀>._adb-tls-connect._tcp shell echo alive
```

## DSH 的 Android 插件

装的是 `@zseven-w/dsh-android`（第三方 bundle），提供 20 个 `android_*` 工具：`android_devices`、`android_boot`、`android_screenshot`、`android_ui_tree`、`android_tap_element`、`android_interact`、`android_logs`、`android_build_run` 等；安装后还会注册 `android-ui-automation` skill（工具用法说明，按需加载）。

它通过环境变量 `ADB` 找 adb：

```powershell
[Environment]::SetEnvironmentVariable('ADB', 'D:\platform-tools\adb.exe', 'User')
```

该变量只在 DSH 进程启动时读一次，改完必须重启 DSH；否则工具报 `adb is unavailable`。

## 安全软件会拦子进程和写入

卡巴斯基有两条互相独立的拦截，踩过多次：

1. **PDM（系统监控）拦 DSH 的子进程**：DSH 启动 `powershell.exe`、批量改文件权限时会报 `PDM:Exploit.Win32.Generic`，工具侧表现为 `spawn EPERM`。
   处理：受信任应用程序里加 `C:\Users\user\AppData\Local\Programs\DeepSeek Harness\DeepSeek Harness.exe`，勾「不监控应用程序活动」「不监控子应用程序活动」（递归）；排除项目录加 `D:\missu`、`C:\Users\user\.dsh`。
2. **按可执行文件拦写入**：某个 `java.exe` 即使被加进"受信任应用程序"，写工作区外目录仍可能被拒（当年 `D:\Android\jdk-17` 就是这样，而当时 AS 自带的 JBR 正常）。
   判别方法：同一个文件分别用 PowerShell 和 JVM 写一次，只有 JVM 被拒 = 程序级拦截，不是文件权限。
   处理：构建用 `D:\Android\jdk-21`（Temurin，`scripts/build.ps1` 自动发现）；换 JDK 前先确认它的 `java.exe` 能正常跑。

## 构建、安装与迭代

```powershell
cd D:\missu
.\scripts\build.ps1            # debug APK
.\scripts\build.ps1 install    # 构建并直接装到当前设备
adb shell am start -n app.missu/.MainActivity
```

- 增量构建通常 10–30 秒；**没有 Fast Refresh**，改完必须重新编译装机才能看到效果。
- 自动化采集输出时设 `MISSU_NO_DAEMON=1`，否则 Gradle daemon 会占着 stdout，管道等不到 EOF（命令看起来"卡死"，其实构建早已结束）。
- `scripts/build.ps1` 里固定了 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_USER_HOME`；这些不是可选项，原因见 `docs/android-native.md` 的「已知坑」。（`TMP/TEMP` 已不再覆盖。）

独立 release 包：

```powershell
.\scripts\build.ps1 release
# 产物：app\build\outputs\apk\release\app-release.apk
```

release 沿用 debug 证书，仅适合自用侧载；换成正式 keystore 后，已安装的旧包必须先卸载才能覆盖安装。

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
.\adb -t 2 shell screencap -p /sdcard/missu-debug.png
.\adb -t 2 pull /sdcard/missu-debug.png D:\missu\.local\missu-debug.png
```

查看日志（Kotlin/Java 与系统都在 logcat 里）：

```powershell
.\adb -t 2 logcat -s AndroidRuntime:E ActivityManager:I
```

## 截图黑屏时读取界面文本

> Agent 侧优先 `android_ui_tree`（结构化节点）；`uiautomator dump` 是人工兜底。

部分页面/报错状态截图为全黑（安全窗口或渲染层在截屏时不可见），但视图层级仍然可读。用 `uiautomator` 导出后按 `text` 字段查看当前页面内容：

```powershell
.\adb -t 2 shell uiautomator dump /sdcard/ui.xml
.\adb -t 2 shell cat /sdcard/ui.xml
```

在输出中筛 `text="..."` 即可拿到按钮、输入框和错误提示文字；`bounds="[x1,y1][x2,y2]"` 可直接用于计算点击坐标。比反复截图更可靠。

## 结束调试

```powershell
.\adb disconnect 手机IP:连接端口
```

调试完成后可关闭手机的无线调试。不要记录或提交真实配对码；`.local/` 已被 Git 忽略，可保存本地截图和诊断输出。

## legacy Expo（只记录结论，不再使用）

Expo 版已停止构建与运行（`node_modules` 已删）。以下结论仍然成立，迁移前值得回看：

- **Metro 重启会断开 App**：每次重启都是新的 dev 会话，手机必须重连；`TMP/TEMP` 指向工作区可避免 `metro-file-map` 与 hermesc 的 EPERM。
- **Expo Go 的自动检测靠 mDNS 组播**：WLAN 为「公用网络」或有 VPN 虚拟网卡时可能发现不到，但 TCP 8081 仍通——「自动检测不到」不等于「连不上」，直接开 `exp://<电脑局域网IP>:8081`。
- **电脑 IP 是 DHCP**：地址一变，URL 与「最近项目」同时失效；根治靠路由器 MAC 绑定固定 IP，或 `adb reverse`。
- **底部导航栏错位**：从二级页快速连按两次返回（间隔 ≤600ms）回到 tab 页时，底栏会落到屏幕中间、约 2 秒自愈。成因是 `useFocusEffect` 在原生栈转场中重渲染导致重新测量；修法是把焦点刷新延后到转场结束（400ms）。Kotlin 版没有这条链路，但**同类教训适用**：不要在导航转场进行中触发整树重新测量。
- **测试登录（已废弃）**：Expo 版靠 `bun scripts/dev-test-login.js` 读 `.env`、经 `adb reverse tcp:8787` 把凭据送进 Debug 构建——那套链路已删除。Kotlin 版改用「记住账号」：第一次手动输入后凭据用 Android Keystore 加密存在设备上，会话失效时自动重登（见 `android-native.md`）。凭据仍然 MUST NOT 进命令行、日志或仓库。
