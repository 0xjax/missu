# Impeccable Design Context

> **实现目标已变更（2026-11）**：现役实现是 **Kotlin + Jetpack Compose**（仓库根；Gradle 根 = 仓库根）。本文只记录现役口径；
> 已归档的 Expo 版（控件口径、已知限制、技术栈）在 [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)。

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
- Material 3 Expressive 的控件尺码：FAB 重新基线到 56dp（`SmallFloatingActionButton` 的 40dp 已退役）、按钮内边距 16dp（24dp 已过时）
- 原生媒体类应用的信息密度：一屏 6 张卡，滚动前不铺三层控件

**Anti-references**（绝对不要）:
- Chip 墙：同一维度并排多组 Chip，视觉重量互相抵消
- 表单控件当工具栏：`ExposedDropdownMenuBox`(56dp) / 满宽等分输入框
- 整块高饱和色表达选中态
- 霓虹色、游戏化徽章、装饰性动效、骨架屏之上的骨架屏

## Design Principles

1. **自绘优先** — 界面 UI 全部用 Compose 自己画，实测参数、可改可关；Material 3E 组件直接用官方 `androidx.compose.material3`，但**不引第三方 UI 组件库**——行为封在内部就只能靠猜默认值调参。系统能力才交给平台：返回手势、播放管线（Media3）、WebView、通知、权限、键盘。
   - 反面案例的完整记录在 [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)：`@expo/ui` 的 Compose 控件不可配（托盘、波纹、弹层高度都在 Material 内部），`react-native-paper` 整个 Material 库只为 5 个控件存在、主题还得再对一遍。两条都已从代码里删掉，结论对 native 同样成立。
2. **同维度单一控件** — 一个维度只允许一种控件形态；频道、周期、筛选、排序各自最多占一个按钮。
3. **尺寸守规范** — 控件尺寸一律用 MD3E 组件的默认尺码（按钮基线 40dp、FAB 56dp），不再自定高度；页面左右留白 16dp；验收时用 `uiautomator dump` 量真实 bounds，不靠肉眼。
4. **选中态轻量表达** — 触发器只显示当前选项值（如 `全部`、`发行日期`），维度由按钮位置表达，不加「筛选 / 排序」前缀；菜单里的选中项只改文字颜色，不插入 `✓` 之类的字符前缀（会撑动布局、行宽跳动）。
5. **状态一致** — 加载、空、错误、重试在首页/列表/发现/详情用同一套表达，错误必须可重试。

> 现役 native 版没有自绘原语层，直接用 Material 3 Expressive 组件（见下面的 Control Map）；Expo 版那层原语（`Sheet` / `Menu` / `Pill` / `Toast` …）见
> [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)。

## Control Map

> 表里的每一行都是**现役 native 实现**。Expo 版当年怎么做的、以及 native 没实现的几项（长按预览、频道/周期菜单、自动播放开关、Toast 反馈），
> 都在 [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)——那是归档，不是待办。

