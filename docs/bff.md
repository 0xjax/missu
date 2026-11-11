# Bun BFF 与上游恢复

`server/index.js` 是 MissAV 上游的轻量 Bun 适配服务。它保持现有网页路径不变，因此客户端解析器无需重写。

## 能力

- 公共 GET 内容缓存：首页、列表、搜索、目录和详情。
- `GET /api/content?path=...` 返回解析后的 JSON（`{ kind, data }`），客户端设置 `EXPO_PUBLIC_BFF_URL` 时优先使用。
- 未设置 BFF 时客户端仍直连源站并在本地解析 HTML，作为开发/降级路径。
- 相同 URL 的并发请求合并。
- 上游请求间隔 400ms。
- GET 403 退避后重试一次；公共内容失败时可回退到过期缓存。
- 登录、退出、云端收藏状态/写操作和 HLS 始终由手机原生直连；这些账号 Cookie 不与 BFF 共享。
- 公共响应 Cookie 在本地 HTTP 开发时会调整 Domain/Secure/SameSite 属性。
- 公共 GET 遇到 403、连接重置或超时时，可选 FlareSolverr 持久浏览器会话恢复；不自动处理 CAPTCHA。

JSON 解析逻辑位于 `src/api/parse.ts`，由客户端和 BFF 共用；部署 `server/` 时必须同时保留该文件及其依赖。真实账号 Cookie 和收藏写操作仍直连源站，不经过 `/api/content`。

默认上游为 `https://missav.ai`，默认仅监听 `127.0.0.1:3000`。不要未经额外鉴权和 TLS 加固就暴露到公网。

## 本机启动

```bash
bun run server
```

自定义端口：

```bash
PORT=3100 bun run server
```

## 让 Expo 使用 BFF

Bash：

```bash
EXPO_PUBLIC_BFF_URL=http://127.0.0.1:3000 bun run start
```

PowerShell 5.1：

```powershell
$env:EXPO_PUBLIC_BFF_URL = 'http://127.0.0.1:3000'
bun run start
```

未设置 `EXPO_PUBLIC_BFF_URL` 时，客户端保持源站直连。

## Android 真机局域网测试

1. 查询电脑局域网 IP，例如 `192.168.1.100`。
2. 启动 BFF 并显式监听局域网：

```powershell
$env:HOST = '0.0.0.0'
$env:PORT = '3000'
bun run server
```

3. 启动 Metro，BFF 地址使用电脑 IP，不能使用手机的 `127.0.0.1`：

```powershell
$env:EXPO_PUBLIC_BFF_URL = 'http://192.168.1.100:3000'
bun run start
```

4. 允许 Windows 防火墙中的专用网络访问，然后在 Expo Go 中重新加载应用。

## 可选 FlareSolverr 恢复

1. 将 FlareSolverr 服务绑定到 `127.0.0.1:8191`，不要暴露公网。
2. 启动 BFF 时设置：

```powershell
$env:FLARESOLVERR_URL = 'http://127.0.0.1:8191/v1'
bun run server
```

BFF 使用名为 `missav-bff` 的持久会话，仅在 GET 请求直接访问及一次退避重试仍返回 403 时调用浏览器恢复。FlareSolverr 的浏览器实例和 Cookie 会消耗内存，长期不用时应停止服务。

HLS 视频仍由 Android 原生 `expo-video` 直接播放，不经过 BFF，避免不必要的带宽和内存消耗。
