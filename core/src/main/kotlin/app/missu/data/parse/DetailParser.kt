package app.missu.data.parse

import app.missu.data.api.SourceConfig
import app.missu.data.model.DetailInfoRow
import app.missu.data.model.DetailInfoValue
import app.missu.data.model.SeekPreviewSpec
import app.missu.data.model.VideoDetail
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 详情页解析。规则逐条对照 legacy 的 `src/api/parse.ts`（`parseVideoDetail` / `parseDetailInfo` /
 * `extractPlaybackUrl`），选择器与打包脚本解包算法都不改。
 */
object DetailParser {

    fun parse(html: String, id: String): VideoDetail {
        val doc = Jsoup.parse(html, SourceConfig.BASE_URL)
        val title = meta(doc, "og:title") ?: doc.selectFirst("h1")?.text()?.trim() ?: id
        val durationSeconds = meta(doc, "og:video:duration")?.toIntOrNull()

        return VideoDetail(
            id = id,
            title = title,
            pageUrl = "${SourceConfig.BASE_URL}/${encode(id)}",
            thumbnailUrl = meta(doc, "og:image")?.let { absolute(it) },
            duration = durationSeconds?.takeIf { it >= 0 }?.let { formatSeconds(it) },
            description = meta(doc, "og:description") ?: meta(doc, "description", "name"),
            releaseDate = meta(doc, "og:video:release_date"),
            actors = doc.select("meta[property=og:video:actor]")
                .mapNotNull { it.attr("content").trim().ifEmpty { null } },
            playbackUrl = extractPlaybackUrl(doc),
            seekPreview = extractSeekPreview(doc),
            internalId = INTERNAL_ID.find(html)?.groupValues?.get(1),
            info = parseInfo(doc),
        )
    }

    /**
     * 信息块：每行首个 `span` 是标签，其余子元素是值；链接先归一化成内容路径。
     * NOTE: 标签文案随内容语言变化，不硬编码。
     */
    private fun parseInfo(doc: Element): List<DetailInfoRow> =
        doc.select("div.text-secondary").mapNotNull { row ->
            val children = row.children()
            val labelNode = children.firstOrNull() ?: return@mapNotNull null
            if (!labelNode.tagName().equals("span", ignoreCase = true)) return@mapNotNull null
            val label = labelNode.text().trim()
            if (label.isEmpty()) return@mapNotNull null

            val values = children.drop(1).mapNotNull { node ->
                val text = node.text().trim()
                if (text.isEmpty()) return@mapNotNull null
                val href = node.attr("href")
                val path = if (href.isNotEmpty() && (href.startsWith("http") || href.startsWith("/"))) {
                    runCatching { SourceConfig.contentPath(URI(absolute(href)).path ?: "") }.getOrNull()
                } else {
                    null
                }
                DetailInfoValue(text = text, path = path)
            }
            if (values.isEmpty()) null else DetailInfoRow(label = label, values = values)
        }

    /**
     * 播放地址藏在页面内联的 Dean Edwards packed 脚本里：先解包，再从结果里挑 `.m3u8`。
     * NOTE: 段位常量与 legacy 一字不差；改这里必须能对上真实页面（`core/src/test/resources/detail.html`）。
     */
    fun extractPlaybackUrl(html: String): String? =
        extractPlaybackUrl(Jsoup.parse(html, SourceConfig.BASE_URL))

    private fun extractPlaybackUrl(doc: Element): String? {
        val scripts = doc.select("script")
        for (script in scripts) {
            for (match in PACKED_PATTERN.findAll(script.data())) {
                val unpacked = unpack(match.groupValues[1], match.groupValues[2].toInt(), match.groupValues[3].toInt(), match.groupValues[4])
                val url = URL_PATTERN.findAll(unpacked)
                    .map { it.value }
                    .firstOrNull { runCatching { URI(it).path?.endsWith(".m3u8") == true }.getOrDefault(false) }
                if (url != null) return url
            }
        }
        return null
    }

