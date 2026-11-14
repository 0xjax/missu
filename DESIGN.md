# Impeccable Design Context

> **实现目标已变更（2026-11）**：现役实现是 **Kotlin + Jetpack Compose**（仓库根；Gradle 根 = 仓库根，`legacy/expo/` 是归档的旧实现）。本文的设计原则、控件规格、已知限制继续有效；「组件框架」「Technical Stack」两节描述的是 legacy Expo 实现，只作参照与迁移输入。

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

> Expo 版那套自绘原语（`Sheet` / `Menu` / `Pill` / `UiButton` / `Checkbox` / `SearchField` / `Toast`）
> 已随归档实现移到 [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)；现役 native 版不再有这层原语，
> 直接用 Material 3 Expressive 组件（见下面的 Control Map）。

## Control Map

> 前半部分是现役 native 的实现；其余行描述的是归档 Expo 版（里面出现 `expo-router` / `FlatList` / `VideoCard` / `TabHeader`
> 这类 RN 组件的行就是 legacy 口径），两者冲突时**以 native 为准**。

| 场景 | 控件 | 规格 |
|---|---|---|
| 底部导航 | `expo-router/js-tabs` 的 `Tabs` | 纯 RN 栏，**3 项固定（首页/发现/我的）**，配色只来自 theme |
| 页头 | `TabHeader`（自绘） | 每个 tab 页自己画大标题，不用原生 Stack header |
| 首页板块 | 首页 HTML 板块 + 顶部「今日热门」 | 板块来自首页 HTML；「今日热门」额外请求 `/today-hot` 列表页置顶，条数与其他板块一致不裁剪，失败时整块不显示，不影响其他板块 |
| 个人中心（我的） | `ListItem` 分组容器 + 资料卡 | 资料卡 `extraLarge` 圆角 + `surfaceContainerHigh`，首字母头像取 `primary`；四个入口装在 `surfaceContainerLow`（`largeIncreased` 圆角）里，行间分隔线从图标位之后开始；「退出登录」单独一张容器并挂 `error` 色，破坏性动作不与普通入口同层 |
| 频道 / 周期 / 筛选 / 排序 | `ChoiceMenuBar` + `Menu` | 40dp 触发器 + 自绘浮层，选中项只改文字颜色 |
| 一级分类（发现页） | `PrimaryTabRow` | 三项等宽（女优/类型/厂商）；基线 `TabRow` 已废弃；发现页**不放搜索入口**（搜索的唯一入口是首页顶部的 `SearchBox`），搜索不单独占页签 |
| 厂商频道组 | `CollapsibleGroup` + `SettingsRow` | 默认折叠三组（素人/无码影片/亚洲 AV），路径见 `src/constants/channel-groups.ts` |
| 目录栅格 | `FlatList` + `DirectoryCard` | 女优 / 排行 3 列（低分辨率竖图），分类 / 厂商 2 列（文字块） |
| 视频卡封面角标 | `VideoCard` | 左下变体标签（中文字幕 / 无码影片），右下时长；标签取自卡片 HTML，不新增请求 |
| 长按预览 | `VideoCard` 长按 + 全屏 `Modal` | 350ms 长按（带 haptic）弹静音循环预览；预览片由封面地址推导为同目录 `preview.mp4`（原站卡片自带，零额外请求），加载失败保留封面；松手停留，点空白/返回键关闭，短按仍进详情 |
| 详情信息块 | 详情页内联行 | 8 行（发行日期/番号/标题/女优/类型/系列/发行商/标籤），文案随内容语言变化不硬编码；标签 `textSecondary` 与值同行，可点击值用 accent 色跳 `/latest?source=path`。不用下划线：Android 嵌套 `Text` 的下划线会错位成漂浮短线 |
| 详情页片单 | 收藏旁「片单」按钮 + `Sheet` | 打开时 `GET /api/playlists/{dvdId}`；点行或勾选框乐观切换再 `POST /api/playlists/add\|remove`；超过 6 行面板内滚动；「新建片单」就地展开单行输入框，提交后重新拉列表；未登录先跳登录 |
| 删除片单 | 片单详情页页头「编辑」→ 编辑页内「删除片单」 | 系统 `Alert` 二次确认（destructive）；确认后提交站点「修改片单」页里的删除表单（`_method=delete` + `_token`），成功后返回列表；列表页不放破坏性入口（滑动露出删除这类方案同理不采用）。改名/描述/公开与删除同在这一个编辑页，站点上它们本就是同一张表单 |
| 二级页顶栏 | `TopAppBar` + `PageTopBar` | up 图标 + 标题 + 动作槽；返回图标挂 `PlainTooltip`；容器色取 `background`（默认 surface 在纯黑底上会露灰边）；`onBack` 可空——「我的」这类一级页签只借它放标题，不伪造返回键 |
| 底部导航 | `ShortNavigationBar` | 3 个目的地，用 `ShortNavigationBarItem`；指示器是药丸形。`NavigationBar` 属 M3 基线，M3E 目标下不再用 |
| 首页推荐位 | `HorizontalMultiBrowseCarousel` | 只给第一个板块做轮播（大项 + 中项 + 小项随滚动变形吸附，这是 M3E carousel 的核心行为，别关）；其余板块保持网格，全轮播会让首页变横向长龙 |
| 下拉刷新 | `PullToRefreshBox` | 首页与发现页（发现页只包住有内容的支）；刷新时内容留在原地、只有顶部指示器转；失败保留旧列表 |
| 排序切换 | `ToggleButton` 连通组 | 女优排序（影片 / 出道）；首尾 `connected*ButtonShapes()` 咬合，语义 `Role.RadioButton`；`SegmentedButton` 已退役 |
| 详情页动作条 | `FlexibleBottomAppBar` + `SplitButtonLayout` | 左半是主操作「收藏 / 已收藏」，右半「+」打开加入片单面板；动作常驻底部，不再插在正文中间 |
| 搜索栏 | M3E `SearchBar`（slot + `SearchBarState`） | 折叠态在页面顶部，点输入框自动展开成全屏 `ExpandedFullScreenSearchBar`；展开态里是最近搜索胶囊；提交后自动收起到结果列表 |
| 我的片单操作 | `FloatingActionButtonMenu` | 右下 FAB 展开「新建片单 / 刷新列表」；FAB 与菜单项容器色显式取 `primary`（默认 `primaryContainer` 是紫色，与粉色 accent 不搭）；主操作不再占顶栏动作位 |
| 片单内移除影片 | 右下 `ToggleFloatingActionButton` + `HorizontalFloatingToolbar` | 未整理时只有一个「整理」FAB（编辑图标，点开 morph）；整理模式里点卡片是选中/取消（不进详情），浮条里是连体 `ButtonGroup`「全选 / 移出 N 部」，少见动作「取消选择 / 完成」放进 `AppBarRow` 溢出容器；二次确认后逐条 `DELETE /api/playlists/{片单id}/{影片id}`，成功只删本地这几条（删空转空态） |
| 页面三态 | `LoadingIndicator` / `EmptyHint` / `ErrorHint` | 统一在 `ui/components/PageStates.kt`：空态 = 降对比度图标 + 标题 + 说明 + 可选 `FilledTonalButton`；错误态必须可重试（M3E 要求），不做「错误但没有出路」的页面 |
| 分页加载更多 | `LoadMoreFooter` | 列表页与收藏页共用；加载中把按钮换成 `LoadingIndicator`，按钮留在原地会让列表底部高度跳变 |
| 登录页 | 整宽主行动 + 内联错误 | 「登录」是整宽 `Button`，加载中按钮内换成 `onPrimary` 色的 `LoadingIndicator`（默认 `primary` 压在粉色按钮上看不见）；账号密码错误写在字段下方，不弹 dialog |
| 女优筛选 | `Sheet` + 手风琴 + `Pill` | 4 个维度各占一行，行内显示当前值，点行展开该维度的胶囊网格；选中即生效并收起。出道年份有 26 个选项，四列同时铺开会撑爆面板 |
| 搜索 | `SearchBox` + `SearchField` | 列表页与首页共用；聚焦时展示搜索历史胶囊 |
| 内容语言 | 设置页内单选分组 | 7 种语言；整行 `selectable(role = RadioButton)`，行尾 `RadioButton(onClick = null)`（整行已可点，控件再接点击会让读屏看到两个节点）；选中态由控件表达，不再给整行刷硬编码底色；路径前缀由 api 层统一拼接，切换后列表换 key 重拉 |
| 外观设置 | 设置页「外观」分组 | `跟随系统 / 浅色 / 深色` 三选一（`ThemeMode` 存进 SharedPreferences）+「动态取色」开关（Android 12+，默认关）；改完立刻重组整棵树，不需要重启 |
| 自动播放 | 设置行 + 系统 `Switch` | 默认开；关闭后打开视频保持暂停，不影响当前播放中的视频 |
| 视频快进退 | 播放器下方 6 个按钮 | 10 秒 / 1 分钟 / 10 分钟双向，36dp 高，点击调用 `seekBy` 并显示提示；按下态用 accent 填充 + 白字，比降透明度更明显 |
| 倍速设置 | 底部条「倍速」按钮 + `Sheet` | 播放倍速 0.75–2x，选中的存进偏好并对所有视频生效；长按不再临时加速，定位靠 6 个 seek 按钮与双击连击 |
| 播放器控件 | 自建覆盖层 + 全屏 `Modal` | 播放/暂停常驻底部条左侧（快进快捷键会唤出控件，居中大按钮会遮挡画面）；进度条单击/拖动、连击快进退（同方向，N 击 = N-1 次）；单击只显隐控件；快进退用居中圆角提示，700ms 后消失；控件 150ms 淡入淡出，seek 期间不切换播放图标；全屏锁横屏并隐藏状态栏 |
| 人机验证 | 全局 Modal + `WebView` | Cloudflare 挑战时显式验证；cf_clearance 与 RN fetch 共享，验证后自动重试原请求 |
| 反馈 | `Toast` + haptics | 收藏、登录、播放手势 |

