package app.missu.data.api

import app.missu.data.model.HomeSection
import app.missu.data.parse.HomeParser

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

    suspend fun html(path: String): String = withChallengeRetry { client.fetchHtml(path) }

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