| 场景 | 控件 | 规格 |
|---|---|---|
| 底部导航 | `ShortNavigationBar` | 3 个目的地，用 `ShortNavigationBarItem`；指示器是药丸形。`NavigationBar` 属 M3 基线，M3E 目标下不再用 |
| 首页板块 | 板块网格（每板块**上限 6 条**）+ 标题行「更多」 | 站点每个板块直出 16 条上下，全铺出来首页实测 64 张卡 = 32 行（约 6000dp）；现在首页只当预览，非轮播板块 `take(6)`（2 列 × 3 行），标题行右侧「更多」→ 复用已有 `/list` 分页页。轮播板块（第一个）不受限，它高度固定 |
| 个人中心（我的） | `ListItem` 分组容器 + 资料卡 | 资料卡 `extraLarge` 圆角 + `surfaceContainerHigh`，首字母头像取 `primary`；四个入口装在 `surfaceContainerLow`（`largeIncreased` 圆角）里，行间分隔线从图标位之后开始；「退出登录」单独一张容器并挂 `error` 色，破坏性动作不与普通入口同层 |
| 一级分类（发现页） | `PrimaryTabRow` | 三项等宽（女优/类型/厂商）；基线 `TabRow` 已废弃；发现页**不放搜索入口**（搜索的唯一入口是首页顶部的 `SearchBar`），搜索不单独占页签 |
| 目录栅格（发现页） | `LazyVerticalGrid` | 女优双列卡片（48dp 圆角头像 + 名字 + 条数 + 出道两行）；类型/厂商三列卡片（`surfaceContainer` 圆角面，名称两行 + 条数压底）。单列时右侧有约 160dp 空白，双/三列后一屏条目数从 9.5 提到 16–24 |
| 无头像占位 | `MaterialShapes.Cookie9Sided` + 三档容器色 | 站点上刚出道的新人常没有头像图。占位不做「灰圈 + 中性首字」（读起来像加载失败），改成 M3E 多边形 + 首字，颜色按条目 id 哈希在 primary/secondary/tertiary 容器里取一档：同一人每次都一样（不闪），不同条目之间有细微差异。形状固定、只变颜色，避免整列花掉 |
| 视频卡封面角标 | `VideoGridCard` | 左下变体标签（中文字幕 / 无码影片），右下时长；标签取自卡片 HTML，不新增请求 |
| 详情信息块 | 详情页内联行 | 8 行（发行日期/番号/标题/女优/类型/系列/发行商/标籤），文案随内容语言变化不硬编码；标签 `onSurfaceVariant` 与值同行，可点击值用 accent 色跳 `/latest?source=path`。不用下划线：Android 嵌套 `Text` 的下划线会错位成漂浮短线 |
| 详情页片单 | 收藏旁「片单」按钮 + `ModalBottomSheet` | 打开时 `GET /api/playlists/{dvdId}`；点行或勾选框乐观切换再 `POST /api/playlists/add\|remove`；超过 6 行面板内滚动；「新建片单」就地展开单行输入框，提交后重新拉列表；未登录先跳登录 |
| 删除片单 | 片单详情页页头「编辑」→ 编辑页内「删除片单」 | `AlertDialog` 二次确认（destructive）；确认后提交站点「修改片单」页里的删除表单（`_method=delete` + `_token`），成功后返回列表；列表页不放破坏性入口（滑动露出删除这类方案同理不采用）。改名/描述/公开与删除同在这一个编辑页，站点上它们本就是同一张表单 |
| 二级页顶栏 | `TopAppBar` + `PageTopBar` | up 图标 + 标题 + 动作槽；返回图标挂 `PlainTooltip`；容器色取 `background`（默认 surface 在纯黑底上会露灰边）；`onBack` 可空——「我的」这类一级页签只借它放标题，不伪造返回键 |
| 首页推荐位 | `HorizontalMultiBrowseCarousel` | 只给第一个板块做轮播（大项 + 中项 + 小项随滚动变形吸附，这是 M3E carousel 的核心行为，别关）；其余板块保持网格，全轮播会让首页变横向长龙 |
| 下拉刷新 | `PullToRefreshBox` | 首页与发现页（发现页只包住有内容的支）；刷新时内容留在原地、只有顶部指示器转；失败保留旧列表 |
| 排序切换 | `ToggleButton` 连通组 | 女优排序（影片 / 出道）；首尾 `connected*ButtonShapes()` 咬合，语义 `Role.RadioButton`；`SegmentedButton` 已退役 |
| 详情页动作条 | `FlexibleBottomAppBar` + `SplitButtonLayout` | 左半是主操作「收藏 / 已收藏」，右半「+」打开加入片单面板；动作常驻底部，不再插在正文中间 |
| 搜索栏 | M3E `SearchBar`（slot + `SearchBarState`），**就地展开** | 首页顶部直接是这条真 `SearchBar`（56dp 胶囊 + 搜索图标），点一下在当前页展开成全屏 `ExpandedFullScreenSearchBar`，提交后同一屏收成带返回箭头的折叠态显示结果——搜索不再是独立路由，没有页面切换、没有键盘重启、输入框不会换样式 |
| 我的片单操作 | `FloatingActionButtonMenu` | 右下 FAB 展开「新建片单 / 刷新列表」；FAB 与菜单项容器色显式取 `primary`（默认 `primaryContainer` 是紫色，与粉色 accent 不搭）；主操作不再占顶栏动作位 |
| 片单内移除影片 | 右下 `ToggleFloatingActionButton` + `HorizontalFloatingToolbar` | 未整理时只有一个「整理」FAB（编辑图标，点开 morph）；整理模式里点卡片是选中/取消（不进详情），浮条里是连体 `ButtonGroup`「全选 / 移出 N 部」，少见动作「取消选择 / 完成」放进 `AppBarRow` 溢出容器；二次确认后逐条 `DELETE /api/playlists/{片单id}/{影片id}`，成功只删本地这几条（删空转空态） |
| 页面三态 | `LoadingIndicator` / `EmptyHint` / `ErrorHint` | 统一在 `ui/components/PageStates.kt`：空态 = 降对比度图标 + 标题 + 说明 + 可选 `FilledTonalButton`；错误态必须可重试（M3E 要求），不做「错误但没有出路」的页面 |
| 状态之间的交接 | `PageStateTransition`（fade through） | 转圈 → 内容不再是硬切：旧内容 90ms 淡出，新内容延迟 90ms 再 210ms 淡入并轻微放大（0.96→1），两段不重叠所以没有重影。`contentKey` 用状态类型，分页时 Content→Content 不重放动画。**详情页的正文与底部动作条也走这一套**——详情请求常在整页淡入窗口（90–300ms）之后才返回，不兜就是一次硬切 |
| 导航转场 | 整幅横滑（**1:1 位移，全程不透明**） | 页与页像一张长纸前后推：进场页从一侧滑入、退场页向另一侧滑出，两页始终不透明，所以不存在叠加、也不暴露窗口底色（alpha 过渡在整屏内容上必然出问题：交叉淡入=文字重影，顺序淡出=中间整屏空底色=「闪一下」）。方向按底栏顺序判断，推进入口（详情/列表）为右进左出，返回反向。300ms。**涉及详情页的交接不走横滑**，见下一行 |
| 卡片 → 详情 | container transform：封面自己长成播放器框 | 卡片封面和详情页顶部的播放器框是同一个容器，所以不能让页面横滑、也不能纯淡入——让那个容器从卡片位置/尺寸长到页首 16:9，页面本身用 fade through 交接（旧页 90ms 淡出、新页延迟 90ms 用 210ms 淡入）。播放器框在**首帧就存在**：解析播放地址的那几百毫秒里铺的是卡片封面，所以容器长到位时画面不会变。进入与返回都走这一套（返回是容器缩回卡片）；**返回时容器内容换回封面**、音频在转场第一帧就停——反向的落点必须是卡片上那张封面，不能是一帧活视频 |
| 静帧 → 画面 | 封面留到视频第一帧：先虚焦退场，再 200ms 显影 | 容器长大到位的那一瞬间视频通常还没出画。所以封面**一直铺到 `onRenderedFirstFrame`**（中途容器里不会空、也不会先冒出控件）。落位 300ms 后开始**虚焦 12dp + 压暗 22%**（1.5s 渐变——按典型视频起步延迟 2–3s 定，不是普通状态切换的 300ms，否则会"一闪就完成然后一直锁死"），虚化后**保持**到出画为止：削掉封面的"认知抓力"，让唯一的主角是还没出现的视频；出画时封面 200ms 淡出=画面在下面显影，控件同时出现、3 秒后自动隐藏。等待中的指示器**延迟 600ms** 才出现（秒开时不该闪一下），且用 `CircularWavyProgressIndicator`（波浪环）而不是页面级的 `LoadingIndicator`（形变团块）——「媒体流在续」和「界面在取数」是两种等待，形状家族不同才分得清。播放失败时立即撤封面，让错误层露出来 |
| 语言设置 | **一个设置项**（界面 + 内容同轴） | 语言 = 界面语言 + 站点内容语言，不是两个开关：详情页的信息行标签（发行日期 / Release date / 発売日）是站点按内容语言返回的、我们只透传，两个轴一分开就会出现「中文界面 + 英文标签」的混排。选项 = **有翻译的**语言（跟随系统 / 简体中文 / 繁體中文 / English / 日本語）——内容是路径前缀、界面是一份翻译，列出后者做不到的语言等于承诺一个错配。跟随系统时：界面回退英文，内容按系统语言（站点支持就跟）。切换当场生效，不重建 Activity |
| 错误与提示 | 状态存 `UiMessage`（类型 + 参数），渲染时才取文案 | 以前 10 个 ViewModel 各有一份 `describe(error)`，把中文文案塞进状态（`Error(message: String)`）——界面语言一变，屏幕上留着的还是旧语言。现在数据层只给类型（`UiMessage.Network / Http(status) / …`），`@Composable fun UiMessage.text()` 按当前资源取字符串 |
| 页签状态保持 | 页签级 ViewModel 提到 Activity 作用域 | 放在目的地里的话，切走页签会销毁目的地、连同 ViewModel 一起清掉，切回来就是「新建 VM → Loading → 重新请求」：用户看到先一帧旧画面再一个转圈。提到外层后，切页签只是视图切换，内容原地保留；要新内容走下拉刷新 |
| 分页加载更多 | `LoadMoreFooter` | 列表页与收藏页共用；加载中把按钮换成 `LoadingIndicator`，按钮留在原地会让列表底部高度跳变 |
| 登录页 | 整宽主行动 + 内联错误 | 「登录」是整宽 `Button`，加载中按钮内换成 `onPrimary` 色的 `LoadingIndicator`（默认 `primary` 压在粉色按钮上看不见）；账号密码错误写在字段下方，不弹 dialog |
| 女优筛选 | `ModalBottomSheet` + 分组 `FilterChip` 网格 | 四组（身高 / 罩杯 / 年龄 / 出道年份）在同一个 `LazyColumn` 里分组列出：每组一个 `labelLarge` 标题 + `FlowRow` 铺开的胶囊；再点一次同一项 = 取消该组。面板高度锁在 520dp 内滚动——出道年份有 26 个选项，四组全铺开会撑爆。不用手风琴：展开/收起多一次点击，且一次只会用到一个维度 |
| 内容语言 | 设置页内单选分组 | **与上文「语言设置」是同一个设置项**，不单列语言清单；整行 `selectable(role = RadioButton)`，行尾 `RadioButton(onClick = null)`（整行已可点，控件再接点击会让读屏看到两个节点）；选中态由控件表达，不再给整行刷硬编码底色 |
| 外观设置 | 设置页「外观」分组 | `跟随系统 / 浅色 / 深色` 三选一（`ThemeMode` 存进 SharedPreferences）+「主题色」一排六块（跟随壁纸 / 品牌粉 / 紫 / 蓝 / 青绿 / 琥珀，`ColorSource`）；色块边长按可用宽度均分（上限 48dp），窄屏不会把最后一块挤出去；改完立刻重组整棵树，不需要重启 |
| 应用图标 | 五个 `activity-alias` + adaptive icon | 图标 = 该色板的深色 `primary` 作底 + 白色播放三角；**五套共用一个前景**，只换背景色（与设置页色块同色）。设置里「图标跟随主题色」**默认关**，开启后**离开应用才生效**；「跟随壁纸」保持品牌粉。机制与边界见 `docs/android-native.md`「桌面图标」 |
| 倍速设置 | 底部条「倍速」小胶囊 + `DropdownMenu` | 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2x，当前值用 `primary` 标出。菜单本体由播放器持有、挂在控件层**之外**——挂进去会跟着控件一起被自动隐藏销毁（实测「菜单一闪就关」）。`speed` 是播放器实例内的状态，换视频回到 1x（legacy 曾跨视频持久化，native 没做） |
| 播放器控件 | media3 的 `PlayerDefaults`（Compose Material3）官方槽位 + 右侧两颗小胶囊 | 中心「快退 / 播放暂停 / 快进」与底部进度条都由 media3 提供（按钮尺寸、渐变底、无障碍语义不抄 internal 常量，避免随版本漂移）；我们只在 `below` 槽位补倍速与全屏两颗 `OverlayChip`（放 `above` 会浮到画面中间挡内容）。单击画面只显隐控件，控件 150ms 淡入淡出，播放中 3 秒无操作自动隐藏（倍速菜单开着时不隐藏）；全屏锁横屏并隐藏状态栏 |
| 人机验证 | 独立 `ChallengeActivity` + `WebView` | Cloudflare 挑战时显式验证；`cf_clearance` 与 OkHttp 共享 `CookieManager`，验证后自动重试原请求 |

