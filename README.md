# missu

Missu 的 Android 客户端。**仓库根就是 Kotlin + Jetpack Compose 工程的根**（Gradle 根 = 仓库根），这是唯一现役、也是标准规范路径的基线实现。
Expo / React Native 的旧实现已归档到 [`legacy/expo/`](legacy/expo/)，只作参照实现与设计规格，不再构建运行。

## 快速开始

```powershell
.\scripts\build.ps1 install          # 构建 debug APK 并装到已连接的设备
adb shell am start -n app.missu/.MainActivity
```

首次或换机器时的工具链配置、版本矩阵、环境清单、已知构建坑，见 [`docs/android-native.md`](docs/android-native.md)。

## 目录

```
app/                  Android 应用模块（UI + 平台适配）→ 交付包 app.missu
core/                 纯 JVM 模块（取数 / 解析 / 模型，不许 android.* / androidx.*）
scripts/build.ps1             构建脚本（配好 JDK / SDK / ANDROID_USER_HOME / 临时目录）
settings.gradle.kts   Gradle 根配置（:app + :core）
gradlew(.bat)         Gradle wrapper 9.6.0

legacy/expo/          归档：Expo SDK 58 的 React Native 实现（参照实现 + 设计规格，不构建）
DESIGN.md             设计语言、控件规格、迁移中仍然有效的约束
AGENTS.md             仓库规则（命令、依赖策略、验证方式）
docs/android-native.md        构建说明、版本矩阵、环境清单、已知坑
docs/android-debugging.md     真机调试：无线 ADB、截图、日志、卡巴斯基与 ACL 的坑
docs/api/                     站点接口的 OpenAPI 记录
.agents/skills/               仓库内置的 agent skills（md3e 等）
```

## 工具链

| 件 | 位置 |
|---|---|
| Android SDK | `D:\Android\Sdk`（API 37.1 + Build-Tools 36 + Platform-Tools） |
| JDK | `D:\Android\jdk-21`（Temurin 21，免安装；AS 已卸载） |
| Gradle | wrapper 9.6.0（发行包走腾讯镜像），缓存 `%USERPROFILE%\.gradle` |
| adb | `D:\Android\Sdk\platform-tools\adb.exe`（在 SDK 里，只有一份；用户级 PATH 已包含该目录） |

## 现状

Kotlin 版已覆盖产品全部功能，是基线实现：

- 首页（推荐轮播 + 网格 + 下拉刷新）、详情（信息行可点、播放器全屏/倍速/进度）、搜索（M3E 搜索栏 + 历史）
- 发现（女优 / 类型 / 厂商，女优筛选与排序）、收藏、观看历史 + 继续播放
- 片单：列表、详情、批量移除、新建、编辑（含删除）
- 设置 + 7 种内容语言、记住账号（Keystore 加密存凭据、失效自动重登）

UI 全部按 Material 3 Expressive 写（material3 1.5.0-rc01），组件选型与动效口径见 `DESIGN.md`。

## 归档实现怎么用

`legacy/expo/` 只用于对照历史行为与设计规格：`src/api/missav.ts`（接口构造）、`src/api/parse.ts`（HTML 解析规则）、`app.json`（旧配置）。
要重跑它需要先 `cd legacy/expo; bun install`，但**不要**把它当交付路径——它不构建、不验收。
