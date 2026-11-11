# Impeccable Design Context

## Users

**Who**: 中文用户，Android 手机竖屏，单手操作，多在夜间或弱光环境使用。

**Platform**: 仅 Android。iOS 与 Web 不是交付目标，现存回退实现只为编译通过，不为它们做适配或验收。

**Context**: 目标明确——找到片子、确认信息、直接播放。单次会话短，进出频繁；不是生产力工具，没有长表单、没有多任务并行。

**Jobs to be done**:
1. 从首页或发现页快速找到想看的片子
2. 用筛选、排序把长列表收敛到可决策的范围
3. 搜索具体番号、女优、关键词
4. 看完回来接着看（历史、收藏）

## Brand Personality

**3 words**: 直接 · 克制 · 原生

- **直接**: 一屏说清楚这是什么、点下去会发生什么。不做悬念式布局，不藏主要操作。
- **克制**: 界面让位于封面内容。控件数量与视觉重量都要压到最低，凑合能用的一律不加。
- **原生**: 用系统控件、系统手势、系统尺寸。不自绘“看起来像原生”的替代品。

**Emotional goals**: 用户不应该注意到界面——注意力在封面和播放器上，操作一次到位，没有“这个控件怎么这么大”的瞬间。

## Aesthetic Direction

**Style**: 内容优先的原生 Material 3。黑底、两列封面、极简工具栏；控件只在需要时展开。

**Theme**: 跟随系统。深色是事实上的主体验（夜间使用为主），浅色可用但未逐屏校验。

**References**:
- Material 3 Expressive 的控件密度：分段按钮 40dp、菜单按钮 40dp
- 原生媒体类应用的信息密度：一屏 6 张卡，滚动前不铺三层控件

**Anti-references**（绝对不要）:
- Chip 墙：同一维度并排多组 Chip，视觉重量互相抵消
- 表单控件当工具栏：`ExposedDropdownMenuBox`(56dp) / 满宽等分输入框
- 整块高饱和色表达选中态
- 霓虹色、游戏化徽章、装饰性动效、骨架屏之上的骨架屏

## Design Principles

1. **原生优先** — 能用 `@expo/ui`（真 Compose）就不用 JS 自绘；Paper 仅保留 Searchbar、Snackbar、搜索历史标签。
2. **同维度单一控件** — 一个维度只允许一种控件形态；频道、周期、筛选、排序各自最多占一个按钮。
3. **尺寸守规范** — 控件高 40dp（分段/菜单）、页面左右留白 16dp；验收时用 `uiautomator dump` 量真实 bounds，不靠肉眼。
4. **选中态轻量表达** — 选中写进按钮文案（`筛选 · 中文字幕`）或分段高亮，不用整块色块抢注意力。
5. **状态一致** — 加载、空、错误、重试在首页/列表/发现/详情用同一套表达，错误必须可重试。

## Control Map

| 场景 | 控件 | 规格 |
|---|---|---|
| 底部导航 | `NativeTabs` | 原生 tab 栏，5 项固定 |
| 页头 | 各 tab 独立 Stack header | 大标题 + 右侧图标 |
| 频道 / 周期 / 筛选 / 排序 | `ChoiceMenuBar` | 40dp TextButton + 系统 DropdownMenu（Compose） |
| 一级分类（发现页） | `DiscoverTabs` | `SingleChoiceSegmentedButtonRow`，等分整宽 |
| 搜索 | `SearchBox` | Paper Searchbar，列表页与首页共用 |
| 反馈 | Snackbar + haptics | 收藏、登录、播放手势 |

NEVER 把多个 Compose `TextField` 塞进同一个 `Host` 并排——它们都是 `fillMaxWidth`，会互相挤扁。需要多控件时每个控件独立 `Host` 定宽。

## Design Tokens

- **Spacing**: 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 / 64，只用 `Spacing.*`
- **Radius**: 8（控件）/ 10 / 12（卡片）/ 9999（胶囊），只用 `Radius.*`
- **Color**: 深色底 `#000000`，卡片面 `#212225`，accent `#d85a76` 仅用于选中与强调
- **TabBarInset**: Android 80，滚动容器底部必须避开原生 tab 栏

## Known Limits

- Expo Go 内置的 ExpoUI 版本落后 npm 包：`SelectableDropdownMenuItem` 会白屏，菜单选中态用 `✓` 文案代替
- 不做 iOS / Web 适配：非 Android 平台走通用回退实现，不接受为其新增平台代码
- `expo-router/unstable-native-tabs` 已废弃，待迁移到 `expo-router/native-tabs`

## Technical Stack

- Expo SDK 58 + React Native 0.88 + TypeScript
- Expo Router（NativeTabs + 每 tab 独立 Stack）
- `@expo/ui`（Compose 路径为主）；`react-native-paper` 仅 Searchbar、Snackbar、搜索历史标签
- `expo-video`、`expo-image`、Reanimated、Gesture Handler
- 深色优先，中文界面，仅 Android 交付