## Motion

选型只有一条判据：**转场两端是不是同一个持续存在的容器**。判据、模式定义、时长档位都来自 `.agents/skills/md3e` 的运动系统，这里只记本工程的落点。

| 场景 | 模式 | 时长 / 曲线 | 落点 |
|---|---|---|---|
| 卡片 → 详情播放器 | container transform（封面自己长成播放器框）+ 页面 fade through | 容器 `tween` 300ms；旧页 90ms 淡出、新页延迟 90ms 用 210ms 淡入 | `ui/components/HeroMotion.kt` |
| 页面三态（加载 / 空 / 错 / 内容） | fade through | 90 + 210，两段不重叠 | `PageStateTransition` |
| 其余页面切换 | shared axis X（整幅横滑，1:1 位移） | 300ms `tween` | `MainActivity` 的 nav 转场 |
| 同页内的显隐（播放器控件、提示） | fade | 150ms | 播放器覆盖层 |
| 组件内部（按压、展开、形态变化） | 交给 `MotionScheme.expressive()` | 官方 spring | `MaterialExpressiveTheme` |

三条硬规则：

1. **整屏内容不能用 alpha 过渡**。交叉淡入 = 两页文字叠在一起；顺序淡出再淡入 = 中间整屏露出窗口底色（「闪一下」）。整屏切换要么不透明横滑，要么让容器自己长过去（container transform），页面本身只用 fade through 兜住。方向按底栏顺序定，推进入口（详情 / 列表）右进左出。
2. **共享元素的时长必须等于导航转场的时长，且两边都用 `tween`**。一方是空间 spring 的话，转场结束的那一刻会把还在跑的动画硬切成终点。试过用 `MotionScheme.expressive()` 的空间 spring 做横滑，节奏和页面内容不齐，已换回 300ms tween。
3. **两种等待必须长得不一样**：界面取数用页面级 `LoadingIndicator`（形变团块），媒体流缓冲用 `CircularWavyProgressIndicator`（波浪环）。同一形状家族会让「网站在响应」和「视频在起播」混成一件事。

