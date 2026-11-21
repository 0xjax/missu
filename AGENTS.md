# missu

| 目录 | 状态 | 说明 |
|---|---|---|
| 仓库根 | **现役 / 基线** | Kotlin + Jetpack Compose；Gradle 根就是仓库根 |
| `legacy/expo/` | **legacy** | Expo SDK 58 参照实现与设计规格；不构建、不运行，需要对照时先 `bun install` |

交付包名 `app.missu`，应用名 `Missu`，仅 Android。设计语言见 `DESIGN.md`，构建与调试见 `docs/`。

## 命令

```powershell
.\scripts\build.ps1              # Android CLI 构建 debug APK
.\scripts\build.ps1 install      # 构建 + 装到设备
.\scripts\build.ps1 run          # 构建 + 装机 + 启动
.\scripts\build.ps1 release      # R8 + 资源压缩，归档到 .local/releases，沿用 debug 签名
.\scripts\build.ps1 check        # core/app 测试 + lint + debug 构建
.\scripts\build.ps1 emulator     # 按模板创建/启动 Android 17.2 x86_64 模拟器
.\scripts\build.ps1 preview      # 常驻渲染 Compose Preview 到 .local/previews
.\scripts\build.ps1 analyze      # 构建并分析 debug APK
.\scripts\build.ps1 analyze-release # 构建并分析 release APK
```

`scripts/build.ps1` 经独立 Android CLI（`android`）调用 Gradle，自动配置 JDK（`D:\Android\jdk-*` 取最高版本）与 `ANDROID_HOME=D:\Android\Sdk`。`ANDROID_USER_HOME` 不得指向 Low IL 工作区。自动化采集输出加 `MISSU_NO_DAEMON=1`。Gradle wrapper 9.6.0；Android Studio 不是构建依赖。

## 开发流程

- 业务模块：`:core` 为纯 JVM 取数/解析/模型，禁止 `android.*` / `androidx.*`；`:app` 为 UI 与平台适配，单向依赖 `:core`。`:baselineprofile` 仅生成启动 Profile，不进交付 APK。
- 修改后只执行 `build.ps1 run`，装机后交用户验收；未经明确要求，禁止运行测试、`check` 或自动化实测。仅在提交前执行一次 `build.ps1 check`，连续修正与纯文档修改不跑。`install/run` 默认用 Android 17.2 x86_64 模拟器，设 `ANDROID_SERIAL` 才指定真机。
- 未经用户明确要求，构建装机后**禁止点击、截屏、dump UI 或抓日志**。用户指定的 `journeys/*.xml` 验证按 `android-cli` 技能逐步执行，不改写成坐标脚本。
- 观感缺陷先量化并定位到窗口底色 / 转场 / 状态机 / 组合生命周期；同层连续两次调参仍复现 → 换层排查。
- 真机纯操作长链路交给用户；需要读屏判断的操作才由 agent 明确驱动。调试细节见 `docs/android-debugging.md`。
- `adb devices` 无条目 → 先查 `adb mdns services`；不得据此声称设备离线或无线调试未开启。发现服务后按当前广播地址连接，禁止复用旧端口。
- adb 的 `%TEMP%\adb.log` 拒写 → 预建文件并 `icacls <file> /setintegritylevel L`，不要覆盖 `TMP/TEMP`。

## 真机权限边界

无线 adb 默认禁止，仅放行：

- `build.ps1 install/run`；启动/停止本应用；本应用内的 `android input` / `adb shell input` 与留在本应用的 `keyevent`；只读 `android screen/layout` / `screencap` / `uiautomator` / `dumpsys` / `logcat`（尽量 `--pid`）。
- 操作前 `dumpsys window | grep mCurrentFocus` 必须指向 `app.missu`；误落别处可直接启动本应用返回。
- 其他操作先说明目的与恢复方式：系统设置、`cmd`、指向别处的 `pm/am`、`pm clear` / `uninstall`、HOME/RECENTS/POWER、系统弹窗、桌面、通知栏。
- 解锁、授权、连网、点系统弹窗交给用户。禁止 `reboot`、`fastboot`、`su`、`pm disable-user`、分区或格式化。

