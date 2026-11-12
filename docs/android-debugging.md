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

`devices -l` 可能同时显示 IP 地址和 mDNS 名称两个条目，它们可以属于同一台手机。使用输出中的 `transport_id` 指定设备，以下示例使用 `2`，实际操作时替换为当前值：

```powershell
.\adb -t 2 shell input keyevent KEYCODE_WAKEUP
```

## 启动应用

先在项目目录保持开发服务运行：

```powershell
cd D:\missav-mobile
bun run start
```

然后在手机 Expo Go 中扫码。也可通过 ADB 打开已安装的 Expo Go：

```powershell
.\adb -t 2 shell monkey -p host.exp.exponent -c android.intent.category.LAUNCHER 1
```

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

## 构建独立 APK

需要脱离 Metro 验证图标、启动图或网络行为时使用。首次构建或修改 `app.json` 后先执行 `bunx expo prebuild --platform android` 生成 `android/`。本机工具链不在系统 PATH，位于 `.local/toolchain`（JDK 17、Android SDK、Gradle、Node），并已 gitignore。

```bash
cd D:/missav-mobile/android
export JAVA_HOME=/d/missav-mobile/.local/toolchain/jdk-tmp/jdk-17.0.2
export ANDROID_HOME=/d/missav-mobile/.local/toolchain/sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="/d/missav-mobile/.local/toolchain/node-v22.20.0-win-x64:$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/d/missav-mobile/.local/gradle-home
/d/missav-mobile/.local/toolchain/gradle-9.4.1/bin/gradle assembleRelease --no-daemon \
  -PreactNativeArchitectures=arm64-v8a
```

- `android/` 是 `expo prebuild` 生成目录，已 gitignore。重新 prebuild 后必须在 `android/gradle.properties` 补回 `expo.android.cmakeObjectPathMax=240`，否则 Windows 260 字符限制会让 ninja 在 gesture-handler 的 codegen 阶段报 `Filename longer than 260 characters`。
- 产物：`android/app/build/outputs/apk/release/app-release.apk`。
- release 变体沿用模板的 debug 证书，仅适合自用侧载；换成正式 keystore 后，已安装的旧包必须先卸载才能覆盖安装。
- 安装：`.\adb -t 2 install -r D:\missav-mobile\.local\Missu-1.0.0-arm64-v8a.apk`。

## 常用自动化命令

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
New-Item -ItemType Directory -Force D:\missav-mobile\.local
.\adb -t 2 shell screencap -p /sdcard/missav-debug.png
.\adb -t 2 pull /sdcard/missav-debug.png D:\missav-mobile\.local\missav-debug.png
```

查看 React Native JavaScript 日志：

```powershell
.\adb -t 2 logcat ReactNativeJS:V "*:S"
```

## 截图黑屏时读取界面文本

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

脚本只在 127.0.0.1 上监听 `8787`，凭据不出现在命令行参数、日志或提交里；Ctrl+C 结束，`adb reverse --remove tcp:8787` 可手动撤销映射。服务没起时点按钮会提示先运行脚本。

## 结束调试

```powershell
.\adb disconnect 手机IP:连接端口
```

调试完成后可关闭手机的无线调试。不要记录或提交真实配对码；`.local/` 已被 Git 忽略，可保存本地截图和诊断输出。
