package app.missu.data.comic

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// NOTE: 该站是 MacCMS 风格 HTML，没有 JSON 列表接口；部分响应把正文放在 rawHtml JSON 字符串中。
class KalamComicApi(
    private val client: ComicHttpClient,
) : ComicSourceApi {
    override val source = ComicSource.KALAM

    override suspend fun getComics(feed: ComicFeed, page: Int): ComicListPage =
        ComicListPage(parseList(document("/")))

    override suspend fun search(query: String, page: Int): ComicListPage {
        val builder = source.baseUrl.toHttpUrl().newBuilder().addPathSegment("search")
        if (page > 1) builder.addPathSegment("page").addPathSegment(page.toString())
        val url = builder.addPathSegment("wd").addPathSegment("$query.html").build().toString()
        val document = document(url)
        val nextPath = "/search/page/${page + 1}/wd/"
        val hasNext = document.select(".stui-page__item a[href], .stui-page__all a[href], .pagination a[href]")
            .any { it.attr("href").startsWith(nextPath) }
        return ComicListPage(parseList(document), if (hasNext) page + 1 else null)
    }

    override suspend fun getDetail(id: String): ComicDetail {
        val document = document("/comic/$id.html")
        val chapterGroups = linkedMapOf<Int, MutableMap<Int, ComicChapter>>()
        document.select("[id^=playlist] a[href], .stui-content__playlist a[href]").forEach { link ->
            val path = link.attr("href")
            val match = CHAPTER_PATH.matchEntire(path) ?: return@forEach
            if (match.groupValues[1] != id) return@forEach
            val route = match.groupValues[2].toInt()
            val number = match.groupValues[3].toInt()
            chapterGroups.getOrPut(route) { linkedMapOf() }.putIfAbsent(
                number,
                ComicChapter(path, link.text().ifBlank { "第$number 话" }),
            )
        }
        val chapters = chapterGroups.values.maxByOrNull { it.size }?.values
            ?.sortedByDescending { chapter -> CHAPTER_PATH.matchEntire(chapter.id)?.groupValues?.get(3)?.toIntOrNull() }
            .orEmpty()
        return ComicDetail(
            id = id,
            title = document.selectFirst("h1.title")?.text().orEmpty(),
            cover = document.selectFirst(".stui-content__thumb img")?.cover()?.let { ComicImage(it, source.baseUrl) },
            description = document.selectFirst(".detail-content, .stui-content__desc .detail-sketch, .stui-content__desc")?.text(),
            chapters = chapters,
        )
    }

    override suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage> {
        val chapterUrl = absolute(chapterId)
        val chapterHtml = unwrap(client.get(chapterUrl, referer = source.baseUrl))
        val player = parseJsonObjectAssignment(chapterHtml, "player_aaaa")
        val address = player?.get("url").text()?.trim().orEmpty()
        val playerUrl = when {
            address.matches(Regex("[0-9a-fA-F]{32,}")) -> "$IMAGE_BASE/play/min/$address"
            address.startsWith("/play/") -> IMAGE_BASE + address
            address.startsWith("$IMAGE_BASE/play/") -> address
            else -> return emptyList()
        }
        var reader = unwrap(client.get(playerUrl, referer = chapterUrl))
        val token = if (reader.contains("window.location.replace(currentUrl.href)")) {
            LOADER_TOKEN.find(reader)?.groupValues?.get(2)
        } else {
            null
        }
        if (token != null) {
            reader = unwrap(client.get(withToken(playerUrl, token), referer = chapterUrl))
        }
        return Jsoup.parse(reader, playerUrl).select("img.comic-img").mapNotNull { image ->
            val url = sequenceOf("data-src", "src")
                .map { image.absUrl(it).trim() }
                .firstOrNull { it.startsWith("$IMAGE_BASE/image/") }
                ?: return@mapNotNull null
            val finalUrl = if (token != null) withToken(url, token) else url
            ComicImage(finalUrl, "$IMAGE_BASE/")
        }
    }

    private fun parseList(document: Document): List<ComicSummary> {
        val found = linkedMapOf<String, ComicSummary>()
        document.select("a.stui-vodlist__thumb[href]").forEach { card ->
            val id = COMIC_PATH.matchEntire(card.attr("href"))?.groupValues?.get(1) ?: return@forEach
            val title = card.attr("title").trim()
            if (title.isEmpty() || id in found) return@forEach
            found[id] = ComicSummary(id, title, card.cover()?.let { ComicImage(it, source.baseUrl) })
        }
        return found.values.toList()
    }

    private suspend fun document(path: String): Document {
        val url = absolute(path)
        return Jsoup.parse(unwrap(client.get(url, referer = source.baseUrl)), url)
    }

    private fun unwrap(body: String): String =
        if (body.contains("var cleanHtml") && body.contains("doc.write(")) {
            parseJsonStringAssignment(body, "rawHtml") ?: body
        } else {
            body
        }

    private fun Element.cover(): String? = sequenceOf("data-original", "data-src", "src")
        .map { absUrl(it).trim() }
        .firstOrNull { it.startsWith("https://") }

    private fun absolute(path: String): String = if (path.startsWith("https://")) path else source.baseUrl + path

    private fun withToken(url: String, token: String): String {
        val parsed = url.toHttpUrl()
        if (parsed.queryParameter("cf_verify") != null) return url
        return parsed.newBuilder().addQueryParameter("cf_verify", token).build().toString()
    }

    private companion object {
        const val IMAGE_BASE = "https://image.kalaimg.top"
        val COMIC_PATH = Regex("/comic/(\\d+)\\.html")
        val CHAPTER_PATH = Regex("/chapter/(\\d+)-(\\d+)-(\\d+)\\.html")
        val LOADER_TOKEN = Regex("\\b(?:var|let|const)\\s+token\\s*=\\s*(['\"])([A-Za-z0-9._-]{1,512})\\1")
    }
}