    /**
     * seek 预览没有 JSON 接口：原站把雪碧图配置直出在详情页的 Plyr `thumbnail` 设置里。
     * 依据是详情页内联脚本及 `js/plyr-plugin-thumbnail.js`；只消费页面已有值，不推导图片地址。
     */
    private fun extractSeekPreview(doc: Element): SeekPreviewSpec? {
        for (script in doc.select("script")) {
            val block = THUMBNAIL_BLOCK.find(script.data())?.groupValues?.get(1) ?: continue
            if (ENABLED.find(block)?.groupValues?.get(1)?.toBooleanStrictOrNull() != true) continue
            val frameCount = intField(block, "pic_num") ?: continue
            val width = intField(block, "width") ?: continue
            val height = intField(block, "height") ?: continue
            val columns = intField(block, "col") ?: continue
            val rows = intField(block, "row") ?: continue
            val urlsValue = URLS.find(block)?.groupValues?.get(1) ?: continue
            val urls = runCatching {
                Json.parseToJsonElement(urlsValue).jsonArray.map { it.jsonPrimitive.content }
            }.getOrNull() ?: continue
            if (frameCount <= 0 || width <= 0 || height <= 0 || columns <= 0 || rows <= 0 || urls.isEmpty()) continue
            return SeekPreviewSpec(frameCount, width, height, columns, rows, urls)
        }
        return null
    }

    private fun intField(block: String, name: String): Int? =
        Regex("\\b${Regex.escape(name)}\\s*:\\s*(\\d+)").find(block)?.groupValues?.get(1)?.toIntOrNull()

    private fun meta(root: Element, property: String, attribute: String = "property"): String? =
        root.selectFirst("meta[$attribute=$property]")?.attr("content")?.takeIf { it.isNotBlank() }

    private fun absolute(value: String): String =
        runCatching { URI(SourceConfig.BASE_URL + "/").resolve(value).toString() }.getOrDefault(value)

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun formatSeconds(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun encodeBase(value: Int, radix: Int): String {
        val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        var rest = value
        val out = StringBuilder()
        do {
            out.insert(0, digits[rest % radix])
            rest /= radix
        } while (rest > 0)
        return out.toString()
    }

    private fun decodePacked(value: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\') {
                out.append(c)
                i += 1
                continue
            }
            i += 1
            if (i >= value.length) break
            when (val escaped = value[i]) {
                'x' -> {
                    val hex = value.substring(i + 1, minOf(i + 3, value.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: ' ')
                    i += 3
                }
                'u' -> {
                    val hex = value.substring(i + 1, minOf(i + 5, value.length))
                    out.append(hex.toIntOrNull(16)?.toChar() ?: ' ')
                    i += 5
                }
                else -> {
                    out.append(
                        when (escaped) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'v' -> '\u000B'
                            else -> escaped
                        },
                    )
                    i += 1
                }
            }
        }
        return out.toString()
    }

    private fun unpack(payload: String, radix: Int, count: Int, keywords: String): String {
        val dictionary = HashMap<String, String>()
        val words = decodePacked(keywords).split('|')
        for (index in 0 until count) {
            val key = encodeBase(index, radix)
            dictionary[key] = words.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: key
        }
        return decodePacked(payload).replace(WORD_PATTERN) { match ->
            dictionary[match.value] ?: match.value
        }
    }

    private val PACKED_PATTERN = Regex(
        "eval\\(function\\(p,a,c,k,e,d\\)[\\s\\S]*?\\}\\('((?:\\\\.|[^'\\\\])*)',(\\d+),(\\d+),'((?:\\\\.|[^'\\\\])*)'\\.split\\('\\|'\\)",
    )
    private val URL_PATTERN = Regex("https?://[^\\s\"'<>\\\\]+")
    private val WORD_PATTERN = Regex("\\b\\w+\\b")
    private val INTERNAL_ID = Regex("/api/items/([a-z0-9]+)/view", RegexOption.IGNORE_CASE)
    private val THUMBNAIL_BLOCK = Regex("\\bthumbnail\\s*:\\s*\\{([\\s\\S]*?)\\}")
    private val ENABLED = Regex("\\benabled\\s*:\\s*(true|false)")
    private val URLS = Regex("\\burls\\s*:\\s*(\\[[\\s\\S]*?])")
}
