package app.missu.data.api

import app.missu.data.model.HomeSection
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoList
import app.missu.data.parse.DetailParser
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

    /** 详情页。播放地址从内联打包脚本里解出来，见 [app.missu.data.parse.DetailParser.extractPlaybackUrl]。 */
    suspend fun getVideoDetail(id: String): VideoDetail = withChallengeRetry {
        DetailParser.parse(client.fetchHtml("/$id"), id)
    }

    suspend fun html(path: String): String = withChallengeRetry { client.fetchHtml(path) }

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
