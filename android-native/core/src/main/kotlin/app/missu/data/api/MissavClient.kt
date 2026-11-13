package app.missu.data.api

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 内存 Cookie 罐。会话与 `cf_clearance` 都靠它维持。
 *
 * TODO: 接 WebView 人机验证时，必须让系统 `CookieManager` 与本罐双向同步，
 * 否则验证过了请求仍然 403（legacy 已踩过这个坑）。
 */
class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val bucket = store.getOrPut(url.host) { ConcurrentHashMap() }
        cookies.forEach { bucket[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.values?.filter { it.matches(url) } ?: emptyList()

    fun clear() = store.clear()
}

/** 站点 HTTP 客户端：UA、超时、Cookie，外加把人机验证响应翻译成 [ChallengeException]。 */
class MissavClient(
    private val userAgent: String = SourceConfig.DEFAULT_USER_AGENT,
    // NOTE: 用 okhttp3.CookieJar 而不是具体实现——Android 侧传的是系统 CookieManager 的适配器，
    // JVM 测试传内存实现，两者都要能进来。
    val cookieJar: CookieJar = MemoryCookieJar(),
) {
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchHtml(path: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SourceConfig.localizedUrl(path))
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            // NOTE: 与 legacy 一致——403、cf-mitigated 头、挑战页标题，三者任一即判定为人机验证。
            if (response.code == 403 ||
                response.header("cf-mitigated") == "challenge" ||
                CHALLENGE_TITLE.containsMatchIn(body)
            ) {
                throw ChallengeException()
            }
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            if (response.request.url.encodedPath == HOME_PATH && path.isNotBlank() && path != "/") {
                // NOTE: 受保护页面被 302 回首页 = 登录态失效；这是唯一可信的信号。
                throw SessionExpiredException()
            }
            body
        }
    }

    private companion object {
        val CHALLENGE_TITLE = Regex("<title>\\s*Just a moment\\.\\.\\.\\s*</title>", RegexOption.IGNORE_CASE)
        const val HOME_PATH = "/"
    }
}
