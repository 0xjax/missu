package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页解析的离线验证：用与站点同构的最小 HTML 校验选择器契约。
 * 真实结构见 legacy `src/api/parse.ts` 的注释；离线用例改这里，真实抓取见 LiveHomeTest。
 */
class HomeParserTest {

    private val fixture = """
        <html><body>
          <div class="section">
            <div class="row">
              <div class="title-row"><h2>最近更新</h2></div>
              <a href="/cn/new">更多</a>
            </div>
            <div class="card">
              <div class="thumbnail group">
                <a href="/cn/abc-123"><img data-src="//img.example.com/a.jpg"></a>
                <span class="absolute bottom-1 left-1">中文字幕</span>
                <span class="absolute bottom-1 right-1">1:23</span>
              </div>
              <a class="text-secondary">Title A</a>
            </div>
            <div class="card">
              <div class="thumbnail group">
                <a href="/cn/def-456"><img src="https://img.example.com/b.jpg"></a>
                <span class="absolute bottom-1 right-1">2:00:00</span>
              </div>
              <a class="text-secondary">Title B</a>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesSectionAndCards() {
        val sections = HomeParser.parseHome(fixture)

        assertEquals(1, sections.size)
        val section = sections.single()
        assertEquals("new", section.id)
        assertEquals("最近更新", section.title)
        assertEquals("/cn/new", section.morePath)
        assertEquals(2, section.videos.size)
    }

    @Test
    fun parsesCardFields() {
        val first = HomeParser.parseHome(fixture).single().videos.first()

        assertEquals("abc-123", first.id)
        assertEquals("Title A", first.title)
        assertTrue("pageUrl 应为绝对地址: ${first.pageUrl}", first.pageUrl.startsWith("https://missav.ai/"))
        assertTrue("缩略图应补全协议: ${first.thumbnailUrl}", first.thumbnailUrl!!.startsWith("https://img.example.com/a.jpg"))
        assertEquals("1:23", first.duration)
        assertEquals(listOf("中文字幕"), first.badges)
    }

    @Test
    fun ignoresCardsWithoutSectionTitleOrMoreLink() {
        val html = """
            <html><body><div><h2>无关标题</h2><div class="thumbnail group">
              <a href="/cn/xyz-1"><img data-src="//i/x.jpg"></a>
            </div></div></body></html>
        """.trimIndent()

        assertTrue("没有可识别的板块 id 时不应产出板块", HomeParser.parseHome(html).isEmpty())
    }
}
