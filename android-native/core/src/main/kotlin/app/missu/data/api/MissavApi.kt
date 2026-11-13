package app.missu.data.api

import app.missu.data.model.AccountUser
import app.missu.data.model.HomeSection
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoList
import app.missu.data.parse.DetailParser
import app.missu.data.parse.HomeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 站点接口。函数命名与 legacy 的 `src/api/missav.ts` 保持一致，便于逐个对照迁移。
 *
 * 人机验证：站点用 Cloudflare 挡非浏览器客户端（实测 403 + `cf-mitigated: challenge`）。
 * 命中时交给 [challenge] 让用户过一遍，`cf_clearance` 写进共享 Cookie 后重试一次。
 *
 * TODO: 目前只有 `getHome`，后续按迁移顺序补 getVideos / getVideoDetail / searchVideos 等。
 */
class MissavApi(
    private val client: MissavClient = MissavClient(),
    private val challenge: ChallengeHandler? = null,
) {

    /** 首页板块。站点直出 HTML，直接 Jsoup 解析，不需要 WebView。 */
    suspend fun getHome(): List<HomeSection> = withChallengeRetry { HomeParser.parseHome(client.fetchHtml("/")) }

    /** 详情页。播放地址从内联打包脚本里解出来，见 [app.missu.data.parse.DetailParser.extractPlaybackUrl]。 */
    suspend fun getVideoDetail(id: String): VideoDetail = withChallengeRetry {
        DetailParser.parse(client.fetchHtml("/$id"), id)
    }

    suspend fun html(path: String): String = withChallengeRetry { client.fetchHtml(path) }

    /**
     * 登录。步骤与 legacy 一致：
     * 1. 先清空 Cookie 罐——带旧会话或 Cloudflare Cookie 时站点直接 401；
     * 2. POST `/{lang}/api/login`，body 带 `remember: true`；
     * 3. 响应只有 `{"user":{"name":...}}`，邮箱用本次输入补全。
     *
     * NOTE: 清空后仍 401 表示账号或密码错，必须转成 [InvalidCredentialsException]，
     * 不能沿用「会话失效」的提示（legacy 踩过）。
     */
    suspend fun login(email: String, password: String): AccountUser {
        val payload = buildJsonObject {
            put("email", email)
            put("password", password)
            put("remember", true)
        }.toString()

        val body = withChallengeRetry {
            client.clearCookies()
            try {
                client.postJson("/api/login", payload)
            } catch (error: SessionExpiredException) {
                throw InvalidCredentialsException()
            }
        }

        val username = runCatching {
            Json.parseToJsonElement(body).jsonObject["user"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return AccountUser(email = email, username = username)
    }

    /**
     * 启动时校验会话：只有「受保护页面被 302 回首页」才判失效；
     * 挑战/网络错误一律维持登录态，避免误登出（legacy 结论）。
     */
    suspend fun checkSession(): Boolean = try {
        client.fetchHtml("/playlists")
        true
    } catch (error: SessionExpiredException) {
        false
    } catch (error: Exception) {
        true
    }

    /**
     * 登出：站点是普通 GET /logout 并跳回首页。
     * NOTE: 跳回首页会被判定成「会话失效」，这里按预期忽略异常——登出本来就要成功。
     */
    suspend fun logout() {
        runCatching { client.fetchHtml("/logout") }
        client.clearCookies()
    }

    /**
     * 搜索：路径 `/search/{keyword}`，分页用 `?page=N`（从 1 开始，第 1 页不带参数）。
     * NOTE: 关键词按 legacy 用 `encodeURIComponent` 语义（空格编码成 %20 而不是 +）。
     */
    suspend fun searchVideos(keyword: String, page: Int = 1): VideoList = withChallengeRetry {
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val path = if (page > 1) "/search/$encoded?page=$page" else "/search/$encoded"
        HomeParser.parseVideoList(client.fetchHtml(path), page)
    }

    private suspend fun <T> withChallengeRetry(block: suspend () -> T): T =
        try {
            block()
        } catch (error: ChallengeException) {
            val handler = challenge ?: throw error
            val result = handler.verify(SourceConfig.localizedUrl("/"))
            if (!result.passed) throw error
            // NOTE: 验证结果里的 HTML 属于某个具体路径，不能拿来当任意请求的响应；统一重试原请求。
            block()
        }
}
