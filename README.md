# missu

Missu 的 Android 客户端。**现役实现是 Kotlin + Jetpack Compose**（`android-native/`）；仓库根保留的是 Expo/React Native 的旧实现，只作参照与设计规格，不再构建运行。

## 快速开始

```powershell
cd android-native
.\build.ps1 install          # 构建 debug APK 并装到已连接的设备
adb shell am start -n app.missu/.MainActivity
```

首次或换机器时的工具链配置、版本矩阵、已知构建坑，见 [`android-native/README.md`](android-native/README.md)。

## 目录

```
android-native/      Kotlin + Compose 工程（现役）
  app/src/main/java/app/missu/
  build.ps1          构建脚本（配好 JDK/SDK/临时目录）
  README.md          构建说明、版本矩阵、迁移顺序、已知坑

src/                 legacy Expo/React Native 源码（参照实现）
app.json             legacy Expo 配置
DESIGN.md            设计语言、控件规格、迁移中仍然有效的约束
AGENTS.md            仓库规则（命令、依赖策略、验证方式）
docs/android-debugging.md   真机调试：无线 ADB、截图、日志、卡巴斯基与 ACL 的坑
```

## 工具链

| 件 | 位置 |
|---|---|
| Android SDK | `D:\Android\Sdk`（API 37.0 + Build-Tools 37 + Platform-Tools） |
| JDK | Android Studio 自带 JBR 21 |
| Gradle | wrapper 9.6.0（发行包走腾讯镜像），缓存 `%USERPROFILE%\.gradle` |
| adb | `D:\platform-tools\adb.exe` |

## 迁移进度

- ✅ 工程骨架可编译、可装机
- ✅ 工具链收口（全局 SDK/JDK、标准 Gradle 缓存、清掉约 10 GB 冗余）
- ⬜ 网络层 + Jsoup 解析（`getHome` 验证）
- ⬜ 首页 / 详情 / 播放 / 搜索 / 登录
- ⬜ 发现 / 片单 / 收藏 / 历史 / 设置

功能规格以 legacy Expo 版为参照：`src/api/missav.ts`（22 个接口）、`src/api/parse.ts`（HTML 解析规则）、`DESIGN.md`（控件与交互规格）。
