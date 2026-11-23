# Android 设备调试与自动化

日常通过独立 Android CLI 构建、装机、读屏和控制设备，无需 Android Studio；ADB 保留为配对、日志与底层诊断工具。**现役应用是 Kotlin 工程（仓库根；Gradle 根 = 仓库根）**：改代码 → `scripts/build.ps1 run` → Android CLI 调用 Gradle、装机并启动，没有 Metro 与 Fast Refresh。文末的「legacy Expo」一节只记录不再使用的旧流程与仍然成立的结论。

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

配对只需一次。成功后先看设备列表，通常不需要再 `adb connect`；自动发现机制见「重开无线调试为什么能自动恢复」。

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

- 自动发现正常时不要手敲 `adb connect`，日常用 mDNS 那条 serial；若 `mdns services` 已发现但 `devices` 始终无条目，按当前广播地址手动连接一次。
- 只用手动：设 `ADB_MDNS_AUTO_CONNECT=0`，adb 不再自动连 mDNS，只保留 `adb connect` 建的那条（IP 固定、mDNS 偶发掉线时更稳）。

出现重复时，删掉**你不用的那条**：

- 默认用自动发现：删手动那条 —— `.\adb disconnect <IP>:<端口>`
- 手动模式（已设 `ADB_MDNS_AUTO_CONNECT=0`）：删 mDNS 那条 —— `.\adb disconnect adb-<设备ID>-<随机后缀>._adb-tls-connect._tcp`

## `adb devices` 为空时：先区分“发现”与“连接”

**`adb devices` 无条目只表示当前没有已建立的 ADB transport**，不能据此声称设备离线、无线调试未开启或要求用户重复打开开关。先查广播：

```powershell
adb mdns services
```

- 有 `_adb-tls-connect._tcp` 服务：设备正在广播无线调试；若自动连接未完成，使用**该输出当前的 IP:端口**执行 `adb connect`，不要复用历史端口。
- 没有任何服务：再检查手机无线调试、同一 Wi-Fi、组播/VPN/防火墙；到这一步才能说“未发现设备”，仍不能武断地说开关未打开。
- `devices` 有 `offline`：这是已有 transport 失效，不是未发现设备；清掉该 transport 后按上面的 mDNS 结果重连。

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

## 无线安装大文件卡住：先看链路

`Standard Install: Sent ...` 只表示电脑端写入本地 ADB 通道，**不代表手机已收到，更不是安装进度**。若随后出现 `Unexpected end of channel` / `device offline`，先跑 `adb -s <serial> shell echo alive`，再用 `adb push` 的临时文件大小变化和传输期间的 Ping 判断真实吞吐；`devices` 显示 `device`、小文件成功或空闲 Ping 正常都不足以证明大文件链路健康。

2026-11-23 的一次个案是手机到原路由器在负载下严重丢包，实测仅 51–140 KiB/s。电脑改连手机热点后绕开原路由器，安装期间 0% 丢包，同一 67.94 MiB APK 约 18 秒成功。手机可继续连接上游 Wi-Fi，热点是电脑到手机的独立子网；地址仍现查 `adb mdns services`，不复用旧端口。厂商若不允许热点接口运行 ADB，改用电脑有线连接或 USB。不要靠反复重启、重新配对或增加安装超时掩盖链路问题。

## 安全软件会拦子进程和写入

JVM 写目录被拒但 PowerShell 可写时，用同一文件分别测试；只有 JVM 失败即程序级拦截。构建固定使用 `D:\Android\jdk-21`，换 JDK 后先验证 `java.exe` 可执行且可写。

## 构建、安装与迭代

