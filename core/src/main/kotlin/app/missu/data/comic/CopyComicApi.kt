package app.missu.data.comic

import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class CopyComicApi(
    private val client: ComicHttpClient,
) : ComicSourceApi {
    override val source = ComicSource.COPY

    override suspend fun getComics(feed: ComicFeed, page: Int): ComicListPage {
        val ordering = if (feed == ComicFeed.POPULAR) "-popular" else "-datetime_updated"
        val url = apiUrl("/api/v3/comics", page) {
            addQueryParameter("ordering", ordering)
        }
        return parseList(client.get(url, referer = source.baseUrl, accept = "application/json"))
    }

    override suspend fun search(query: String, page: Int): ComicListPage {
        val url = apiUrl("/api/v3/search/comic", page) {
            addQueryParameter("q", query)
            addQueryParameter("q_type", "")
        }
        return parseList(client.get(url, referer = source.baseUrl, accept = "application/json"))
    }

    override suspend fun getDetail(id: String): ComicDetail {
        val root = client.get(
            apiUrl("/api/v3/comic2/$id"),
            referer = source.baseUrl,
            accept = "application/json",
        ).toJsonElement().obj().orEmpty()
        val results = root.at("results").obj().orEmpty()
        val comic = results.at("comic").obj().orEmpty()
        val groups = results.at("groups").obj().orEmpty()
        val group = groups["default"].obj()
            ?: groups.values.mapNotNull { it.obj() }.maxByOrNull { it["count"].int() ?: 0 }
        val chapters = mutableListOf<ComicChapter>()
        if (group != null) {
            val groupPath = group["path_word"].text().orEmpty()
            val count = group["count"].int() ?: 0
            var offset = 0
            do {
                val url = apiUrl("/api/v3/comic/$id/group/$groupPath/chapters") {
                    addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
                    addQueryParameter("offset", offset.toString())
                }
                val page = client.get(url, referer = source.baseUrl, accept = "application/json")
                    .toJsonElement().obj().orEmpty()
                val items = page.listAt("results", "list")
                items.mapNotNull { item ->
                    val chapter = item.obj() ?: return@mapNotNull null
                    val uuid = chapter["uuid"].text() ?: return@mapNotNull null
                    ComicChapter(
                        id = uuid,
                        title = chapter["name"].text().orEmpty(),
                    )
                }.let(chapters::addAll)
                offset += items.size
            } while (itemsIsFull(items.size) && offset < count)
        }
        return ComicDetail(
            id = id,
            title = comic["name"].text().orEmpty(),
            cover = comic["cover"].text()?.let { ComicImage(it, source.baseUrl) },
            authors = comic["author"].array()?.mapNotNull { it.obj()?.get("name").text() }.orEmpty(),
            description = comic["brief"].text(),
            status = comic.at("status", "display").text(),
            tags = comic["theme"].array()?.mapNotNull { it.obj()?.get("name").text() }.orEmpty(),
            chapters = chapters,
        )
    }

    override suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage> {
        val root = client.get(
            apiUrl("/api/v3/comic/$mangaId/chapter/$chapterId"),
            referer = source.baseUrl,
            accept = "application/json",
        ).toJsonElement().obj().orEmpty()
        val chapter = root.at("results", "chapter").obj().orEmpty()
        val contents = chapter["contents"].array()?.mapNotNull { item ->
            item.obj()?.get("url").text()
        }.orEmpty()
        val words = chapter["words"].array()?.mapNotNull { it.int() }.orEmpty()
        val ordered = if (words.size == contents.size) {
            words.zip(contents).sortedBy { it.first }.map { it.second }
        } else {
            contents
        }
        return ordered.map { ComicImage(it, source.baseUrl) }
    }

    private fun parseList(body: String): ComicListPage {
        val results = body.toJsonElement().obj().orEmpty().at("results").obj().orEmpty()
        val items = results["list"].array()?.mapNotNull { item ->
            val comic = item.obj() ?: return@mapNotNull null
            val id = comic["path_word"].text() ?: return@mapNotNull null
            ComicSummary(
                id = id,
                title = comic["name"].text().orEmpty(),
                cover = comic["cover"].text()?.let { ComicImage(it, source.baseUrl) },
                subtitle = comic["author"].array()
                    ?.mapNotNull { it.obj()?.get("name").text() }
                    ?.joinToString(" / ")
                    ?.takeIf { it.isNotBlank() }
                    ?: comic["datetime_updated"].text(),
            )
        }.orEmpty()
        val offset = results["offset"].int() ?: 0
        val total = results["total"].int() ?: items.size
        return ComicListPage(
            items = items,
            nextPage = if (items.isNotEmpty() && offset + items.size < total) offset / PAGE_SIZE + 2 else null,
        )
    }

    private fun apiUrl(path: String, page: Int = 1, builder: HttpUrl.Builder.() -> Unit = {}): String =
        API_BASE.toHttpUrl().newBuilder()
            .encodedPath(path)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1).coerceAtLeast(0) * PAGE_SIZE).toString())
            .addQueryParameter("platform", "3")
            .apply(builder)
            .build()
            .toString()

    private fun itemsIsFull(size: Int) = size == CHAPTER_PAGE_SIZE

    private companion object {
        const val API_BASE = "https://api.2024manga.com"
        const val PAGE_SIZE = 18
        const val CHAPTER_PAGE_SIZE = 100
    }
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
