# MissAV API 规格

- `openapi.json`：人工校正的正式契约，供后续客户端实现使用。
- `openapi.generated.json`：从脱敏 HAR 自动生成的草稿，不直接手工修改。
- 抓取源：`https://missav.ai`，抓取日期：2026-11-11；片单读写在 2026-11-12 补齐并实测。

## 重新生成草稿

原始 HAR 保存在本地 `.local/missav-api/`，该目录已被 Git 忽略，不提交 Cookie、令牌或签名播放地址。

```bash
bun scripts/har-to-openapi.js \
  .local/missav-api/home.har \
  .local/missav-api/list.har \
  .local/missav-api/list-page2.har \
  .local/missav-api/search.har \
  .local/missav-api/detail.har
```

脚本仅保留 HTML/JSON 业务响应，过滤静态资源和媒体文件，合并重复操作并推断参数及 JSON Schema。生成后需人工校正路径参数、动态路由、接口语义和错误响应。

## 当前源站约束

- 首页、列表、搜索、详情和收藏页返回 HTML；登录、查看状态、收藏写操作、片单加入/移出使用 `/api/` JSON 接口；片单的**新建和删除是网页表单**（见下）。
- **API 路径一般带语言前缀**：站点自己的登录是 `POST /cn/api/login`（不是 `/api/login`）。客户端应复用内容语言拼接，参考 `localizedPath`。
- **片单与收藏接口是例外，挂在根路径**：`GET /api/items/{internalId}/view`、`POST|DELETE /api/items/{internalId}/save`、`GET /api/playlists/{dvdId}`、`POST /api/playlists/add|remove` 都没有语言前缀，写成 `/cn/api/items/...` 会 404（`The route cn/api/items/... could not be found`）。
- **收藏/片单写接口在会话失效时返回 401**（`{"message":"Unauthenticated."}`）：客户端把它当作「会话失效」处理，清本地登录态并引导重新登录，不展示原文；登录接口的 401 相反，表示账号或密码不对。
- **登录必须从无 Cookie 状态发起**：带旧会话或 Cloudflare Cookie 时 `/api/login` 会返回 `401 {"message":"Unauthenticated."}`；清空 Cookie 罐（`NativeModules.Networking.clearCookies`）后同一请求返回 200 并下发新的会话 Cookie。
- **会话是否失效只认「受保护页面被 302 回首页」**：原生 fetch 比对 `response.url` 的最终路径，WebView 比对回传的 `location.href`。不能用 `/logout` 链接判断——挑战页、502 页、精简布局的表单页都没有它，会把有效会话误判成失效并主动登出；也不能用 `id="login_email"` 判断，站点所有页面都内嵌登录弹窗。
- 登录和收藏请求携带 Android CookieManager 的原生会话；`Set-Cookie` 在 RN 响应头里通常不可见，XSRF 令牌只在能捕获时附带，不阻塞请求。不保存密码或 Cookie 到项目文件。
- Cookie 由 CookieManager 持久化到磁盘，重启 App 后会话通常仍然有效；启动时仍用 `/playlists` 做一次校验。
- 登录和收藏请求补发浏览器态的 `Origin`、`Referer`、`X-Requested-With`，与站点 Axios 行为一致。
- 片单列表/详情是服务端直出的 HTML，原生 `fetchHtml` 即可取到（实测 ~0.8s）；WebView 只在原生取数失败时兜底。渲染出的链接可能带语言前缀，解析前先归一化路径。
- 片单的新建/删除是服务端直出的**网页表单**，不是 JSON 接口，原生 fetch 就能拿到，无需 WebView：
  - 新建页 `GET /{lang}/playlists/create/{dvdId}`，表单字段名与 `_token` 从 HTML 读取后按 `application/x-www-form-urlencoded` POST 到表单 `action`；
  - 删除表单在「修改片单」页 `GET /{lang}/playlists/{id}/edit` 里，形如 `<form action="/cn/playlists/{id}" method="post"><input name="_method" value="delete"><input name="_token">`，原样提交；片单详情页本身没有删除入口。
