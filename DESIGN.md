# Impeccable Design Context

> **实现目标已变更（2026-11）**：现役实现是 **Kotlin + Jetpack Compose**（`android-native/`）。本文的设计原则、控件规格、已知限制继续有效；「组件框架」「Technical Stack」两节描述的是 legacy Expo 实现，只作参照与迁移输入。

## Users

**Who**: 中文用户，Android 手机竖屏，单手操作，多在夜间或弱光环境使用。

**Platform**: 仅 Android。App 只交付 Android 一个平台，因此不存在平台回退实现（`*.ios.* / *.web.*` 已删除，`*.android.*` 也已并入普通文件）。

**Context**: 目标明确——找到片子、确认信息、直接播放。单次会话短，进出频繁；不是生产力工具，没有长表单、没有多任务并行。

**Jobs to be done**:
1. 从首页或发现页快速找到想看的片子
2. 用筛选、排序把长列表收敛到可决策的范围
3. 搜索具体番号、女优、关键词
4. 看完回来接着看（历史、收藏）

## Brand Personality

**3 words**: 直接 · 克制 · 顺手

- **直接**: 一屏说清楚这是什么、点下去会发生什么。不做悬念式布局，不藏主要操作。
- **克制**: 界面让位于封面内容。控件数量与视觉重量都要压到最低，凑合能用的一律不加。
- **顺手**: 用系统手势、系统尺寸、系统字体缩放。控件是我们画的，但手感按 Material 的规范来。

**Emotional goals**: 用户不应该注意到界面——注意力在封面和播放器上，操作一次到位，没有「这个控件怎么这么大」的瞬间。

## Aesthetic Direction

**Style**: 内容优先的 Material 3 密度。黑底、两列封面、极简工具栏；控件只在需要时展开。

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

1. **自绘优先** — 界面 UI 全部用 RN 视图 + Reanimated 画（`src/components/ui/`），实测参数、可改可关。原生只留给系统能力：Stack 转场与返回手势、播放管线（`expo-video`）、WebView、通知、权限、键盘、`Switch`/`Alert` 这类系统对话框。
   - 反面案例：`@expo/ui` 的 Compose 控件（旧版曾用 DropdownMenu / BottomSheet / Picker / ToggleButton / Checkbox），托盘、波纹、选中图标、弹层高度都在 Material 内部，`seedColor` 只能近似配色。SDK 58 早期 Expo Go 内置版本落后于 npm，曾两次把 dev 环境搞崩（白屏、`Cannot set prop 'modifiers'` 红条）；**当前 `bundledNativeModules.json` 与 npm 都是 `~58.0.28`，版本错配风险已消除**——不用它的理由仍是观感与可配置性，不是稳定性。已全部移除。
   - 反面案例：`react-native-paper` 整个 Material 库只为 5 个控件存在（Snackbar / Chip / Searchbar / Menu），主题还得再对一遍。已全部移除。
2. **同维度单一控件** — 一个维度只允许一种控件形态；频道、周期、筛选、排序各自最多占一个按钮。
3. **尺寸守规范** — 控件高 40dp（分段/菜单）、页面左右留白 16dp；验收时用 `uiautomator dump` 量真实 bounds，不靠肉眼。
4. **选中态轻量表达** — 触发器只显示当前选项值（如 `全部`、`发行日期`），维度由按钮位置表达，不加「筛选 / 排序」前缀；菜单里的选中项只改文字颜色，不插入 `✓` 之类的字符前缀（会撑动布局、行宽跳动）。
5. **状态一致** — 加载、空、错误、重试在首页/列表/发现/详情用同一套表达，错误必须可重试。

## 组件框架（`src/components/ui/`）

界面只允许用这些原语拼，新控件先加到这里，不要在页面里就地发明：

| 原语 | 文件 | 用途 | 取代 |
|---|---|---|---|
| `Sheet` | `ui/sheet.tsx` | 底部弹层：遮罩淡入 + 卡片上滑，`onRequestClose` 接返回键 | Compose `BottomSheet`、Paper `Modal` |
| `Menu` | `ui/menu.tsx` | 工具栏下拉：触发器 + 现测坐标的浮层，下方放不开自动翻到上方 | Paper `Menu`、Compose `DropdownMenu` |
| `Pill` | `ui/pill.tsx` | 胶囊按钮（选中只填充色） | Paper `Chip`、Compose `ToggleButton` |
| `UiButton` | `ui/button.tsx` | 40dp 按钮，`text` 次要 / `filled` 主操作 | Paper `Button`、Compose `Button`/`TextButton` |
| `Checkbox` | `ui/checkbox.tsx` | 22dp 勾选框 | Compose `Checkbox` |
| `SearchField` | `ui/search-field.tsx` | 搜索输入框（放大镜 + 清空） | Paper `Searchbar` |
| `Toast` | `ui/toast.tsx` | 底部悬浮提示，自动消失 | Paper `Snackbar` |

