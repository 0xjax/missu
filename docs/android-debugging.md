# Android 真机调试与自动化

使用 Android Platform-Tools 的 ADB 控制手机，无需安装完整 Android Studio。ADB 只负责设备控制；当前项目仍需 Expo Go 运行应用，并保持电脑上的 Metro 开发服务运行。

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

## 结束调试

```powershell
.\adb disconnect 手机IP:连接端口
```

调试完成后可关闭手机的无线调试。不要记录或提交真实配对码；`.local/` 已被 Git 忽略，可保存本地截图和诊断输出。
