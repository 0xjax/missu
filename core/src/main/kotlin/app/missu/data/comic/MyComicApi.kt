package app.missu.data.comic

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// NOTE: 列表与详情由服务端直出 HTML；章节在 x-data 的 JSON 数组里，阅读页直出图片。
class MyComicApi(
    private val client: ComicHttpClient,
) : ComicSourceApi {
    override val source = ComicSource.MYCOMIC

    override suspend fun getComics(feed: ComicFeed, page: Int, category: String?): ComicListPage {
        val sort = if (feed == ComicFeed.POPULAR) "-views" else "-update"
        val url = source.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("comics")
            .addQueryParameter("sort", sort)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
            .toString()
        return parseList(document(url), page)
    }

    override suspend fun search(query: String, page: Int): ComicListPage {
        val url = source.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("comics")
            .addQueryParameter("q", query)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
            .toString()
        return parseList(document(url), page)
    }

    override suspend fun getDetail(id: String): ComicDetail {
        val document = document("/comics/$id")
        val detail = document.selectFirst("div[data-flux-card]")
        val chapters: List<ComicChapter> = document.select("div[data-flux-card] + div div[x-data]")
            .flatMap { parseChapters(it) }
            .asReversed()
        val metadataGroups = detail?.selectFirst("div[data-flux-badge] + div")?.children().orEmpty()
        return ComicDetail(
            id = id,
            title = detail?.selectFirst("div[data-flux-heading]")?.text().orEmpty(),
            cover = detail?.selectFirst("img.object-cover")?.imageUrl()?.let { ComicImage(it, source.baseUrl) },
            authors = metadataGroups.firstOrNull()?.select("a")
                ?.map { it.text() }?.filter { it.isNotBlank() }?.distinct().orEmpty(),
            description = detail?.selectFirst("div[data-flux-badge] + div + div div[x-show=show]")?.text()
                ?: document.selectFirst("meta[name=description]")?.attr("content"),
            status = detail?.selectFirst("div[data-flux-badge]")?.text(),
            tags = metadataGroups.drop(1).flatMap { group ->
                group.select("a").map { it.text() }
            }.filter { it.isNotBlank() }.distinct(),
            chapters = chapters,
        )
    }

    override suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage> {
        val document = document("/chapters/$chapterId")
        return document.select("img[x-ref]").mapNotNull { image ->
            image.imageUrl()?.let { ComicImage(it, source.baseUrl) }
        }
    }

    private fun parseList(document: Document, page: Int): ComicListPage {
        val items = document.select("div.grid > div.group").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val id = link.attr("href").substringAfter("/comics/", "").substringBefore('/')
                .takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val image = element.selectFirst("img")
            ComicSummary(
                id = id,
                title = image?.attr("alt")?.ifBlank { link.text() }.orEmpty(),
                cover = image?.imageUrl()?.let { ComicImage(it, source.baseUrl) },
            )
        }
        val next = document.selectFirst("nav[role=navigation] a[rel=next]") != null
        return ComicListPage(items, if (next) page + 1 else null)
    }

    private fun parseChapters(element: Element): List<ComicChapter> {
        val data = element.attr("x-data")
        return CHAPTER_REGEX.findAll(data).flatMap { match ->
            match.value.toJsonElement().array()?.mapNotNull { item ->
                val chapter = item.obj() ?: return@mapNotNull null
                val id = chapter["id"].text() ?: chapter["id"].int()?.toString() ?: return@mapNotNull null
                ComicChapter(id, chapter["title"].text().orEmpty())
            }.orEmpty()
        }.toList()
    }

    private suspend fun document(path: String): Document {
        val url = if (path.startsWith("https://")) path else source.baseUrl + path
        return Jsoup.parse(client.get(url, referer = source.baseUrl), url)
    }

    private fun Element.imageUrl(): String? =
        (if (hasAttr("data-src")) absUrl("data-src") else absUrl("src")).takeIf { it.isNotBlank() }

    private companion object {
        val CHAPTER_REGEX = Regex("(?<=chapters: )\\[\\{.*?\\}]", RegexOption.DOT_MATCHES_ALL)
    }
}