## Control Map

| 场景 | 控件 | 规格 |
|---|---|---|
| 底部导航 | `expo-router/js-tabs` 的 `Tabs` | 纯 RN 栏，4 项固定（首页/列表/发现/我的），配色只来自 theme |
| 页头 | `TabHeader`（自绘） | 每个 tab 页自己画大标题，不用原生 Stack header |
| 首页板块 | 首页 HTML 板块 + 顶部「今日热门」 | 板块来自首页 HTML；「今日热门」额外请求 `/today-hot` 列表页置顶，条数与其他板块一致不裁剪，失败时整块不显示，不影响其他板块 |
| 个人中心 | `SettingsRow` | 56dp 行、透明容器、右侧 chevron；二级页走根 Stack |
| 频道 / 周期 / 筛选 / 排序 | `ChoiceMenuBar` + `Menu` | 40dp 触发器 + 自绘浮层，选中项只改文字颜色 |
| 一级分类（发现页） | `DiscoverTabs` + `Pill` | 单行等宽胶囊（热门/分类/女优/厂商/排行），选中只填充 accent |
| 厂商频道组 | `CollapsibleGroup` + `SettingsRow` | 默认折叠三组（素人/无码影片/亚洲 AV），路径见 `src/constants/channel-groups.ts` |
| 目录栅格 | `FlatList` + `DirectoryCard` | 女优 / 排行 3 列（低分辨率竖图），分类 / 厂商 2 列（文字块） |
| 视频卡封面角标 | `VideoCard` | 左下变体标签（中文字幕 / 无码影片），右下时长；标签取自卡片 HTML，不新增请求 |
| 长按预览 | `VideoCard` 长按 + 全屏 `Modal` | 350ms 长按（带 haptic）弹静音循环预览；预览片由封面地址推导为同目录 `preview.mp4`（原站卡片自带，零额外请求），加载失败保留封面；松手停留，点空白/返回键关闭，短按仍进详情 |
| 详情信息块 | 详情页内联行 | 8 行（发行日期/番号/标题/女优/类型/系列/发行商/标籤），文案随内容语言变化不硬编码；标签 `textSecondary` 与值同行，可点击值用 accent 色跳 `/latest?source=path`。不用下划线：Android 嵌套 `Text` 的下划线会错位成漂浮短线 |
| 详情页片单 | 收藏旁「片单」按钮 + `Sheet` | 打开时 `GET /api/playlists/{dvdId}`；点行或勾选框乐观切换再 `POST /api/playlists/add\|remove`；超过 6 行面板内滚动；「新建片单」就地展开单行输入框，提交后重新拉列表；未登录先跳登录 |
| 删除片单 | 片单详情页页头「编辑」→ 编辑页内「删除片单」 | 系统 `Alert` 二次确认（destructive）；确认后提交站点「修改片单」页里的删除表单（`_method=delete` + `_token`），成功后返回列表；列表页不放破坏性入口（滑动露出删除这类方案同理不采用）。改名/描述/公开与删除同在这一个编辑页，站点上它们本就是同一张表单 |
| 女优筛选 | `Sheet` + 手风琴 + `Pill` | 4 个维度各占一行，行内显示当前值，点行展开该维度的胶囊网格；选中即生效并收起。出道年份有 26 个选项，四列同时铺开会撑爆面板 |
| 搜索 | `SearchBox` + `SearchField` | 列表页与首页共用；聚焦时展示搜索历史胶囊 |
| 内容语言 | 设置行 → `/language` 子页 | 7 种语言，当前项 accent 色；路径前缀由 api 层统一拼接，切换后列表换 key 重拉 |
| 自动播放 | 设置行 + 系统 `Switch` | 默认开；关闭后打开视频保持暂停，不影响当前播放中的视频 |
| 视频快进退 | 播放器下方 6 个按钮 | 10 秒 / 1 分钟 / 10 分钟双向，36dp 高，点击调用 `seekBy` 并显示提示；按下态用 accent 填充 + 白字，比降透明度更明显 |
| 倍速设置 | 底部条「倍速」按钮 + `Sheet` | 播放倍速 0.75–2x，选中的存进偏好并对所有视频生效；长按不再临时加速，定位靠 6 个 seek 按钮与双击连击 |
| 播放器控件 | 自建覆盖层 + 全屏 `Modal` | 播放/暂停常驻底部条左侧（快进快捷键会唤出控件，居中大按钮会遮挡画面）；进度条单击/拖动、连击快进退（同方向，N 击 = N-1 次）；单击只显隐控件；快进退用居中圆角提示，700ms 后消失；控件 150ms 淡入淡出，seek 期间不切换播放图标；全屏锁横屏并隐藏状态栏 |
| 人机验证 | 全局 Modal + `WebView` | Cloudflare 挑战时显式验证；cf_clearance 与 RN fetch 共享，验证后自动重试原请求 |
| 反馈 | `Toast` + haptics | 收藏、登录、播放手势 |

