package app.missu.data.comic

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// NOTE: 网页列表、搜索与阅读页均直出 HTML；复用 Cloudflare 验证，不引入移动端的签名与 AES 协议。
class JmicComicApi(
    private val client: ComicHttpClient,
) : ComicSourceApi {
    override val source = ComicSource.JMIC

    override suspend fun getComics(feed: ComicFeed, page: Int): ComicListPage {
        val order = if (feed == ComicFeed.POPULAR) "mv" else "mr"
        val url = source.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("albums")
            .addQueryParameter("o", order)
            .addQueryParameter("page", page.toString())
            .build()
            .toString()
        return parseList(document(url), page)
    }

    override suspend fun search(query: String, page: Int): ComicListPage {
        val url = source.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment("photos")
            .addQueryParameter("search_query", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("o", "mr")
            .addQueryParameter("t", "a")
            .build()
            .toString()
        return parseList(document(url), page)
    }

    override suspend fun getDetail(id: String): ComicDetail {
        val document = document("/album/$id")
        val chapters = document.select("[data-album], a[href^=/photo/]").mapNotNull { element ->
            val chapterId = element.attr("data-album").takeIf { it.isNotBlank() }
                ?: PHOTO_ID.find(element.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = element.text().trim().ifBlank { "第 $chapterId 话" }
            ComicChapter(chapterId, title)
        }.distinctBy { it.id }
        return ComicDetail(
            id = id,
            title = document.selectFirst("#book-name")?.text()
                ?: BOOK_NAME.find(document.html())?.groupValues?.get(1)?.trim().orEmpty(),
            cover = ComicImage(coverUrl(id), source.baseUrl),
            authors = document.select("span[itemprop=author][data-type=author] a").map { it.text() },
            description = DESCRIPTION.find(document.html())?.groupValues?.get(1)?.trim(),
            tags = document.select("span[itemprop=genre][data-type=tags] a").map { it.text() },
            chapters = chapters.ifEmpty { listOf(ComicChapter(id, document.selectFirst("#book-name")?.text().orEmpty())) },
        )
    }

    override suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage> {
        val html = client.get("${source.baseUrl}/photo/$chapterId", referer = source.baseUrl)
        val images = PAGE_ARRAY.find(html)?.groupValues?.get(1)?.toJsonElement()?.array()
            ?.mapNotNull { it.text() }.orEmpty()
        val domain = IMAGE_DOMAIN.find(html)?.groupValues?.get(1) ?: FALLBACK_IMAGE_HOST
        val scrambleId = SCRAMBLE_ID.find(html)?.groupValues?.get(1)?.toLongOrNull()
        return images.map { name ->
            ComicImage(
                url = "https://$domain/media/photos/$chapterId/$name",
                referer = "${source.baseUrl}/photo/$chapterId",
                scrambleId = scrambleId,
            )
        }
    }

    private fun parseList(document: Document, page: Int): ComicListPage {
        val items = document.select("a[href^=/album/]").mapNotNull { link ->
            val id = ALBUM_ID.find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val title = link.attr("title").trim().ifBlank {
                link.selectFirst(".video-title, .title-truncate")?.text()?.trim().orEmpty()
            }
            if (title.isBlank()) return@mapNotNull null
            ComicSummary(
                id = id,
                title = title,
                cover = ComicImage(coverUrl(id), source.baseUrl),
                subtitle = "JM$id",
            )
        }.distinctBy { it.id }
        val total = TOTAL.find(document.html())?.groupValues?.get(1)?.toIntOrNull()
        val hasNext = items.isNotEmpty() && (total == null || page * PAGE_SIZE < total)
        return ComicListPage(items, if (hasNext) page + 1 else null)
    }

    private suspend fun document(path: String): Document {
        val url = source.baseUrl + path
        return Jsoup.parse(client.get(url, referer = source.baseUrl), url)
    }

    private fun coverUrl(id: String) = "https://$FALLBACK_IMAGE_HOST/media/albums/${id}_3x4.jpg"

    private companion object {
        const val FALLBACK_IMAGE_HOST = "cdn-msp.jmapiproxy1.cc"
        const val PAGE_SIZE = 80
        val ALBUM_ID = Regex("/album/(\\d+)")
        val PHOTO_ID = Regex("/photo/(\\d+)")
        val BOOK_NAME = Regex("id=\"book-name\"[^>]*>([\\s\\S]*?)<")
        val DESCRIPTION = Regex("[叙|敘]述：([\\s\\S]*?)</h2>")
        val TOTAL = Regex("class=\"text-white\">(\\d+)\\+?</span>")
        val PAGE_ARRAY = Regex("var\\s+page_arr\\s*=\\s*(.*?);", RegexOption.DOT_MATCHES_ALL)
        val IMAGE_DOMAIN = Regex("src=\"https://(.*?)/media/albums/blank")
        val SCRAMBLE_ID = Regex("var\\s+scramble_id\\s*=\\s*(\\d+);")
    }
}
