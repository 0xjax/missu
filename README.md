# missu

Missu 的 Android 客户端。**仓库根就是 Kotlin + Jetpack Compose 工程的根**（Gradle 根 = 仓库根），这是唯一现役、也是标准规范路径的基线实现。
Expo / React Native 的旧实现已归档到 [`legacy/expo/`](legacy/expo/)，只作参照实现与设计规格，不再构建运行。

## 快速开始

```powershell
.\scripts\build.ps1 check           # 测试 + lint + debug 构建
.\scripts\build.ps1 run             # 构建并启动到 Android 17.2 x86_64 模拟器（ANDROID_SERIAL 可指定真机）
.\scripts\build.ps1 preview         # 渲染 Compose Preview 到 .local/previews
.\scripts\build.ps1 analyze         # 构建并分析 debug APK
```

首次或换机器时的工具链配置、版本矩阵、环境清单、已知构建坑，见 [`docs/android-native.md`](docs/android-native.md)。

## 目录

```
app/                  Android 应用模块（UI + 平台适配）→ 交付包 app.missu
core/                 纯 JVM 模块（取数 / 解析 / 模型，不许 android.* / androidx.*）
baselineprofile/      独立启动 Profile 生成器（仅测试，不进入交付 APK）
journeys/              Android CLI Journey 关键路径用例
gradle/libs.versions.toml      依赖与插件版本唯一来源
content/literature/   诗文可读内容源（原文、注释、意译、赏析与多语言元数据）
scripts/build-literature.ts  从同一语料生成带 FTS5 索引的核心库与可选完整文库包
scripts/build.ps1     Android CLI 统一入口：构建 / 装机 / 运行 / 检查 / 预览 / 分析 / 调试
scripts/emulator/     medium_phone AVD 的确定性架构与硬件规格模板
settings.gradle.kts   Gradle 根配置（:app + :core + :baselineprofile）
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
| Android CLI | `%USERPROFILE%\AppData\AndroidCLI\android.exe`（已加入用户 PATH；配置 `%USERPROFILE%\.androidrc`） |
| Android SDK | `D:\Android\Sdk`（编译 API 37.1 + Build-Tools 36 + Platform-Tools） |
| Android Emulator | `medium_phone`：Android 17.2（API 37.2）、x86_64、16 KB 页大小；日常 install/run 默认 headless 使用，Device Hub 提供完整窗口 |
| JDK | `D:\Android\jdk-21`（Temurin 21，免安装；AS 已卸载） |
| Gradle | wrapper 9.6.0（发行包走腾讯镜像），缓存 `%USERPROFILE%\.gradle` |
| adb | `D:\Android\Sdk\platform-tools\adb.exe`（在 SDK 里，只有一份；用户级 PATH 已包含该目录） |

## 现状

当前版本 **3.0.0-beta.1**（`versionCode = 9`）。3.0 已进入 Beta 验收阶段，主要功能形态基本确定，仍不代表正式完工。Kotlin 版是基线实现：

- 首页（非露骨内置指南 + 主动影片入口）、书房（诗文原文/注释/意译/赏析/搜索/收藏 + 英语双语阅读/点词释义）、详情、搜索（M3E 搜索栏 + 历史）
- 影片导航（热门 / 女优 / 类型 / 厂商 / 频道，女优筛选与排序）、收藏、观看历史 + 继续播放
- 漫画导航：拷贝漫画 / 肉漫屋 / 18Comic / 卡拉漫画 / MyComic，共用热门、最新、搜索、详情与竖向阅读 UI
- 片单：列表、详情、批量移除、新建、编辑（含删除）
- 设置（视频单列/双列、外观、播放）+ 7 种内容语言、记住账号（Keystore 加密存凭据、失效自动重登）

UI 全部按 Material 3 Expressive 写（material3 1.5.0-rc01），组件选型与动效口径见 `DESIGN.md`。

## 归档实现怎么用

`legacy/expo/` 只用于对照历史行为与设计规格：`src/api/missav.ts`（接口构造）、`src/api/parse.ts`（HTML 解析规则）、`app.json`（旧配置）。
要重跑它需要先 `cd legacy/expo; bun install`，但**不要**把它当交付路径——它不构建、不验收。
