package app.missu.data.parse

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实抓下来的搜索页验证列表解析（fixture 来源：`.local/missav-api/search.har`，
 * 抓的是 `https://missav.ai/search/SONE`，200 OK）。
 */
class SearchFixtureTest {

    private val html: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("search.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun parsesRealSearchPage() {
        val list = HomeParser.parseVideoList(html, page = 1)

        println("videos=${list.videos.size} nextPage=${list.nextPage}")
        list.videos.take(3).forEach { println("  ${it.id} | ${it.duration} | ${it.badges} | ${it.title.take(30)}") }

        assertTrue("搜索结果应非空", list.videos.isNotEmpty())
        assertTrue("应有下一页（实测搜索页带 page=2 链接），实际 ${list.nextPage}", list.nextPage == 2)
        assertTrue("id 不应带路径", list.videos.all { !it.id.contains("/") })
        assertTrue("pageUrl 必须是绝对地址", list.videos.all { it.pageUrl.startsWith("https://missav.ai/") })
    }

    @Test
    fun lastPageHasNoNext() {
        // NOTE: 用同一份 HTML 但声明当前已是最后一页的信号不存在——换个方式验证逻辑：
        // page=2 时页面若不含 page=3 链接，nextPage 应为 null。
        val list = HomeParser.parseVideoList(html, page = 99)
        assertTrue("第 99 页不应有下一页", list.nextPage == null)
    }
}
