# MissAV API 规格

- `openapi.json`：人工校正的正式契约，供后续客户端实现使用。
- `openapi.generated.json`：从脱敏 HAR 自动生成的草稿，不直接手工修改。
- 抓取源：`https://missav.ai`，抓取日期：2026-11-11。

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

- 首页、列表、搜索、详情和收藏页返回 HTML；登录、查看状态和收藏写操作使用 `/api/` JSON 接口。
- **API 路径也带语言前缀**：站点自己的登录是 `POST /cn/api/login`（不是 `/api/login`）。客户端应复用内容语言拼接，参考 `localizedPath`。
- **登录必须从无 Cookie 状态发起**：带旧会话或 Cloudflare Cookie 时 `/api/login` 会返回 `401 {"message":"Unauthenticated."}`；清空 Cookie 罐（`NativeModules.Networking.clearCookies`）后同一请求返回 200 并下发新的会话 Cookie。
- **不能用 `id="login_email"` 判断会话**：站点在所有页面都内嵌登录弹窗，该 id 始终存在。已登录页面才渲染 `/logout` 链接，客户端应据此判断（`isLoginPage`）。
- 登录和收藏请求携带 Android CookieManager 的原生会话；`Set-Cookie` 在 RN 响应头里通常不可见，XSRF 令牌只在能捕获时附带，不阻塞请求。不保存密码或 Cookie 到项目文件。
- Cookie 由 CookieManager 持久化到磁盘，重启 App 后会话通常仍然有效；启动时仍用 `/playlists` 做一次校验。
- 登录和收藏请求补发浏览器态的 `Origin`、`Referer`、`X-Requested-With`，与站点 Axios 行为一致。
- 片单列表/详情的原生 HTML 可能只有空壳（无列表标记），需在 WebView 中渲染后取 `document.documentElement.outerHTML`；渲染出的链接带语言前缀，解析前先归一化路径。
- `/` 和 `/new` 会重定向到带动态 `dm` 前缀的页面；客户端应使用稳定入口并跟随重定向。
- 列表和搜索使用从 1 开始的 `page` 查询参数。
- 详情页通过内联打包脚本初始化 HLS 播放器，播放地址来自外部媒体主机；不得把临时 URL 写成固定接口。
- Cloudflare 挑战会对任意路径出现（含登录）。客户端通过 WebView 让用户显式完成验证，`cf_clearance` 与 RN fetch 共享 CookieManager；验证后自动重试原请求。
- `components.schemas` 是客户端解析目标；源站实际 HTTP 响应类型以各操作的 `content` 为准。
