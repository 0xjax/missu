package app.missu.data.comic

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// NOTE: 站点没有公开 JSON 列表接口；页面直出卡片、详情、章节与水合图片数组。
class RoumanComicApi(
    private val client: ComicHttpClient,
) : ComicSourceApi {
    override val source = ComicSource.ROUMAN

    override suspend fun getComics(feed: ComicFeed, page: Int): ComicListPage {
        if (feed == ComicFeed.LATEST) {
            val document = document("/home")
            val items = document.selectFirst("div.site-home")?.children().orEmpty()
                .filter { it.selectFirst(".site-section-heading")?.text()?.contains("最近更新") == true }
                .flatMap(::parseEntries)
                .distinctBy { it.id }
            return ComicListPage(items)
        }
        val path = "/books?page=${page - 1}"
        return parseList(document(path), page)
    }

    override suspend fun search(query: String, page: Int): ComicListPage {
        val url = source.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("term", query)
            .addQueryParameter("page", (page - 1).toString())
            .build()
            .toString()
        return parseList(document(url), page)
    }

    override suspend fun getDetail(id: String): ComicDetail {
        val document = document("/books/$id")
        val info = document.selectFirst("div.site-book-info")
        val data = info?.select("dl.site-book-data dt").orEmpty().associate { dt ->
            dt.text() to dt.nextElementSibling()?.text().orEmpty()
        }
        val chapters = document.select("a.site-chapter-link").mapNotNull { link ->
            val path = link.attr("href").takeIf { it.startsWith("/books/") } ?: return@mapNotNull null
            ComicChapter(
                id = path,
                title = link.selectFirst("span")?.attr("title")?.ifBlank { link.text() }.orEmpty(),
            )
        }
        val tags = buildList {
            data["地區"]?.takeIf { it.isNotBlank() }?.let(::add)
            info?.selectFirst("p.site-eyebrow")?.text()
                ?.substringBefore("/")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return ComicDetail(
            id = id,
            title = info?.selectFirst("h1")?.text().orEmpty(),
            cover = document.selectFirst("img.site-detail-cover")?.absUrl("src")
                ?.let { ComicImage(it, source.baseUrl) },
            authors = data["作者"]?.split(Regex("[、,/]"))?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty(),
            description = document.selectFirst("div.site-book-synopsis")?.text(),
            status = data["狀態"],
            tags = tags,
            chapters = chapters,
        )
    }

    override suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage> {
        val body = client.get(absolute(chapterId), referer = source.baseUrl)
        val marker = body.indexOf("imagePaths:")
        val arrayStart = if (marker >= 0) body.indexOf("=[", marker) + 1 else -1
        val arrayEnd = if (arrayStart > 0) body.indexOf(']', arrayStart) else -1
        if (arrayEnd < 0) return emptyList()
        return body.substring(arrayStart, arrayEnd + 1).toJsonElement().array()
            ?.mapNotNull { it.text() }
            ?.map { ComicImage(it, absolute(chapterId)) }
            .orEmpty()
    }

    private fun parseList(document: Document, page: Int): ComicListPage {
        val pages = document.selectFirst(".site-pagination-mobile")?.text()?.split('/').orEmpty()
        val total = pages.getOrNull(1)?.trim()?.toIntOrNull()
        return ComicListPage(
            items = parseEntries(document),
            nextPage = if (total != null && page < total) page + 1 else null,
        )
    }

    private fun parseEntries(root: org.jsoup.nodes.Element): List<ComicSummary> =
        root.select("a.site-comic").mapNotNull { card ->
            val id = card.attr("href").removePrefix("/books/")
                .takeIf { it.isNotBlank() && !it.contains('/') } ?: return@mapNotNull null
            ComicSummary(
                id = id,
                title = card.selectFirst("h3")?.text().orEmpty(),
                cover = card.selectFirst("img")?.absUrl("src")?.let { ComicImage(it, source.baseUrl) },
                subtitle = card.selectFirst(".site-comic-chapter")?.text(),
            )
        }

    private suspend fun document(url: String): Document = Jsoup.parse(client.get(absolute(url), referer = source.baseUrl), absolute(url))

    private fun absolute(path: String): String =
        if (path.startsWith("https://")) path else source.baseUrl + path
}
