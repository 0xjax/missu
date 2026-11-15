package app.missu.data.api

import okhttp3.CookieJar

/**
 * 可清空的 Cookie 罐。
 * NOTE: 站点拒绝带旧会话 / Cloudflare Cookie 的登录请求（返回 401），登录前必须清空整罐；
 * 这个能力由具体实现提供（Android 用系统 CookieManager，测试用内存实现）。
 */
interface ClearableCookieJar : CookieJar {
    suspend fun clear()
}