## Design Tokens

- **圆角**：只用 `MaterialTheme.shapes`（M3E 八档）。封面/缩略图 `medium`(12dp)、分组容器 `largeIncreased`(20dp)、资料卡 `extraLarge`(28dp)、角标 `extraSmall`(4dp)
- **间距**：4 / 8 / 12 / 16 / 20 / 24 / 32；页面左右留白固定 16dp，分组之间 8–12dp
- **色板**：整套由品牌粉 `#D85A76` 推导——固定色相，按 M3 的 tone（`L*`）取各角色，超出 sRGB 色域时降饱和度：
  - 深色（主体验）`primary` 直接钉品牌色本身，白字压它 **3.7:1**：够图形/大字的 3:1，不够正文级 4.5:1，所以品牌粉只做图形强调（FAB、选中态、进度条），不做小字正文底色
  - 浅色 `primary` 用 tone 40 的深一档品牌粉 `#AA2F51`，白字 **6.5:1**
  - 五档 `surfaceContainer*` 必须显式给：只给 `surface` 时官方推导出的容器色会和底色几乎重合，「分组容器 / 卡片」的层级会整个丢掉（只剩分隔线）
  - `secondary` / `tertiary` / `error` / `outline` / `inverse*` 同源推导；所有 on/容器 配对实测对比度 ≥ 5.49:1
