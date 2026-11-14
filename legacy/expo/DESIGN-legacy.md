# 归档：Expo 版的组件框架与口径

> 这份说明只描述**已归档**的 React Native 实现（`legacy/expo/src/`），不再构建、不再维护，也**不是待办清单**。
> 现役基线是仓库根的 Kotlin + Jetpack Compose 工程，设计语言与控件口径见根目录 `DESIGN.md`。
>
> 保留它的唯一理由：碰到「为什么现在不这么做」时有个出处，别照着它写 native 代码。

## 自绘原语

界面只允许用这些原语拼，新控件先加到这里，不要在页面里就地发明：

| 原语 | 文件 | 用途 | 当年取代了 |
|---|---|---|---|
| `Sheet` | `src/components/ui/sheet.tsx` | 底部弹层：遮罩淡入 + 卡片上滑，`onRequestClose` 接返回键 | Compose `BottomSheet`、Paper `Modal` |
| `Menu` | `src/components/ui/menu.tsx` | 工具栏下拉：触发器 + 现测坐标的浮层，下方放不开自动翻到上方 | Paper `Menu`、Compose `DropdownMenu` |
| `Pill` | `src/components/ui/pill.tsx` | 胶囊按钮（选中只填充色） | Paper `Chip`、Compose `ToggleButton` |
| `UiButton` | `src/components/ui/button.tsx` | 40dp 按钮，`text` 次要 / `filled` 主操作 | Paper `Button`、Compose `Button`/`TextButton` |
| `Checkbox` | `src/components/ui/checkbox.tsx` | 22dp 勾选框 | Compose `Checkbox` |
| `SearchField` | `src/components/ui/search-field.tsx` | 搜索输入框（放大镜 + 清空） | Paper `Searchbar` |
| `Toast` | `src/components/ui/toast.tsx` | 底部悬浮提示，自动消失 | Paper `Snackbar` |

现状：现役 native 版**没有**这层自绘原语，直接用 Material 3 Expressive 组件（见根 `DESIGN.md` 的 Control Map）；
保留这张表只是为了让「当年为什么不用 @expo/ui / Paper」这段结论有出处。

## 当年为什么不用第三方 UI 库（两条原始记录）

- `@expo/ui` 的 Compose 控件（旧版曾用 DropdownMenu / BottomSheet / Picker / ToggleButton / Checkbox）：托盘、波纹、选中图标、弹层高度都在 Material 内部，`seedColor` 只能近似配色。SDK 58 早期 Expo Go 内置版本落后于 npm，曾两次把 dev 环境搞崩（白屏、`Cannot set prop 'modifiers'` 红条）；当时 `bundledNativeModules.json` 与 npm 都是 `~58.0.28`，版本错配风险已消除——不用它的理由仍是观感与可配置性，不是稳定性。已全部移除。
- `react-native-paper`：整个 Material 库只为 5 个控件存在（Snackbar / Chip / Searchbar / Menu），主题还得再对一遍。已全部移除。

## 当时的控件口径

这里是从 `DESIGN.md` 搬出来的 legacy 行。**「现役」一列写的是今天换成什么了**（没写的说明 native 已不再有这个概念）。