`MotionScheme.expressive()` 管的是官方组件内部动效，页面转场它不管——两层的时长与曲线得各自定，不能指望前者给后者提供节奏。实现层的坑（组合期约束、`isCurrent` 判离开、`remember` 的 key 不能是派生值）记在 `docs/android-native.md`「已知坑」，不在这里重复。

## Design Tokens

- **圆角**：只用 `MaterialTheme.shapes`（M3E 八档）。封面/缩略图 `medium`(12dp)、分组容器 `largeIncreased`(20dp)、资料卡 `extraLarge`(28dp)、角标 `extraSmall`(4dp)
- **间距**：4 / 8 / 12 / 16 / 20 / 24 / 32；页面左右留白固定 16dp，分组之间 8–12dp
- **色板**：每套都由种子色推导——固定色相，按 M3 的 tone（`L*`）取各角色，超出 sRGB 色域时降饱和度：
  - 深色（主体验）`primary` 钉在该色相的 **tone 55**（品牌粉本身正好是这个音调），白字压它 **3.7:1**：够图形/大字的 3:1，不够正文级 4.5:1，所以强调色只做图形强调（FAB、选中态、进度条），不做小字正文底色
  - 浅色 `primary` 用该色相 tone 40 的深一档（品牌粉是 `#AA2F51`），白字 **6.5:1**
  - 五档 `surfaceContainer*` 必须显式给：只给 `surface` 时官方推导出的容器色会和底色几乎重合，「分组容器 / 卡片」的层级会整个丢掉（只剩分隔线）
  - `secondary` / `tertiary` / `error` / `outline` / `inverse*` 同源推导；所有 on/容器 配对实测对比度 ≥ 5.49:1
