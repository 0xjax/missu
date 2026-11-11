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
- 登录和收藏请求携带原生 Cookie 会话，并按网页 Axios 行为发送内存中的 XSRF 令牌；不保存密码或 Cookie 到项目文件。
- `/` 和 `/new` 会重定向到带动态 `dm` 前缀的页面；客户端应使用稳定入口并跟随重定向。
- 列表和搜索使用从 1 开始的 `page` 查询参数。
- 详情页通过内联打包脚本初始化 HLS 播放器，播放地址来自外部媒体主机；不得把临时 URL 写成固定接口。
- 普通导航请求成功，而同页 `fetch` 请求曾收到 Cloudflare 403。规格记录这一行为，不包含绕过验证的方案。
- `components.schemas` 是客户端解析目标；源站实际 HTTP 响应类型以各操作的 `content` 为准。