- **深色底的取值**：默认用 M3 的 neutral **tone 6**（`#191112`），**不是纯黑**。纯黑是 MD2 起就不推荐的深色底：OLED 上黑底与亮像素相邻会拖影，且纯黑之上留不出「更亮的容器」这一档。实测标准深色：底 `#191112` / 分组容器 `#251E1F`（差 12 级）；纯黑模式：底 `#000000` / 容器 `#1D1D21`
- **纯黑背景**：设置项、**默认关**（仅深色生效）。给想省电的人用，切过去时容器梯度整体压暗，避免容器和底几乎同色
- **明暗模式**：默认跟随系统，设置页可强制浅色/深色；状态栏与导航栏图标按**实际生效**的主题再设一次（`enableEdgeToEdge` 只跟系统走，强制浅色时会白图标压白底）
- **动态取色**：Android 12+ 可用、**默认关**——开了品牌粉会被壁纸色板替换，识别度就没了；关掉时永远用品牌色板

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

**现役（Kotlin 原生，仓库根）**

- Kotlin 2.4.21 + Jetpack Compose（BOM **alpha 2026.11.00** → material3 1.5.0-rc01）+ Material 3 **Expressive**；AGP 9.5.1 / Gradle 9.6.0
- 单 Activity + Navigation Compose；界面一律自己写 Compose，不引第三方 UI 组件库
- 数据层：OkHttp + Jsoup（对应 legacy 的 `fetch` + `node-html-parser`）
- 存储：目前是 `SharedPreferences`（凭据再用 Android Keystore 里的密钥做 AES-GCM 加密）；迁 DataStore / Room 只在代码 TODO 里，尚未发生
- 媒体：Media3 ExoPlayer；图片 Coil；网页/人机验证：系统 `WebView` + `CookieManager`

**legacy（Expo，仅参照）**

- Expo SDK 58 + React Native 0.88 + TypeScript
- 路由 `expo-router`；UI 是 `legacy/expo/src/components/ui/` 的七个自绘原语 + Reanimated + Gesture Handler（见 `legacy/expo/DESIGN-legacy.md`）
- 数据 `legacy/expo/src/api/*`；存储 `expo-sqlite/kv-store`；媒体 `expo-video` / `expo-image` / `react-native-webview`
- 不再构建、不再运行；`node_modules` 已删除，需要时 `bun install`