- **深色底的取值**：默认用 M3 的 neutral **tone 6**（`#171213`），**不是纯黑**。纯黑是 MD2 起就不推荐的深色底：OLED 上黑底与亮像素相邻会拖影，且纯黑之上留不出「更亮的容器」这一档。实测标准深色：底 `#171213` / 分组容器 `#221F1F`（通道差约 12 级）；纯黑模式：底 `#000000` / 容器 `#1E1B1B`
- **中性表面的色度**：取 **2**（`#171213` 的通道差 5）。这是「表面带一丝来源色相」与「看起来是灰」的平衡点：官方基线深色 surface `#141218` 的通道差是 6，而色度取 4 时我们到 8，肉眼就能看出偏粉（已踩过）。要彻底中性灰就取 0（`#131313`）
- **纯黑背景**：设置项、**默认关**（仅深色生效）。给想省电的人用，切过去时容器梯度整体压暗，避免容器和底几乎同色
- **明暗模式**：默认跟随系统，设置页可强制浅色/深色；状态栏与导航栏图标按**实际生效**的主题再设一次（`enableEdgeToEdge` 只跟系统走，强制浅色时会白图标压白底）
- **主题色**：一排六块色块，`跟随壁纸` ＋ 四套静态色板（紫 / 蓝 / 青绿 / 琥珀），默认品牌粉。壁纸取色（Android 12+）不是独立开关，而是这条轴上的第一块——它的意思只是「整套色板由系统按壁纸生成」；低版本没有系统色板，那块直接不出现
- **五套色板同一套 tone 阶梯、只差色相**：`scripts/gen-theme.py` 从种子色一次生成（`Palettes.kt`，勿手改），所以每套的对比度与品牌色板逐位相同，换色源既不用重新验算对比度，也不会改变容器台阶；中性表面色度仍是 2，深色底仍是 tone 6

