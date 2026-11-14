package app.missu.data.parse

import app.missu.data.api.SourceConfig
import app.missu.data.model.DirectoryItem
import app.missu.data.model.DirectoryList
import java.net.URI
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 目录页解析（女优 / 类型 / 厂商）。规则逐条对照 legacy `parseDirectory`：
 * 条目是 `a.text-nord13`，链接里必须含 `/{kind}/`，`/ranking` 结尾的要排除。
 *
 * NOTE: 计数文案随语言变化（條影片 / 条影片 / 本の動画 / videos），名字在计数之前，
 * 所以用「数字 + 各单位」的正则切开，不能写死某一个语言的词。
 */
object DirectoryParser {

    fun parse(html: String, kind: String, page: Int = 1): DirectoryList {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)
        val marker = "/$kind/"
        val byPath = LinkedHashMap<String, DirectoryItem>()

        for (anchor in doc.select("a.text-nord13[href]")) {
            val href = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
            // NOTE: 站点会给链接加 /dmNNN 与语言前缀（如 /dm825/cn/makers/Moody's），
            // 必须先归一化再判断与存储，否则 id/路径会带着这些临时前缀（实测厂商页全是这种链接）。
            val pathname = runCatching { URI(href).path }.getOrNull() ?: continue
            val path = SourceConfig.contentPath(pathname)
            if (!path.contains(marker) || path.endsWith("/ranking")) continue

            val raw = anchor.text().trim().replace(WHITESPACE, " ")
            if (raw.isEmpty()) continue

            val cut = COUNT_SUFFIX.find(raw)
            val name = (if (cut != null && cut.range.first > 0) raw.substring(0, cut.range.first) else raw).trim()
            if (name.isEmpty()) continue

            val parentText = anchor.parent()?.text()?.trim()?.replace(WHITESPACE, " ").orEmpty()
            val subtitle = raw.removePrefix(name).trim()
                .ifEmpty { parentText.replace(raw, "").trim() }
                .ifEmpty { null }

            val card = anchor.parent()?.parent()
            val image = card?.selectFirst("img")
            val imageUrl = image?.attr("data-src")?.ifEmpty { image.attr("src") }
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { URI(SourceConfig.BASE_URL + "/").resolve(it).toString() }.getOrDefault(it) }

            // NOTE: 无头像的条目用「首字占位块」，会先命中一个单字链接；同路径取名字最长的那条。
            val existing = byPath[path]
            if (existing == null || name.length > existing.name.length) {
                byPath[path] = DirectoryItem(id = path, path = path, name = name, subtitle = subtitle, imageUrl = imageUrl)
            }
        }

        val next = page + 1
        val hasNext = doc.select("a[href]").any { anchor ->
            val href = anchor.absUrl("href").ifEmpty { anchor.attr("href") }
            pageParam(href) == next.toString()
        }
        return DirectoryList(items = byPath.values.toList(), nextPage = if (hasNext) next else null)
    }

    private fun pageParam(href: String): String? {
        val query = runCatching { URI(href).rawQuery }.getOrNull() ?: return null
        return query.split('&').firstOrNull { it.startsWith("page=") }?.substringAfter("page=")
    }

    private val WHITESPACE = Regex("\\s+")
    private val COUNT_SUFFIX = Regex(
        "\\s+\\d+\\s+(?:條|条)?影片|\\s+\\d+\\s+本の動画|\\s+\\d+\\s+videos?\\b|\\s+第\\s*\\d+\\s*名|\\s+(?:No\\.?|#)\\s*\\d+",
        RegexOption.IGNORE_CASE,
    )
}
