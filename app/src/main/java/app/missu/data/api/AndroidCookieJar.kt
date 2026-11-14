package app.missu.data.api

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * 直接以系统 [CookieManager] 为存储的 CookieJar。
 *
 * NOTE: 这是 Cloudflare 那一关的关键——WebView 过完人机验证后，`cf_clearance` 写在系统 Cookie 里，
 * OkHttp 必须读同一份存储才能带着它发请求；反过来 OkHttp 收到的新 Cookie 也写回去，
 * 让 WebView 后续导航处于同一会话。legacy 就是靠这一份共享存储跑通的。
 */
class AndroidCookieJar : ClearableCookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val manager = CookieManager.getInstance()
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = CookieManager.getInstance().getCookie(url.toString()) ?: return emptyList()
        return header.split(';')
            .mapNotNull { part -> Cookie.parse(url, part.trim()) }
    }

    /**
     * 清空整罐。登录前必须调用（站点拒绝带旧会话的登录）。
     * NOTE: `removeAllCookies(Callback)` 在 API 30+ 标记废弃，但没有等价的同步 API 可用；
     * 这里不需要回调结果，传 null 即可。
     */
    @Suppress("DEPRECATION")
    override fun clear() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies(null)
        manager.flush()
    }
}
