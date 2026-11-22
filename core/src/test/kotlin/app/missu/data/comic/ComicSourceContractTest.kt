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
import java.io.IOException

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
    fun roumanKeepsSiteAscendingChapters() = runBlocking {
        val api = RoumanComicApi(client(ROUMAN_DETAIL))
        val detail = api.getDetail("cmvvbj1kw01mib407zapem6t2")

        assertEquals(listOf("第1話", "第2話", "第3話"), detail.chapters.map { it.title })
        assertEquals("/books/cmvvbj1kw01mib407zapem6t2/0", detail.chapters.first().id)
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
    fun myComicReadsChaptersInAscendingOrder() = runBlocking {
        val api = MyComicApi(client(MYCOMIC_DETAIL))
        val detail = api.getDetail("123")

        assertEquals(listOf("第1话", "第2话", "第3话"), detail.chapters.map { it.title })
        assertEquals("1", detail.chapters.first().id)
    }

    @Test
    fun jmicReadsRealAlbumCards() = runBlocking {
        val api = JmicComicApi(client(JMIC_LIST))
        val page = api.getComics(ComicFeed.POPULAR, 1)

        assertEquals("123456", page.items.single().id)
        assertEquals("示例漫画", page.items.single().title)
        assertEquals("JM123456", page.items.single().subtitle)
        assertTrue(page.items.single().cover?.url?.startsWith("https://cdn-msp3.18comic.ink/") == true)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun jmicIgnoresUserAddedDetailMetadata() = runBlocking {
        val api = JmicComicApi(client(JMIC_DETAIL))
        val detail = api.getDetail("123456")

        assertEquals(listOf("美娜讚", "钢铁王"), detail.authors)
        assertEquals(listOf("韓漫", "連載中"), detail.tags)
        assertEquals("真实简介", detail.description)
    }

    @Test
    fun jmicFallsBackToAnotherOfficialMirrorAfterNetworkFailure() = runBlocking {
        val api = JmicComicApi(client { url ->
            if (url.contains("18comic.ink")) throw IOException("offline") else JMIC_LIST
        })
        val page = api.getComics(ComicFeed.POPULAR, 1)

        assertEquals("123456", page.items.single().id)
        assertEquals(2, page.nextPage)
    }

    @Test
    fun kalamReadsDedicatedListAndNextPage() = runBlocking {
        val api = KalamComicApi(client(KALAM_LIST))
        val page = api.getComics(ComicFeed.POPULAR, 1)

        assertEquals("122410", page.items.single().id)
        assertEquals("漆黑魔法师", page.items.single().title)
        assertEquals(2, page.nextPage)
        assertTrue(page.items.single().cover?.url?.startsWith("https://image.kalaimg.top/image/cover/") == true)
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

    private fun client(body: String): ComicHttpClient = client { body }

    private fun client(responder: (String) -> String): ComicHttpClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val body = responder(chain.request().url.toString())
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

        val ROUMAN_DETAIL = """
            <div class="site-book-info"><h1>姨見鍾情</h1></div>
            <div class="site-chapters">
              <a href="/books/cmvvbj1kw01mib407zapem6t2/0" class="site-chapter-link"><span title="第1話">第1話</span></a>
              <a href="/books/cmvvbj1kw01mib407zapem6t2/1" class="site-chapter-link"><span title="第2話">第2話</span></a>
              <a href="/books/cmvvbj1kw01mib407zapem6t2/2" class="site-chapter-link"><span title="第3話">第3話</span></a>
            </div>
        """.trimIndent()

        val MYCOMIC_LIST = """
            <div class="grid"><div class="group"><a href="/cn/comics/123">
              <img data-src="https://mycomic.com/cover.jpg" alt="一拳超人">
            </a></div></div><nav role="navigation"><a rel="next" href="/cn/comics?page=2">下一页</a></nav>
        """.trimIndent()

        val MYCOMIC_DETAIL = """
            <div data-flux-card><div data-flux-heading>一拳超人</div></div>
            <div><div x-data="{chapters: [
              {&quot;id&quot;: 3, &quot;title&quot;: &quot;第3话&quot;},
              {&quot;id&quot;: 2, &quot;title&quot;: &quot;第2话&quot;},
              {&quot;id&quot;: 1, &quot;title&quot;: &quot;第1话&quot;}
            ]}"></div></div>
        """.trimIndent()

        val JMIC_LIST = """
            <html><head><link rel="next" href="https://18comic.ink/albums?o=mv&amp;page=2"></head><body>
              <div class="list-col"><div class="thumb-overlay-albums">
                <a href="/album/123456/示例漫画">
                  <img src="https://cdn-msp3.18comic.ink/media/albums/123456_3x4.jpg?u=1795188452"
                    title="示例漫画" alt="示例漫画" class="lazy_img img-responsive img-rounded">
                </a>
              </div>
              <span class="video-title title-truncate m-t-5">示例漫画</span></div>
            </body></html>
        """.trimIndent()

        val JMIC_DETAIL = """
            <h1 id="book-name">秘密教学</h1>
            <h2>敘述：真实简介</h2>
            <span itemprop="author" data-type="author">
              <a name="vote_" class="phone-author-tag">美娜讚</a>
              <a name="vote_" class="phone-author-tag">钢铁王</a>
              <a name="vote_267050" class="phone-author-tag">用户追加的作者评论</a>
            </span>
            <span itemprop="genre" data-type="tags">
              <a name="vote_" class="phone-tags-tag">韓漫</a>
              <a name="vote_" class="phone-tags-tag">連載中</a>
              <a name="vote_229910" class="phone-tags-tag">用户追加的分类评论</a>
            </span>
        """.trimIndent()

        val KALAM_LIST = """
            <ul class="stui-vodlist clearfix"><li><div class="stui-vodlist__box">
              <a class="stui-vodlist__thumb lazyload" href="/comic/122410.html" title="漆黑魔法师"
                data-original="https://image.kalaimg.top/image/cover/63fb4d30.jpg">
                <span class="pic-text">连载中,第21话</span>
              </a>
            </div></li></ul>
            <ul class="stui-page__item"><li><a href="/list/All/page/2.html">下一页</a></li></ul>
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