## 工程约束

- 默认首页只呈现非露骨、有实际价值的内容；禁止用成人封面、热度或连续刺激争夺默认注意力。
- 成人内容入口必须清晰且由用户主动触发；禁止暗中限制、羞辱或制造罪恶感。
- 禁止惩罚性障碍、观看/自控力评分和强迫打卡；提醒、专注功能须可选。
- 新内容、推荐和增长功能不得绕开以上原则；实现细节只记 `DESIGN.md`。
- UI 使用 **Material 3 Expressive + 自己的 Compose**，不引第三方 UI 组件库；行为不可配置的组件宁自绘或换库。
- Android API/工具用法先用 `android docs search/fetch`；性能与 R8 分析分别使用项目级 `jetpack-compose-performance` / `r8-analyzer` 技能。
- MD3E 选型先查 `.agents/skills/md3e`；当前 material3 `1.5.0-rc01`，不用 `SegmentedButton` / `NavigationBar` / `Medium|LargeTopAppBar`。图标统一 `material-icons-extended` Outlined，仅明确状态语义用 Filled，不自绘或混入其他图标源。
- 不做无实测需求的状态管理、查询缓存、绘图库或抽象；优先简单实现，不顺手改相邻代码。
- 版本与插件坐标统一在 `gradle/libs.versions.toml`；站点、播放器、Coil 共享 `AppDependencies` 的 OkHttp 基础设施，各自保留请求头、Cookie 与超时。
- 播放画面和进度状态优先 Media3 Compose 原生 API；控件外观与手势按 `DESIGN.md` 自绘。
- 仅适配 Android；注释用中文，提交信息用英文 Conventional Commits；注释标签只用 `CRITICAL` / `WARNING` / `TODO` / `NOTE`。
- 浮点比较、累计或链式计算前先按业务精度舍入，禁止原始 `==`。
- 界面文案走 `res/values/strings.xml` 及三个翻译目录；`:core` 只放值，ViewModel 存 `UiMessage`，不存拼好的字符串。
- Kotlin 包名 `app.missu`，源码在 `app/src/main/java/app/missu/`。

## 站点取数

优先级：**站点 JSON 接口 → HTML 解析 → Blade 表单 POST**，不得跳级。

- 端点、参数与取值只能来自站点脚本/表单、legacy 请求构造或真实响应；无法确认就不实现。不得靠页面链接猜测前端脚本能力。
- 使用 HTML / 表单时：代码 `NOTE` 或 `WARNING` 写明无 JSON 接口及依据；真实结构 fixture 离线测试锁定；结论记入 `docs/android-native.md`「已知坑 · 站点契约」。
- 按“脆弱面 × 使用频率”决定是否实现，不因其他功能高效就引入解析。
- WebView 只用于人机验证：组合根部 `ChallengeOverlayHost` 获取 `cf_clearance` 后重试原请求，必须共享系统 `CookieManager`；不做通用浏览或表单兜底。

## Git / Secrets / Tools

- 当前分支提交，不建 PR 或 worktree；用户验收后再提交。`git add` 等本地可恢复操作无需确认；`git commit` 每次确认一次，批准后可与暂存合并执行；push、merge、rebase、reset、分支/标签删除及历史改写逐操作确认，force push / 历史改写必须明确精确命令与范围。不得把一个文件的改动拆进两个提交。
- 禁止读取 `.env*`、SSH 私钥等秘密文件；禁止在日志、注释、提交、工具输出或命令参数中暴露秘密。需要环境变量值时直接向用户索取。
- 内容搜索用 Grep，文件定位用 Glob，读写编辑用 Read / Edit / Write；长文件分批处理。
- `legacy/expo/` 不参与验证。结构变化后同步 `AGENTS.md` / `DESIGN.md` / `docs/android-native.md`。
- 验收以 `build.ps1 check` 成功为基线；默认真机流程见「开发流程」。
- 升级 SDK / JDK 前按 `docs/android-native.md` 检查 Android CLI 与 Smart App Control 兼容性，并回写结论。
