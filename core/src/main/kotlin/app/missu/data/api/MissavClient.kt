package app.missu.data.api

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 内存 Cookie 罐。会话与 `cf_clearance` 都靠它维持。
 *
 * TODO: 接 WebView 人机验证时，必须让系统 `CookieManager` 与本罐双向同步，
 * 否则验证过了请求仍然 403（legacy 已踩过这个坑）。
 */
class MemoryCookieJar : ClearableCookieJar {
    private val store = ConcurrentHashMap<String, MutableMap<String, Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val bucket = store.getOrPut(url.host) { ConcurrentHashMap() }
        cookies.forEach { bucket[it.name] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        store[url.host]?.values?.filter { it.matches(url) } ?: emptyList()

    override fun clear() = store.clear()
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

    /**
     * JSON GET/POST/DELETE。
     *
     * NOTE: `rawPath = true` 时不加语言前缀——收藏与片单接口只挂在根路径（`/api/items/...`），
     * 带 `/{lang}` 前缀会 404（legacy 的 rawPath 参数就是干这个的）。
     * NOTE: 401 统一表示会话失效；**登录接口的 401 语义不同**（账号密码错），由调用方转换。
     */
    suspend fun requestJson(
        method: String,
        path: String,
        body: String? = null,
        rawPath: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val referer = SourceConfig.BASE_URL +
            if (SourceConfig.contentLanguage == "tw") "/" else "/${SourceConfig.contentLanguage}/"
        val builder = Request.Builder()
            .url(if (rawPath) SourceConfig.BASE_URL + path else SourceConfig.localizedUrl(path))
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Origin", SourceConfig.BASE_URL)
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((body ?: "").toRequestBody(JSON_MEDIA_TYPE))
            "DELETE" -> builder.delete()
            else -> throw IllegalArgumentException("unsupported method: $method")
        }

        client.newCall(builder.build()).execute().use { response ->
            val text = response.body.string()
            if (response.code == 403 ||
                response.header("cf-mitigated") == "challenge" ||
                CHALLENGE_TITLE.containsMatchIn(text)
            ) {
                throw ChallengeException()
            }
            if (response.code == 401) throw SessionExpiredException()
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            text
        }
    }

    /** 兼容旧调用点：登录用的 JSON POST（走语言前缀）。 */
    suspend fun postJson(path: String, body: String): String = requestJson("POST", path, body)

    /**
     * 表单 POST（`application/x-www-form-urlencoded`）。
     * NOTE: 站点的 Blade 表单（新建片单）不接受 JSON，必须按表单编码提交，并带上 Origin/Referer。
     */
    suspend fun postForm(path: String, fields: Map<String, String>): String = withContext(Dispatchers.IO) {
        val referer = SourceConfig.BASE_URL +
            if (SourceConfig.contentLanguage == "tw") "/" else "/${SourceConfig.contentLanguage}/"
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }
        val request = Request.Builder()
            // NOTE: 片单删除表单的 action 是绝对 URL（含语言前缀），不能再走 localizedUrl，
            // 否则会拼成 /cn/https://… 直接 404（实测踩过）。只有相对路径才加语言前缀。
            .url(if (path.startsWith("http://") || path.startsWith("https://")) path else SourceConfig.localizedUrl(path))
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Origin", SourceConfig.BASE_URL)
            .header("Referer", referer)
            .post(body.toRequestBody(FORM_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.code == 403 || response.header("cf-mitigated") == "challenge" ||
                CHALLENGE_TITLE.containsMatchIn(text)
            ) {
                throw ChallengeException()
            }
            if (response.code == 401) throw SessionExpiredException()
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            text
        }
    }
    /** 清空 Cookie 罐（登录前必须调用，见 [ClearableCookieJar]）。 */
    fun clearCookies() {
        (cookieJar as? ClearableCookieJar)?.clear()
    }

    private companion object {
        val CHALLENGE_TITLE = Regex("<title>\\s*Just a moment\\.\\.\\.\\s*</title>", RegexOption.IGNORE_CASE)
        const val HOME_PATH = "/"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    }
}
