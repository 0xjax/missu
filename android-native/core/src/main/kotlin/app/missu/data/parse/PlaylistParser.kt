package app.missu.data.parse

import app.missu.data.api.SourceConfig
import app.missu.data.model.PlaylistDetail
import app.missu.data.model.PlaylistSummary
import java.net.URI
import org.jsoup.Jsoup

/**
 * 片单页解析。`/playlists` 与 `/playlists/{id}` 都是服务端直出的 HTML（站点没有对应 JSON 接口），
 * 规则逐条对照 legacy `parsePlaylists` / `parsePlaylistDetail`。
 *
 * WARNING: 站点**没有**片单列表 / 详情的 JSON 接口（详情页脚本里只出现 `/api/playlists/{dvdId}`、
 * `/api/playlists/add|remove` 这类「针对某部影片」的调用），所以这里只能解析 HTML；
 * 改动选择器前先确认页面结构，并更新 `core/src/test/resources/playlists.html` 的测试。
 */
object PlaylistParser {

    fun parsePlaylists(html: String): List<PlaylistSummary> {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<PlaylistSummary>()

        for (anchor in doc.select("a[href*=/playlists/]")) {
            val href = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
            val pathname = runCatching { URI(href).path }.getOrNull() ?: continue
            // NOTE: 渲染后的链接带语言前缀（/cn/playlists/xxx），先归一化再匹配。
            val path = SourceConfig.contentPath(pathname)
            if (!PLAYLIST_PATH.matches(path) || path == "/playlists/create") continue
            if (!seen.add(path)) continue

            // NOTE: 用 wholeText() 而不是 text()——text() 会把源码里的换行压成空格，
            // 而片单行的语义是靠换行分行的（名字 / 可见性 / 作者 / 更新时间）。
            val lines = anchor.wholeText().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val name = lines.firstOrNull() ?: continue
            if (name.isEmpty()) continue

            val visibility = lines.firstOrNull { VISIBILITY.containsMatchIn(it) }
            val updatedLine = lines.firstOrNull { UPDATED_LABEL.containsMatchIn(it) }
            val updatedAt = updatedLine?.let { DATE.find(it)?.value }
            val owner = lines.firstOrNull { it != name && it != visibility && !UPDATED_LABEL.containsMatchIn(it) }

            result += PlaylistSummary(
                id = path.split('/').filter { it.isNotEmpty() }.last(),
                path = path,
                name = name,
                visibility = visibility,
                owner = owner,
                updatedAt = updatedAt,
            )
        }
        return result
    }

    fun parsePlaylistDetail(html: String): PlaylistDetail {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)
        val rawTitle = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        return PlaylistDetail(
            name = TITLE_PREFIX.replace(rawTitle, "").trim(),
            videos = HomeParser.parseVideoCards(doc),
        )
    }

    private val PLAYLIST_PATH = Regex("^/playlists/[^/]+$")
    private val VISIBILITY = Regex("^(私人|公開|公开|Private|Public)$", RegexOption.IGNORE_CASE)
    private val UPDATED_LABEL = Regex("更新|Updated", RegexOption.IGNORE_CASE)
    private val DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val TITLE_PREFIX = Regex("^(?:片單|片单|Playlist)[:：]\\s*", RegexOption.IGNORE_CASE)
}