- `/` 和 `/new` 会重定向到带动态 `dm` 前缀的页面；客户端应使用稳定入口并跟随重定向。
- 列表和搜索使用从 1 开始的 `page` 查询参数。
- **时间周期热度是同一面列表的三个别名**：`/today-hot`、`/weekly-hot`、`/monthly-hot` 分别等于 `/today-hot?sort=today_views|weekly_views|monthly_views`（第 1、2 页条目 id 实测完全一致，月榜同验）。客户端只要记一个 path + 排序取值即可。同理**女优月榜是独立页面** `/actresses/ranking`（导航标签形如「女优排行 NOV 2026」），不吃 `/actresses` 的 `sort` / `height` / `cup` / `age` / `debut` 参数——把它当 `sort=ranking` 发出去只会 404 或返回没有排序的目录。
- **列表页有两个口径，都是 query 参数**：`?sort=`（7 个取值：`released_at` / `published_at` / `saved` / `today_views` / `weekly_views` / `monthly_views` / `views`）与 `?filters=`（3 个取值：`individual` / `multiple` / `chinese-subtitle`）。**不传 = 站点默认序 / 不过滤**；默认序既不是 `published_at` 也不是 `released_at`（`/new` 实测），别拿某个值去「还原默认」。**`filters` 行为上是单选**：站点接受多值写法（重复参数、逗号都返回 200，不报错），但实测只生效一个——结果等于单个 `chinese-subtitle`，既非并集也非交集。所以这不是待修的缺陷，而是它的实际语义；客户端按单选实现。
- **站点给这 7 档的显示名（列表页头部的排序菜单，2026-11-14 取自站点自身 UI）**：`released_at` 发行日期 / `published_at` 最近更新 / `saved` 收藏数 / `today_views` 今日浏览数 / `weekly_views` 本週浏览数 / `monthly_views` 本月浏览数 / `views` 总浏览数；过滤控件是「过濾: 所有」。**站点没有「默认」这一档**——它总是把某个具体排序显示为选中态（`/siro` 上是「发行日期」），但 `/new` 不带参数时的顺序并不等于任何一个取值，所以「默认」是否真实存在随页面类型而异，客户端把它当独立状态更安全。
- **列表页头部直出「当前排序」**：一行 `排序: <当前项显示名>`（前缀文案随语言变），紧跟的下拉里是 7 个 `<a href="…?sort=取值">显示名</a>`——**锚点是多行标记**（`<a` 换行、`href=` 另起一行、显示名也单独一行）。所以「这一页当前的排序」不需要猜也无需额外请求：拿头部那句显示名去匹配下拉里同名的那一项，就得到 `?sort=` 的取值。站点**没有**「默认」项，它总把某个具体排序显示为当前项。
- **判断两个入口是不是同一集合要比条目 id，不要比页面字节数**：同一面列表的 `hreflang` / `canonical` 与排序菜单标记不同，长度能差 3–4KB，条目却一模一样。
- **`/genres` 索引里没有 VR**：实测 36 项/页、翻到第 2 页也没有，但 `/genres/VR` 是**正常类型页**（有片、有排序）。导航里的 VR 是单独一项，别指望从类型索引里解析出来。
- **频道来自首页导航**：素人 / 无码影片 / 亚洲 AV 三组频道（`/siro`、`/fc2`、`/madou` …）以及直播（`/klive`、`/clive`）都只在首页 HTML 的可折叠导航里列出，**没有接口**。导航是 Alpine 两段式：标题 `<a href="#" @click.prevent="showCollapse = … 'key' …">`，项目在紧跟的 `x-show="showCollapse === 'key'"` 的 `<span>` 里；同一份导航会出现两次（桌面 + 移动），解析后要去重。直播两页是**正常视频列表**（12 条/页、带预览片），不是外部直播站。
- 详情页通过内联打包脚本初始化 HLS 播放器，播放地址来自外部媒体主机；不得把临时 URL 写成固定接口。
- 首页、列表、搜索的卡片自带静音预览片：卡片 HTML 里直出 `<video class="preview" data-src="https://fourhoi.com/{变体路径}/preview.mp4">`（`muted`/`loop`，桌面端移入鼠标时替换封面）。**取这个 `data-src` 原样使用，不要用封面地址替换文件名推导**——路径里的「变体路径」是卡片自己的（同一番号的中文字幕 / 无码流出各有自己的预览轨），首尾模板页（首页头图、详情页）只有 Vue 的 `:data-src` 绑定、取不到值，缺片时回退封面。
- Cloudflare 挑战会对任意路径出现（含登录）。客户端通过 WebView 让用户显式完成验证，`cf_clearance` 与 RN fetch 共享 CookieManager；验证后自动重试原请求。
- `components.schemas` 是客户端解析目标；源站实际 HTTP 响应类型以各操作的 `content` 为准。