| 场景 | 当年（Expo） | 现役 |
|---|---|---|
| 底部导航 | `expo-router/js-tabs` 的 `Tabs`：纯 RN 栏，3 项固定（首页/发现/我的），配色只来自 theme | `ShortNavigationBar`（M3E） |
| 页头 | `TabHeader` 自绘：每个 tab 页自己画大标题，不用原生 Stack header | `PageTopBar`；首页顶部直接是 `SearchBar`，不另画大标题 |
| 频道 / 周期 / 筛选 / 排序 | `ChoiceMenuBar` + `Menu`：40dp 触发器 + 自绘浮层，选中项只改文字颜色 | 现役 App 没有频道/周期入口（站点那两套页面没做）；排序是连通 `ToggleButton`，筛选是 `ModalBottomSheet` + `FilterChip` |
| 厂商频道组 | `CollapsibleGroup` + `SettingsRow`：默认折叠三组（素人/无码影片/亚洲 AV），路径见 `src/constants/channel-groups.ts` | 厂商是一张三列目录网格，没有折叠分组；那份静态清单（站点改版要手工同步）也一起消失了 |
| 长按预览 | `VideoCard` 长按 + 全屏 `Modal`：350ms 长按（带 haptic）弹静音循环预览；预览片由封面地址推导为同目录 `preview.mp4`，加载失败保留封面；松手停留，点空白/返回键关闭，短按仍进详情 | 已实现（`ui/preview/VideoPreview.kt`）：长按 500ms（`combinedClickable` 默认阈值）、全屏 `Dialog`、86% 黑幕、关闭 150ms 淡出。预览地址改为**解析卡片 HTML 的 `data-src`**，不再按封面推导——变体不同，文件名不固定 |
| 搜索 | `SearchBox` + `SearchField`，聚焦时展示搜索历史胶囊 | M3E `SearchBar` 就地展开成全屏 `ExpandedFullScreenSearchBar` |
| 自动播放 | 设置行 + 系统 `Switch`：默认开，关掉后打开视频保持暂停 | **恒开**，没有设置项（播放器就绪即 `playWhenReady = true`） |
| 视频快进退 | 播放器下方 6 个按钮（10 秒 / 1 分钟 / 10 分钟双向，36dp 高），按下态 accent 填充 + 白字 | 用 media3 `PlayerDefaults` 的中心「快退 / 播放暂停 / 快进」官方按钮，不再自绘一排 |
| 倍速设置 | 底部条「倍速」按钮 + `Sheet`：0.75–2x，选中的**存进偏好并对所有视频生效** | 底部小胶囊 + `DropdownMenu`，0.5–2x；**只作用于本次播放**，换视频回到 1x |
| 播放器控件 | 自建覆盖层 + 全屏 `Modal`：播放/暂停常驻底部条左侧；进度条单击/拖动、连击快进退（N 击 = N-1 次）；单击只显隐控件；快进退用居中圆角提示 700ms 后消失；控件 150ms 淡入淡出；全屏锁横屏 | media3 官方槽位 + 右侧倍速/全屏两颗 `OverlayChip`；单击只显隐控件，**没有双击快进退** |
| 反馈 | `Toast` + haptics：收藏、登录、播放手势 | 没有 Toast、没有 haptic；错误与结果都是页面内联文案（`UiMessage`） |

## 当时的已知限制

- `Sheet` / `Menu` 都是 RN `Modal`：遮罩会盖住底部 tab 栏，这是 Modal 的正常行为，弹层内不要再依赖 tab 栏可见
- `Menu` 的浮层坐标是按下那一刻 `measureInWindow` 测的：浮层打开后不会跟随列表滚动（Modal 已经挡住滚动），因此不要在会自己动的容器里当锚点
- 女优筛选从「4 个原生 Picker」改成「手风琴 + 胶囊」：滚轮/下拉在 26 个年份选项上比胶囊网格慢，改动是取舍不是退步
- 存储只有一套（`expo-sqlite/kv-store`）：早前写在 AsyncStorage 里的历史 / 搜索记录 / 登录资料不再迁移，从那些版本升上来会丢这批本地数据
- 从详情页跳列表必须用 `router.dismissTo`（弹出详情页）而不是 `push`/`replace`：详情页留在根栈里不卸载，播放器与 ExoPlayer 实例会一直挂着（现役对应：`NavController.popBackStack`）
- 片单面板里行点击和 `Checkbox` 的 `onValueChange` 是同一次点击的两条路径，用同步 ref 去重，否则一次点击会切两次
- `Toast` 渲染在 `Sheet` 之外时会被 Modal 遮罩盖住：片单面板的操作失败文案直接写在面板里，不用 Toast
- 播放/暂停、全屏、倍速按钮必须用 RNGH `Gesture.Tap`，不能用 RN `Pressable`：双击后第一次点击会被手势竞技场吞掉
- 视频画面只挂一个 `Tap` 手势：长按加速曾与它组成 `Simultaneous` 组合，`onFinalize` 受渲染期闭包影响会漏掉、倍速卡住，已整体移除
- 全屏 Modal 内要嵌套 `GestureHandlerRootView`；内联播放器保持挂载、Modal 共享同一 player；退出时重挂内联 `VideoView` 刷新 SurfaceView 并 seek 回原时间点
- 退出全屏重挂 surface 会有约 300–400ms 画面停顿，是双视图 Modal 方案的已知取舍
- seek 期间 `playingChange` 的短暂 paused 要屏蔽，避免暂停图标闪烁；快进后主动显示控件

## 当时的技术栈

- Expo SDK 58 + React Native 0.88 + TypeScript
- 路由 `expo-router`；UI 是 `legacy/expo/src/components/ui/` 的七个自绘原语 + Reanimated + Gesture Handler
- 数据 `legacy/expo/src/api/*`；存储 `expo-sqlite/kv-store`；媒体 `expo-video` / `expo-image` / `react-native-webview`
- 不再构建、不再运行；`node_modules` 已删除，需要时 `bun install`
