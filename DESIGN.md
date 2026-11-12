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
4. **选中态轻量表达** — 触发器只显示当前选项值（如 `全部`、`发行日期`），维度由按钮位置表达，不加「筛选 / 排序」前缀；菜单里的选中项只改文字颜色，不插入 `✓` 之类的字符前缀（会撑动布局、行宽跳动）。
5. **状态一致** — 加载、空、错误、重试在首页/列表/发现/详情用同一套表达，错误必须可重试。

## Control Map

| 场景 | 控件 | 规格 |
|---|---|---|
| 底部导航 | `NativeTabs` | 原生 tab 栏，4 项固定（首页/列表/发现/我的） |
| 页头 | 各 tab 独立 Stack header | 大标题 + 右侧图标 |
| 首页板块 | 首页 HTML 板块 + 顶部「今日热门」 | 板块来自首页 HTML；「今日热门」额外请求 `/today-hot` 列表页置顶，条数与其他板块一致不裁剪，失败时整块不显示，不影响其他板块 |
| 个人中心 | `SettingsRow` | 56dp 行、透明容器、右侧 chevron；二级页走根 Stack |
| 频道 / 周期 / 筛选 / 排序 | `ChoiceMenuBar` | 40dp TextButton + 系统 DropdownMenu（Compose） |
| 一级分类（发现页） | `DiscoverTabs` | 单行原生 `ToggleButton`（热门/分类/女优/厂商/排行），选中仅填充色 |
| 厂商频道组 | `CollapsibleGroup` + `SettingsRow` | 默认折叠三组（素人/无码影片/亚洲 AV），路径见 `src/constants/channel-groups.ts` |
| 目录栅格 | `FlatList` + `DirectoryCard` | 女优 / 排行 3 列（低分辨率竖图），分类 / 厂商 2 列（文字块） |
| 视频卡封面角标 | `VideoCard` | 左下变体标签（中文字幕 / 无码影片），右下时长；标签取自卡片 HTML，不新增请求 |
| 长按预览 | `VideoCard` 长按 + 全屏 `Modal` | 350ms 长按（带 haptic）弹静音循环预览；预览片由封面地址推导为同目录 `preview.mp4`（原站卡片自带，零额外请求），加载失败保留封面；松手停留，点空白/返回键关闭，短按仍进详情 |
| 详情信息块 | 详情页内联行 | 8 行（发行日期/番号/标题/女优/类型/系列/发行商/标籤），文案随内容语言变化不硬编码；标签 `textSecondary` 与值同行，可点击值用 accent 色跳 `/latest?source=path`。不用下划线：Android 嵌套 `Text` 的下划线会错位成漂浮短线 |
| 详情页片单 | 收藏旁「片单」按钮 + 原生 `ModalBottomSheet` | 打开时 `GET /api/playlists/{dvdId}`；点行或勾选框乐观切换再 `POST /api/playlists/add\|remove`；勾选框是 Compose 组件，配色由 `Host seedColor` 跟随 accent；超过 6 行放进 Compose `ScrollView`；「新建片单」就地展开单行输入框，提交后重新拉列表；未登录先跳登录 |
| 删除片单 | 片单详情页页头「删除」 | 原生 `Alert` 二次确认（destructive）；确认后提交站点「修改片单」页里的删除表单，成功后返回列表；列表页不放破坏性入口 |
| 女优筛选 | `BottomSheet` + 4 × `Picker` | 2×2 排布、内容自适应高度；排序走 `ChoiceMenuBar` 菜单 |
| 搜索 | `SearchBox` | Paper Searchbar，列表页与首页共用 |
| 内容语言 | 设置行 → `/language` 子页 | 7 种语言，当前项 accent 色；路径前缀由 api 层统一拼接，切换后列表换 key 重拉 |
| 自动播放 | 设置行 + 原生 `Switch` | 默认开；关闭后打开视频保持暂停，不影响当前播放中的视频 |
| 视频快进退 | 播放器下方 6 个按钮 | 10 秒 / 1 分钟 / 10 分钟双向，36dp 高，点击调用 `seekBy` 并显示提示 |
| 播放器控件 | 自建覆盖层 + 全屏 `Modal` | 播放/暂停、进度条单击/拖动、连击快进退（同方向，N 击 = N-1 次，提示分左右）；单击只显隐控件；控件 150ms 淡入淡出，seek 期间不切换播放图标；全屏锁横屏并隐藏状态栏 |
| 人机验证 | 全局 Modal + `WebView` | Cloudflare 挑战时显式验证；cf_clearance 与 RN fetch 共享，验证后自动重试原请求 |
| 反馈 | Snackbar + haptics | 收藏、登录、播放手势 |

NEVER 把多个 Compose `TextField` 塞进同一个 `Host` 并排——它们都是 `fillMaxWidth`，会互相挤扁。需要多控件时每个控件独立 `Host` 定宽。

## Design Tokens

- **Spacing**: 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 / 64，只用 `Spacing.*`
- **Radius**: 8（控件）/ 10 / 12（卡片）/ 9999（胶囊），只用 `Radius.*`
- **Color**: 深色底 `#000000`，卡片面 `#212225`，accent `#d85a76` 仅用于选中与强调
- **TabBarInset**: Android 80，滚动容器底部必须避开原生 tab 栏