## Known Limits

- 不做 iOS / Web 适配：只交付 Android（`*.ios.*` / `*.web.*` 已删除，`*.android.*` 也已并入普通文件）；恢复需要重新引入对应实现并重测
- 站点的内容语言比 App 界面翻译多：菜单里有 `cn / tw / ja / en / ko / ms / th`，界面只翻了 4 种。「跟随系统」时界面回退英文、内容按系统语言走，两者可以不一致（ja 内容 + 英文界面是允许的组合）
- `ko / ms / th` 只在站点菜单中确认存在，Cloudflare 拦截导致尚未拿到 200 实测
- 「随机」板块在非中文语言下没有「更多」链接，可能不显示
- 图标换色只覆盖「读启动组件 `ActivityInfo`」的地方（桌面、启动画面、概览）；「设置 → 应用信息」「权限弹窗」「分享面板」读 `<application android:icon>`，永远是品牌粉。另外用户开了「主题化图标」（Android 13+）时系统会按壁纸色给图标上色，Android 16 QPR 2 起对没提供 monochrome 层的应用也会自动上色——**色板颜色可能被系统覆盖**，这是机制的边界不是 bug
- 人机验证走 WebView + 共享 Cookie；若 Cloudflare 进一步按 TLS 指纹拒绝，验证后仍可能 403（备选是让 WebView 代取 HTML，未做）
- 登录前必须清空 Cookie 罐：带旧会话/Cloudflare Cookie 时 `/api/login` 返回 401；清空后登录响应写入新会话
- 登录态失效只认「受保护页面被 302 回首页」：看 `response.url` / WebView 回传的最终地址。不能用「页面里有没有 `/logout`」判断——挑战页、502 页、精简布局的表单页都没有它，会把有效会话误判成失效并主动登出
- 站点所有页面都内嵌 `#login_email` 登录弹窗，因此也不能靠「有没有登录表单」判断登录态
- 片单列表/详情都是服务端直出的 HTML，直接解析即可（实测 ~0.8s，7 条影片）；WebView 只在原生取数失败时兜底。早前“原生只有空壳、必须 WebView”的结论不成立，已实测推翻
- 详情页「相关推荐」不在 HTML 里：由页面内 Vue 组件向 Recombee（`client-rapi-missav.recombee.com`，带 `frontend_sign`）请求后渲染，原生 HTML 只有空壳，因此未实现；要做就是新增第三方请求与签名逻辑，不是解析问题。信息块本身是服务端直出，可直接解析
- 首页顶部「推荐给你」与详情页「相关推荐」同源：站点用 Recombee 个性化接口（原生 HTML 只有占位骨架和 `x-for` 模板），未实现，首页用「今日热门」替代
- 片单接口挂在根路径（`/api/playlists/...`），没有语言前缀；其它接口是 `/cn/api/...`。请求层因此带 `rawPath` 开关，写错前缀会 404「route cn/api/playlists/... could not be found」
- 新建/删除片单没有 JSON 接口，是服务端直出的网页表单（新建页 `/playlists/create/{dvdId}`，删除表单在「修改片单」页 `/playlists/{id}/edit`，`_method=delete` + `_token`，POST 到片单自身 URL）。直接取 HTML 实测就能拿到表单，WebView 只作回退

> Expo 时代的已知限制（RN `Modal` 遮罩、RNGH 手势、`expo-sqlite` 存储那些）见 [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)。

## Technical Stack

**现役（Kotlin 原生，仓库根）**

- Kotlin 2.4.21 + Jetpack Compose（BOM **alpha 2026.11.00** → material3 1.5.0-rc01）+ Material 3 **Expressive**；AGP 9.5.1 / Gradle 9.6.0
- 单 Activity + Navigation Compose；界面一律自己写 Compose，不引第三方 UI 组件库
- 数据层：OkHttp + Jsoup
- 存储：目前是 `SharedPreferences`（凭据再用 Android Keystore 里的密钥做 AES-GCM 加密）；迁 DataStore / Room 只在代码 TODO 里，尚未发生
- 媒体：Media3 ExoPlayer；图片 Coil；网页/人机验证：系统 `WebView` + `CookieManager`

> Expo 版的技术栈（SDK 58 / RN 0.88 / `expo-router` / `expo-sqlite` 存储 / `expo-video`）记在
> [`legacy/expo/DESIGN-legacy.md`](legacy/expo/DESIGN-legacy.md)。那份归档不再构建、不再运行，`node_modules` 已删除，需要时 `bun install`。
