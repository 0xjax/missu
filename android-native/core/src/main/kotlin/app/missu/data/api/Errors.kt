package app.missu.data.api

/** 请求失败（非 2xx）。 */
class HttpStatusException(val status: Int) : Exception("请求失败（HTTP $status）")

/** 命中 Cloudflare 人机验证：需要 WebView 走一遍并共享 Cookie 后重试。 */
class ChallengeException : Exception("人机验证未通过")

/** 登录态失效：受保护页面被站点 302 回了首页。 */
class SessionExpiredException : Exception("登录态已失效")
