package app.missu.data.comic

import app.missu.data.api.MemoryCookieJar
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicSourceContractTest {
    @Test
    fun copyReadsRealJsonList() = runBlocking {
        val api = CopyComicApi(client(COPY_LIST))
        val page = api.getComics(ComicFeed.POPULAR, 1)

        assertEquals(2, page.items.size)
        assertEquals(2, page.nextPage)
        assertEquals("yiquanchaoren", page.items[1].id)
        assertEquals("村田雄介 / ONE", page.items[1].subtitle)
    }

    @Test
    fun roumanReadsRealCardsAndPagination() = runBlocking {
        val api = RoumanComicApi(client(ROUMAN_LIST))
        val page = api.getComics(ComicFeed.POPULAR, 1)

        assertEquals("cmvvbj1kw01mib407zapem6t2", page.items.single().id)
        assertEquals("姨見鍾情", page.items.single().title)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun roumanReadsHydratedImagePaths() = runBlocking {
        val api = RoumanComicApi(client(ROUMAN_READER))
        val pages = api.getChapterPages("book", "/books/book/0")

        assertEquals(2, pages.size)
        assertTrue(pages[1].url.contains("/sr:1/"))
    }

    @Test
    fun myComicReadsRealCards() = runBlocking {
        val api = MyComicApi(client(MYCOMIC_LIST))
        val page = api.search("一拳", 1)

        assertEquals("123", page.items.single().id)
        assertEquals("一拳超人", page.items.single().title)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun jmicReadsRealAlbumCards() = runBlocking {
        val api = JmicComicApi(client(JMIC_LIST))
        val page = api.getComics(ComicFeed.POPULAR, 1)

        assertEquals("123456", page.items.single().id)
        assertEquals("JM123456", page.items.single().subtitle)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun kalamUnwrapsRealRawHtmlAndReadsChapters() = runBlocking {
        val wrapped = "<script>var rawHtml = ${JsonPrimitive(KALAM_DETAIL)}; " +
            "var cleanHtml = rawHtml; doc.write(cleanHtml);</script>"
        val api = KalamComicApi(client(wrapped))
        val detail = api.getDetail("67213")

        assertEquals("海贼王", detail.title)
        assertEquals(listOf("1卷", "2卷"), detail.chapters.map { it.title })
        assertEquals("/chapter/67213-2-1.html", detail.chapters.first().id)
    }

    private fun client(body: String): ComicHttpClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }.build()
        return ComicHttpClient(http, MemoryCookieJar())
    }

    private companion object {
        val COPY_LIST = """
            {"code":200,"results":{"total":30,"limit":18,"offset":0,"list":[
              {"name":"魔都精兵的奴隸","path_word":"modujingbingdenuli","author":[{"name":"竹村洋平"}],"cover":"https://sm.mangafunb.fun/cover.jpg"},
              {"name":"一拳超人","path_word":"yiquanchaoren","author":[{"name":"村田雄介"},{"name":"ONE"}],"cover":"https://sy.mangafunb.fun/cover.jpg"}
            ]}}
        """.trimIndent()

        val ROUMAN_LIST = """
            <div class="site-comic-grid"><a class="site-comic" href="/books/cmvvbj1kw01mib407zapem6t2">
              <div class="site-comic-cover"><img src="https://towm85.xyz/cover.jpg" alt="姨見鍾情"></div>
              <h3 title="姨見鍾情">姨見鍾情</h3><span class="site-comic-chapter">第11話</span>
            </a></div><span class="site-pagination-mobile">1<!-- --> / <!-- -->2</span>
        """.trimIndent()

        val ROUMAN_READER = """
            <script>imagePaths:${'$'}R[25]=["https://v1.kelv47.xyz/m/a/wm:0/sr:0/a.jpg",
              "https://v2.kelv47.xyz/m/b/wm:2/sr:1/b.jpg"]</script>
        """.trimIndent()

        val MYCOMIC_LIST = """
            <div class="grid"><div class="group"><a href="/cn/comics/123">
              <img data-src="https://mycomic.com/cover.jpg" alt="一拳超人">
            </a></div></div><nav role="navigation"><a rel="next" href="/cn/comics?page=2">下一页</a></nav>
        """.trimIndent()

        val JMIC_LIST = """
            <div class="well well-sm"><a href="/album/123456/" title="示例漫画">
              <div class="title-truncate">示例漫画</div></a>
              <span class="text-white">100</span>
            </div>
        """.trimIndent()

        val KALAM_DETAIL = """
            <html><body><h1 class="title">海贼王</h1>
              <div class="stui-content__thumb"><img data-original="https://image.kalaimg.top/image/cover/a.jpg"></div>
              <span class="detail-content">简介</span>
              <div id="playlist2"><ul class="stui-content__playlist">
                <li><a href="/chapter/67213-2-1.html">1卷</a></li>
                <li><a href="/chapter/67213-2-2.html">2卷</a></li>
              </ul></div>
            </body></html>
        """.trimIndent()
    }
}
