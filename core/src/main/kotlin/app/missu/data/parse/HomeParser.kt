package app.missu.data.parse

import app.missu.data.api.SourceConfig
import app.missu.data.model.HomeSection
import app.missu.data.model.VideoList
import app.missu.data.model.VideoSummary
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 首页与视频卡片的解析。选择器逐条对照 legacy 的 `src/api/parse.ts`，不要凭印象改。
 *
 * 卡片结构（站点直出）：
 * `.thumbnail.group` 里是封面链接与图片，它的**父节点**才是整张卡片（标题在 `a.text-secondary`）。
 * 时长与角标都在同一棵子树里的 `span` 上，靠位置类区分，不额外请求。
 */
object HomeParser {
    private val SECTION_IDS = setOf("new", "release", "fc2", "random")
    private val SECTION_TITLES = mapOf(
        "新作上市" to "release",
        "最近更新" to "new",
        "無碼影片" to "fc2",
        "无码影片" to "fc2",
        "隨機" to "random",
        "随机" to "random",
    )
    private val DURATION = Regex("^\\d+:\\d{2}(:\\d{2})?$")
    private val DM_PREFIX = Regex("^dm\\d+$")

    fun parseHome(html: String): List<HomeSection> {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)

        return doc.select("h1, h2, h3").mapNotNull { heading ->
            val title = heading.text().trim()
            // 「更多」链接在标题所在行，靠路径末段识别板块，避免依赖各语言标题文案；
            // 页脚也有指向 new/release 的链接，所以只认唯一命中的标题行。
            val row = heading.parent()?.parent()
            val links = row?.select("a[href]").orEmpty().mapNotNull { anchor ->
                val absolute = resolve(anchor.absUrl("href").ifEmpty { anchor.attr("href") })
                val pathname = pathnameOf(absolute)
                val id = pathname.trimEnd('/').substringAfterLast('/')
                if (id.isNotEmpty() && SECTION_IDS.contains(id)) id to pathname else null
            }
            val link = links.map { it.first }.distinct().singleOrNull()?.let { id -> links.first { it.first == id } }

            val sectionId = SECTION_TITLES[title] ?: link?.first ?: return@mapNotNull null
            val section = heading.parent()?.parent()?.parent() ?: return@mapNotNull null
            val videos = parseVideoCards(section)
            if (videos.isEmpty()) return@mapNotNull null

            HomeSection(id = sectionId, title = title, videos = videos, morePath = link?.second)
        }
    }

    /**
     * 列表/搜索页：卡片规则与首页相同，另加一次「有没有下一页」的判断。
     * NOTE: 下一页信号是页面里任意链接带 `?page=N+1`；legacy 用 searchParams 比较，这里等价实现。
     */
    fun parseVideoList(html: String, page: Int = 1): VideoList {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)
        val next = page + 1
        val hasNext = doc.select("a[href]").any { anchor ->
            val href = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
            pageParam(href) == next.toString()
        }
        return VideoList(videos = parseVideoCards(doc), nextPage = if (hasNext) next else null)
    }

    private fun pageParam(href: String): String? {
        val query = runCatching { java.net.URI(resolve(href)).rawQuery }.getOrNull() ?: return null
        return query.split('&').firstOrNull { it.startsWith("page=") }?.substringAfter("page=")
    }

    fun parseVideoCards(root: Element): List<VideoSummary> =
        root.select(".thumbnail.group").mapNotNull { thumbnail ->
            val anchor = thumbnail.selectFirst("a[href]") ?: return@mapNotNull null
            val pageUrl = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
            if (pageUrl.isEmpty()) return@mapNotNull null

            val id = pageUrl.substringBefore('?').trimEnd('/').substringAfterLast('/')
            if (id.isEmpty() || DM_PREFIX.matches(id)) return@mapNotNull null

            val card = thumbnail.parent() ?: return@mapNotNull null
            val title = card.selectFirst("a.text-secondary")?.text()?.trim().orEmpty().ifEmpty { id }

            val image = thumbnail.selectFirst("img")
            val rawImage = image?.attr("data-src")?.takeIf { it.isNotEmpty() } ?: image?.attr("src")
            val thumbnailUrl = rawImage?.takeIf { it.isNotEmpty() && !it.startsWith("data:") }?.let { absolute(it) }

            val spans = thumbnail.select("span").map { span ->
                Triple(span.className(), span.text().trim(), span)
            }
            val duration = spans.firstOrNull { DURATION.matches(it.second) }?.second

            // NOTE: 封面左下角是变体角标（中文字幕 / 無碼影片），右下角是时长；用位置类区分。
            val badges = spans
                .filter { (cls, _, _) ->
                    cls.contains("bottom-1") && cls.contains("left-1") && !cls.contains("right-1")
                }
                .map { it.second }
                .filter { it.isNotEmpty() }

            VideoSummary(
                id = id,
                title = title,
                pageUrl = absolute(pageUrl),
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                badges = badges,
            )
        }

    private fun absolute(value: String): String = resolve(value)

    /**
     * 按站点基址解析链接：同时支持 `/x`、`x.jpg` 和 `//host/x`（协议相对）。
     * NOTE: legacy 用 `new URL(value, BASE)`，Kotlin 侧等价的是 `URI.resolve`；
     * 手写字符串拼接会把 `//host/x` 拼成 `https://missav.ai//host/x`，这是踩过的坑。
     */
    private fun resolve(value: String): String =
        runCatching { java.net.URI(SourceConfig.BASE_URL + "/").resolve(value).toString() }
            .getOrDefault(value)

    private fun pathnameOf(url: String): String =
        runCatching { java.net.URI(url).path ?: url }.getOrDefault(url)
}
