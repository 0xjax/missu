package app.missu.data.comic

enum class ComicSource(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val supportsLatest: Boolean = true,
) {
    COPY("copy", "拷贝漫画", "https://www.mangacopy.com"),
    ROUMAN("rouman", "肉漫屋", "https://rouman5.com"),
    JMIC("jmic", "18Comic", "https://18comic.ink"),
    KALAM("kalam", "卡拉漫画", "https://www.kalamhw.com", supportsLatest = false),
    MYCOMIC("mycomic", "MyComic", "https://mycomic.com/cn"),
    ;

    companion object {
        fun fromId(id: String): ComicSource = entries.firstOrNull { it.id == id } ?: COPY
    }
}

enum class ComicFeed(val value: String) {
    POPULAR("popular"),
    LATEST("latest"),
}

enum class KalamCategory(val value: String) {
    ALL("All"),
    KOREAN("Kc"),
    JAPANESE("Jc"),
    CHINESE("Cc"),
    WESTERN("Ec"),
    OTHER("Oc"),
}

data class ComicImage(
    val url: String,
    val referer: String? = null,
    val scrambleId: Long? = null,
)

data class ComicSummary(
    val id: String,
    val title: String,
    val cover: ComicImage? = null,
    val subtitle: String? = null,
)

data class ComicListPage(
    val items: List<ComicSummary>,
    val nextPage: Int? = null,
)

data class ComicChapter(
    val id: String,
    val title: String,
)

data class ComicDetail(
    val id: String,
    val title: String,
    val cover: ComicImage? = null,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    val chapters: List<ComicChapter> = emptyList(),
)

interface ComicSourceApi {
    val source: ComicSource

    suspend fun getComics(feed: ComicFeed, page: Int, category: String? = null): ComicListPage

    suspend fun search(query: String, page: Int): ComicListPage

    suspend fun getDetail(id: String): ComicDetail

    suspend fun getChapterPages(mangaId: String, chapterId: String): List<ComicImage>
}

class ComicRepository(sources: List<ComicSourceApi>) {
    val available: List<ComicSourceApi> = sources.distinctBy { it.source }
    private val sources = available.associateBy { it.source }

    operator fun get(source: ComicSource): ComicSourceApi =
        sources.getValue(source)
}