```powershell
cd D:\missu
.\scripts\build.ps1 check     # 测试 + lint + debug 构建
.\scripts\build.ps1 run       # 默认 headless 启动 Android 17.2 x86_64 模拟器，再构建、装机、启动
.\scripts\build.ps1 install   # 只构建与装机，不启动
.\scripts\build.ps1 preview   # 常驻渲染 Compose Preview 到 .local\previews
.\scripts\build.ps1 analyze   # 构建并分析 debug APK
.\scripts\build.ps1 analyze-release # 构建并分析 release APK
.\scripts\build.ps1 trace     # 采集启动 Perfetto trace
.\scripts\build.ps1 heapdump  # 采集应用 HPROF
.\scripts\build.ps1 hub       # 打开 Device Hub
```

- 设备命令默认使用 `medium_phone` 模拟器；只有设 `ANDROID_SERIAL=<serial>` 才指定真机或其他设备。装机启动后默认交由用户验收，确认后再提交，不自动追加点击与截图。
- 自动化采集输出时设 `MISSU_NO_DAEMON=1`；CLI 将详细构建日志写入系统临时目录。
- `scripts/build.ps1` 固定 `JAVA_HOME` 与 `ANDROID_HOME`，其余用户目录保持默认。Android CLI 与 `ANDROID_USER_HOME` 不得放到 Low IL 工作区。
- `trace` / `heapdump` 自动安装 profileable release，避免用 debuggable 构建的 ART 开销评估性能。Compose BOM 同时提供 `runtime-tracing`；`startup.zip` 包含系统与 Compose trace，`app.hprof` 为堆快照。

独立 release 包：

```powershell
.\scripts\build.ps1 release
# 产物：app\build\outputs\apk\release\app-release.apk
# 归档：.local\releases\Missu-<versionName>-arm64-v8a.apk（同时输出 SHA-256）
```

release 沿用 debug 证书，仅适合自用侧载；换成正式 keystore 后，已安装的旧包必须先卸载才能覆盖安装。

## 常用自动化命令

> 仅在用户明确要求的真机操作或视觉诊断中使用；默认流程在装机启动后交由用户验收。优先 CLI 的结构化布局与选择器，ADB 用于底层操作。

```powershell
$serial = '<从 adb devices 复制>'

# 结构化读屏：优先 layout；WebView 或图形内容再截图
android layout --device=$serial --pretty
New-Item -ItemType Directory -Force D:\missu\.local
android screen capture --device=$serial -o D:\missu\.local\missu-debug.png
android screen capture --device=$serial --annotate -o D:\missu\.local\missu-annotated.png

# 按语义定位，不手写坐标；文本匹配默认是不区分大小写的子串
android input --device=$serial tap 'text=搜索,clickable=true'
android input --device=$serial text 'SONE' 'class=EditText' --submit

# 底层按键与日志仍用 adb
adb -s $serial shell input keyevent KEYCODE_BACK
adb -s $serial logcat -s AndroidRuntime:E ActivityManager:I
```

截图由 CLI 直接写 PNG，避免 PowerShell 5.1 原生输出重定向破坏二进制内容；截图后必须先查看图片，再继续判断或操作。

## Journey 关键路径

`journeys/core-navigation.xml` 覆盖首页、影片、我的与设置导航。由 agent 按项目级 `android-cli` 技能逐条执行，使用 `layout` / `screen` 判读、语义选择器操作；失败时报告实际状态，不把 Journey 改写成固定坐标脚本。2026-11-20 模拟器复验全部通过；设置页语言区域以正常速度滚动后的截图确认，不以 CLI 的滚动状态或单次布局快照代替视觉验收。

## 截图黑屏时读取界面文本

部分页面/报错状态截图为全黑（安全窗口或渲染层在截屏时不可见），但视图层级仍然可读。优先用 `android layout --full --pretty` 获取 JSON 节点；需要完整原始 XML 时才用 `uiautomator dump` 兜底：

```powershell
android layout --device=$serial --full --pretty
adb -s $serial shell uiautomator dump /sdcard/ui.xml
adb -s $serial shell cat /sdcard/ui.xml
```

布局输出包含 `text`、`content-desc`、`interactions`、`state` 与 `bounds`；优先把这些字段交给 `android input` 选择器，不手算坐标。

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
