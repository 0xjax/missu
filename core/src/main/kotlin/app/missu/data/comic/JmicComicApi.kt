package app.missu.data.comic

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

// NOTE: 网页列表、搜索与阅读页均直出 HTML；复用 Cloudflare 验证，不引入移动端的签名与 AES 协议。
class JmicComicApi(
    private val client: ComicHttpClient,
) : ComicSourceApi {
    override val source = ComicSource.JMIC

    override suspend fun getComics(feed: ComicFeed, page: Int, category: String?): ComicListPage {
        val order = if (feed == ComicFeed.POPULAR) "mv" else "mr"
        return parseList(document("/albums", mapOf("o" to order, "page" to page.toString())), page)
    }

    override suspend fun search(query: String, page: Int): ComicListPage {
        return parseList(
            document(
                "/search/photos",
                mapOf(
                    "search_query" to query,
                    "page" to page.toString(),
                    "o" to "mr",
                    "t" to "a",
                ),
            ),
            page,
        )
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
            authors = document.select("span[itemprop=author][data-type=author] > a[name=vote_]")
                .map { it.text() }
                .distinct(),
            description = DESCRIPTION.find(document.html())?.groupValues?.get(1)?.trim(),
            tags = document.select("span[itemprop=genre][data-type=tags] > a[name=vote_]")
                .map { it.text() }
                .distinct(),
            chapters = chapters.ifEmpty { listOf(ComicChapter(id, document.selectFirst("#book-name")?.text().orEmpty())) },
        )
    }

    override suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage> {
        val (html, pageUrl) = fetch("/photo/$chapterId")
        val images = PAGE_ARRAY.find(html)?.groupValues?.get(1)?.toJsonElement()?.array()
            ?.mapNotNull { it.text() }.orEmpty()
        val domain = IMAGE_DOMAIN.find(html)?.groupValues?.get(1) ?: FALLBACK_IMAGE_HOST
        val scrambleId = SCRAMBLE_ID.find(html)?.groupValues?.get(1)?.toLongOrNull()
        return images.map { name ->
            ComicImage(
                url = "https://$domain/media/photos/$chapterId/$name",
                referer = pageUrl,
                scrambleId = scrambleId,
            )
        }
    }

    private fun parseList(document: Document, page: Int): ComicListPage {
        val items = document.select("div.thumb-overlay-albums > a[href^=/album/]").mapNotNull { link ->
            val id = ALBUM_ID.find(link.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
            val image = link.selectFirst("img") ?: return@mapNotNull null
            val title = image.attr("title").trim().ifBlank { image.attr("alt").trim() }
            if (title.isBlank()) return@mapNotNull null
            ComicSummary(
                id = id,
                title = title,
                cover = ComicImage(image.absUrl("src").ifBlank { coverUrl(id) }, source.baseUrl),
                subtitle = "JM$id",
            )
        }.distinctBy { it.id }
        val hasNext = document.selectFirst("link[rel=next]") != null
        return ComicListPage(items, if (hasNext) page + 1 else null)
    }

    private suspend fun document(path: String, params: Map<String, String> = emptyMap()): Document {
        val (body, url) = fetch(path, params)
        return Jsoup.parse(body, url)
    }

    private suspend fun fetch(path: String, params: Map<String, String> = emptyMap()): Pair<String, String> {
        var lastNetworkError: IOException? = null
        MIRRORS.forEach { baseUrl ->
            val url = baseUrl.toHttpUrl().newBuilder()
                .encodedPath(path)
                .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
                .toString()
            try {
                return client.get(url, referer = "$baseUrl/") to url
            } catch (error: IOException) {
                lastNetworkError = error
            }
        }
        lastNetworkError?.let { throw it }
        throw IOException("18Comic mirrors unavailable")
    }

    private fun coverUrl(id: String) = "https://$FALLBACK_IMAGE_HOST/media/albums/${id}_3x4.jpg"

    private companion object {
        val MIRRORS = listOf(
            "https://18comic.ink",
            "https://jmcomic-zzz.one",
            "https://18comic.vip",
        )
        const val FALLBACK_IMAGE_HOST = "cdn-msp.jmapiproxy1.cc"
        val ALBUM_ID = Regex("/album/(\\d+)")
        val PHOTO_ID = Regex("/photo/(\\d+)")
        val BOOK_NAME = Regex("id=\"book-name\"[^>]*>([\\s\\S]*?)<")
        val DESCRIPTION = Regex("[叙|敘]述：([\\s\\S]*?)</h2>")
        val PAGE_ARRAY = Regex("var\\s+page_arr\\s*=\\s*(.*?);", RegexOption.DOT_MATCHES_ALL)
        val IMAGE_DOMAIN = Regex("src=\"https://(.*?)/media/albums/blank")
        val SCRAMBLE_ID = Regex("var\\s+scramble_id\\s*=\\s*(\\d+);")
    }
}