## Known Limits

- Expo Go 内置的 ExpoUI 版本落后 npm 包：`SelectableDropdownMenuItem` 会白屏，菜单选中态用 `✓` 文案代替
- 同上原因，`ListItem` 的 `colors` 会抛 `Cannot set prop 'modifiers'` 红条，个人中心/设置改用 `SettingsRow` 自建行
- 不做 iOS / Web 适配：非 Android 平台走通用回退实现，不接受为其新增平台代码
- 频道分组是静态清单（`src/constants/channel-groups.ts`），站点改版需手工同步
- 内容语言只切换站点数据（路径前缀 `/cn`、`/en` …），App 界面文案仍为中文；「随机」板块在非中文语言下没有「更多」链接，可能不显示
- `ko / ms / th` 语言只在站点菜单中确认存在，Cloudflare 拦截导致尚未拿到 200 实测
- 人机验证走 WebView + 共享 Cookie；若 Cloudflare 进一步按 TLS 指纹拒绝，验证后仍可能 403（备选是让 WebView 代取 HTML，未做）
- 登录前必须清空 Cookie 罐：带旧会话/Cloudflare Cookie 时 `/api/login` 返回 401；清空后登录响应写入新会话
- 登录态失效只认「受保护页面被 302 回首页」：原生 `fetch` 看 `response.url`，WebView 看回传的最终地址。不能用「页面里有没有 `/logout`」判断——挑战页、502 页、精简布局的表单页都没有它，会把有效会话误判成失效并主动登出
- 站点所有页面都内嵌 `#login_email` 登录弹窗，因此也不能靠「有没有登录表单」判断登录态
- 片单列表/详情都是服务端直出的 HTML，原生 `fetchHtml` 即可（实测 ~0.8s，7 条影片）；WebView 只在原生取数失败时兜底。早前“原生只有空壳、必须 WebView”的结论不成立，已实测推翻
- 详情页「相关推荐」不在 HTML 里：由页面内 Vue 组件向 Recombee（`client-rapi-missav.recombee.com`，带 `frontend_sign`）请求后渲染，原生 HTML 只有空壳，因此未实现；要做就是新增第三方请求与签名逻辑，不是解析问题。信息块本身是服务端直出，可直接解析
- 首页顶部「推荐给你」与详情页「相关推荐」同源：站点用 Recombee 个性化接口（原生 HTML 只有占位骨架和 `x-for` 模板），未实现，首页用「今日热门」替代
- 从详情页跳列表必须用 `router.dismissTo`（弹出详情页）而不是 `push`/`replace`：详情页留在根栈里不卸载，播放器与 ExoPlayer 实例会一直挂着；播放器另有失焦暂停兜底
- 片单接口挂在根路径（`/api/playlists/...`），没有语言前缀；其它接口是 `/cn/api/...`。`fetchJson` 因此带 `rawPath` 开关，写错前缀会 404「route cn/api/playlists/... could not be found」
- 新建/删除片单没有 JSON 接口，是服务端直出的网页表单（新建页 `/playlists/create/{dvdId}`，删除表单在「修改片单」页 `/playlists/{id}/edit`，`_method=delete` + `_token`，POST 到片单自身 URL）。原生 `fetchHtml` 实测就能取到表单，WebView 只作回退；只有片单列表/详情这类客户端渲染的页面必须走 WebView
- 片单面板里行点击和 `Checkbox` 的 `onValueChange` 是同一次点击的两条路径，用同步 ref 去重，否则一次点击会切两次
- Snackbar 会被 Compose 浮层遮住：片单操作成功不提示（勾选框即反馈），失败文案直接显示在面板内
- 播放/暂停、全屏按钮必须用 RNGH `Gesture.Tap`，不能用 RN `Pressable`：双击后第一次点击会被手势竞技场吞掉
- 长按手势的 `onFinalize` 在普通点击失败时也会触发，只有真正激活过才能结束倍速/清提示
- 全屏 Modal 内要嵌套 `GestureHandlerRootView`；内联播放器保持挂载、Modal 共享同一 player；退出时重挂内联 `VideoView` 刷新 SurfaceView 并 seek 回原时间点，期间用 seeking 保护屏蔽暂停图标
- 退出全屏重挂 surface 会有约 300–400ms 画面停顿，是双视图 Modal 方案的已知取舍；根治需改为单常驻 `VideoView`，当前不做
- seek 期间 `playingChange` 的短暂 paused 要屏蔽，避免暂停图标闪烁；快进后主动显示控件
- `expo-router/unstable-native-tabs` 已废弃，待迁移到 `expo-router/native-tabs`

## Technical Stack

- Expo SDK 58 + React Native 0.88 + TypeScript
- Expo Router（NativeTabs + 每 tab 独立 Stack）
- `@expo/ui`（Compose 路径为主）；`react-native-paper` 仅 Searchbar、Snackbar、搜索历史标签
- `expo-video`、`expo-image`、Reanimated、Gesture Handler
- 深色优先，中文界面，仅 Android 交付
