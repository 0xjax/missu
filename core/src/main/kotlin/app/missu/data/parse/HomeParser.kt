package app.missu.data.parse

import app.missu.data.api.SourceConfig
import app.missu.data.api.VideoListOptions
import app.missu.data.model.HomeSection
import app.missu.data.model.NavGroup
import app.missu.data.model.NavItem
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
    private val COLLAPSE_KEY = Regex("""showCollapse\s*===\s*'([^']+)'""")
    private val BASE_HOST = java.net.URI(SourceConfig.BASE_URL).host

    /** 头部「排序: X」那行文本（X 是站点当前排序的显示名）。 */
    private val SORT_HEADER = Regex(""">([^<>]{1,40})</span>\s*<svg[^>]*showDropdown !== 'sort'""")

    /**
     * 排序下拉里的一项：`<a href="…?sort=value" …>显示名</a>`。
     *
     * NOTE: 只看取值、不看参数顺序。筛选生效时站点把 `sort=` 挤到第二个参数、并用 `&amp;` 转义
     * （`…?filters=chinese-subtitle&amp;sort=today_views`，2026-11-15 实测）——所以前缀既可能是
     * `?` / `&`，也可能是 HTML 实体化的 `&amp;`。
     */
    private val SORT_ITEM = Regex("""<a\s[^>]*?href="([^"]*(?:[?&]|&amp;)sort=([a-z_]+)[^"]*)"[^>]*>\s*([^<>]{1,24}?)\s*</a>""")

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
        return VideoList(
            videos = parseVideoCards(doc),
            nextPage = if (hasNext) next else null,
            sort = parseListSort(html),
        )
    }

    /**
     * 站点自己在列表页头部声明的「当前排序」。
     *
     * 结构（列表页 HTML）：头部是一行 `<span>排序: 发行日期</span><svg x-show="showDropdown !== 'sort'">…`，
     * 紧跟的下拉里是 7 个 `<a href="…?sort=value">显示名</a>`。所以只要**拿显示名去匹配那个 href**，
     * 就能得到取值——显示名随内容语言变化，取值不变，我们不需要认识任何语言，也不需要猜。
     *
     * NOTE: 站点没有独立的「默认」项，它总把某个具体排序显示成当前项（`/siro` 是「发行日期」）。
     * 所以客户端不需要造一个「默认」档，只要把站点声明的这一项显示成选中即可。
     * 解析失败（站点改结构）返回 null，调用方回落到「不传参数」并显示「默认」。
     */
    fun parseListSort(html: String): VideoListOptions.Sort? {
        val header = SORT_HEADER.find(html)?.groupValues?.get(1)?.trim() ?: return null
        // 「排序: 发行日期」——前缀文案随语言变，所以只取最后一个分隔符后面的显示名。
        val label = header.substringAfterLast(':').substringAfterLast('：').trim().ifEmpty { header }
        val blockStart = html.indexOf("showDropdown === 'sort'").takeIf { it >= 0 } ?: return null
        val block = html.substring(blockStart, minOf(blockStart + 8_000, html.length))
        val value = SORT_ITEM.findAll(block)
            .firstOrNull { it.groupValues[3].trim() == label }
            ?.groupValues
            ?.get(2)
            ?: return null
        return VideoListOptions.Sort.entries.firstOrNull { it.value == value }
    }

    private fun pageParam(href: String): String? {
        val query = runCatching { java.net.URI(resolve(href)).rawQuery }.getOrNull() ?: return null
        return query.split('&').firstOrNull { it.startsWith("page=") }?.substringAfter("page=")
    }

    /**
     * 站点导航里的可折叠分组（素人 / 无码影片 / 亚洲 AV …），用于「频道」页签。
     *
     * 结构（首页 HTML，Alpine.js）：分组标题是 `<a href="#" @click.prevent="showCollapse = … 'key' …">`
     * 里唯一的 `<span>`；紧跟其后那个 `x-show="showCollapse === 'key'"` 的 `<span>` 才是它的项目。
     * 同一份导航在页面里出现两次（桌面 + 移动），所以按「项目路径序列」去重。
     *
     * NOTE: 只用结构与路径，不看分组标题文案——标题随内容语言变化，拿它做判据会在换语言时全崩。
     * NOTE: 返回**全部**分组（含「观看日本 AV」「我的收藏」这些）；哪些算频道由 `:app` 侧按路径过滤。
     */
    fun parseNav(html: String): List<NavGroup> {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)

        return doc.select("a").mapNotNull { header ->
            if (header.attr("href") != "#") return@mapNotNull null
            val key = COLLAPSE_KEY.find(header.attr("@click.prevent"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = header.selectFirst("span")?.text()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val panel = header.nextElementSibling() ?: return@mapNotNull null
            if (!panel.attr("x-show").contains("showCollapse === '$key'")) return@mapNotNull null

            val items = panel.select("a[href]").mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
                val name = anchor.text().trim()
                if (name.isEmpty() || href.isEmpty() || href.startsWith("#")) return@mapNotNull null
                // 外链（页脚那堆 bit.ly / 友站）不是站内频道。
                val host = runCatching { java.net.URI(href).host }.getOrNull() ?: return@mapNotNull null
                if (host != BASE_HOST) return@mapNotNull null
                NavItem(name = name, path = SourceConfig.contentPath(pathnameOf(href)))
            }.distinctBy { it.path }

            if (items.isEmpty()) null else NavGroup(title = title, items = items)
        }.distinctBy { group -> group.items.map { it.path } }
    }

    fun parseVideoCards(root: Element): List<VideoSummary> =
        root.select(".thumbnail.group").mapNotNull { thumbnail ->
            val anchor = thumbnail.selectFirst("a[href]") ?: return@mapNotNull null
            val pageUrl = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
            if (pageUrl.isEmpty()) return@mapNotNull null

            val id = pageUrl.substringBefore('?').trimEnd('/').substringAfterLast('/')
            // NOTE: 空结果页/页脚里也会出现 `.thumbnail.group` 外壳，最后一段可能是 `#` 这类非番号；
            // 只接受含字母或数字的 id，否则会解析出一张标题为域名的"幽灵卡片"（实测踩过）。
            if (id.isEmpty() || DM_PREFIX.matches(id) || id.none { it.isLetterOrDigit() }) return@mapNotNull null

            // NOTE: 真卡片一定带封面 `<img>`。没有 `<img>` 的 `.thumbnail.group` 不是视频卡——
            // 空结果页上就留着这么一个壳，解析出来就是一张没有图的空缩略图卡片。
            val image = thumbnail.selectFirst("img") ?: return@mapNotNull null

            val card = thumbnail.parent() ?: return@mapNotNull null
            val title = card.selectFirst("a.text-secondary")?.text()?.trim().orEmpty().ifEmpty { id }

            val rawImage = image.attr("data-src").takeIf { it.isNotEmpty() } ?: image.attr("src")
            val thumbnailUrl = rawImage.takeIf { it.isNotEmpty() && !it.startsWith("data:") }?.let { absolute(it) }

            // NOTE: 卡片同时直出一段静音预览片（`<video class="preview" data-src="…/preview.mp4">`），
            // 站点在桌面端移入鼠标时就是拿它换掉封面。我们原样取这个值，不按番号拼 URL——
            // 变体（中文字幕 / 无码流出）各有自己的预览轨。
            // WARNING: Vue 模板里是 `:data-src` 绑定属性（详情页、首页头图），取不到值；`javascript:;`
            // 这类占位也在这里滤掉，宁可没有预览，也不要让播放器去加载一个假地址。
            val rawPreview = thumbnail.selectFirst("video.preview")?.attr("data-src").orEmpty()
            val previewUrl = rawPreview
                .takeIf { it.isNotEmpty() }
                ?.let { absolute(it) }
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

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
                previewUrl = previewUrl,
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
