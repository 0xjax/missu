package app.missu.data.api

import app.missu.data.model.HomeSection
import app.missu.data.parse.HomeParser

/**
 * 站点接口。函数命名与 legacy 的 `src/api/missav.ts` 保持一致，便于逐个对照迁移。
 *
 * TODO: 目前只有 `getHome`，后续按迁移顺序补 getVideos / getVideoDetail / searchVideos 等。
 */
class MissavApi(private val client: MissavClient = MissavClient()) {

    /** 首页板块。站点直出 HTML，直接 Jsoup 解析，不需要 WebView。 */
    suspend fun getHome(): List<HomeSection> = HomeParser.parseHome(client.fetchHtml("/"))
}