## Design Tokens

- **Spacing**: 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 / 64，只用 `Spacing.*`
- **Radius**: 8（控件）/ 10 / 12（卡片）/ 9999（胶囊），只用 `Radius.*`
- **Color**: 深色底 `#000000`，卡片面 `#212225`，accent `#d85a76` 仅用于选中与强调

## Known Limits

- `Sheet` / `Menu` 都是 RN `Modal`：遮罩会盖住底部 tab 栏，这是 Modal 的正常行为，弹层内不要再依赖 tab 栏可见
- `Menu` 的浮层坐标是按下那一刻 `measureInWindow` 测的：浮层打开后不会跟随列表滚动（Modal 已经挡住滚动），因此不要在会自己动的容器里当锚点
- 女优筛选从「4 个原生 Picker」改成「手风琴 + 胶囊」：滚轮/下拉在 26 个年份选项上比胶囊网格慢，改动是取舍不是退步
- 不做 iOS / Web 适配：这两个平台的依赖（`react-native-web`、`react-dom`）与回退实现已删除，恢复需要重新引入并重测
- 存储只有一套（`expo-sqlite/kv-store`）：早前写在 AsyncStorage 里的历史 / 搜索记录 / 登录资料不再迁移，从那些版本升上来会丢这批本地数据；要恢复只能重新引入 AsyncStorage 与迁移代码
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
- `Toast` 渲染在 `Sheet` 之外时会被 Modal 遮罩盖住：片单面板的操作失败文案直接写在面板里，不用 Toast
- 播放/暂停、全屏、倍速按钮必须用 RNGH `Gesture.Tap`，不能用 RN `Pressable`：双击后第一次点击会被手势竞技场吞掉
- 视频画面只挂一个 `Tap` 手势：长按加速曾与它组成 `Simultaneous` 组合，`onFinalize` 受渲染期闭包影响会漏掉、倍速卡住，已整体移除
- 全屏 Modal 内要嵌套 `GestureHandlerRootView`；内联播放器保持挂载、Modal 共享同一 player；退出时重挂内联 `VideoView` 刷新 SurfaceView 并 seek 回原时间点，期间用 seeking 保护屏蔽暂停图标
- 退出全屏重挂 surface 会有约 300–400ms 画面停顿，是双视图 Modal 方案的已知取舍；根治需改为单常驻 `VideoView`，当前不做
- seek 期间 `playingChange` 的短暂 paused 要屏蔽，避免暂停图标闪烁；快进后主动显示控件

## Technical Stack

**现役（Kotlin 原生，`android-native/`）**

- Kotlin 2.4.21 + Jetpack Compose（BOM 2026.09.00）+ Material 3；AGP 9.5.1 / Gradle 9.6.0
- 单 Activity + Navigation Compose；界面一律自己写 Compose，不引第三方 UI 组件库
- 数据层：OkHttp + Jsoup（对应 legacy 的 `fetch` + `node-html-parser`）
- 存储：DataStore（对应 legacy 的 `expo-sqlite/kv-store`）
- 媒体：Media3 ExoPlayer；图片 Coil；网页/人机验证：系统 `WebView` + `CookieManager`

**legacy（Expo，仅参照）**

- Expo SDK 58 + React Native 0.88 + TypeScript
- 路由 `expo-router`；UI 是 `src/components/ui/` 的七个自绘原语 + Reanimated + Gesture Handler
- 数据 `src/api/*`；存储 `expo-sqlite/kv-store`；媒体 `expo-video` / `expo-image` / `react-native-webview`
- 不再构建、不再运行；`node_modules` 已删除，需要时 `bun install`
