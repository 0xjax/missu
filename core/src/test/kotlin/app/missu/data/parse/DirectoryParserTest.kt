package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目录页解析。用真实结构（真机抓到的女优条目形如「波多野结衣 5714 条影片 2008 出道」）
 * 构造 fixture，重点覆盖两条容易回归的规则：
 * 1. 计数文案随语言变化，名字必须切得出来；
 * 2. 无头像条目会先命中单字占位链接，同路径要取名字最长的那条。
 */
class DirectoryParserTest {

    private val actresses = """
        <html><body>
          <div class="grid">
            <div class="card">
              <a class="text-nord13" href="/cn/actresses/波多野结衣">
                <img data-src="//fourhoi.com/actress/hatano.jpg">
                波多野结衣 5714 条影片 2008 出道
              </a>
            </div>
            <div class="card">
              <a class="text-nord13" href="/cn/actresses/大槻响">大槻响 3616 条影片 2009 出道</a>
            </div>
            <div class="card">
              <a class="text-nord13" href="/cn/actresses/unknown">未 22 条影片</a>
            </div>
            <div class="card">
              <a class="text-nord13" href="/cn/actresses/ranking">女优排行</a>
            </div>
            <div class="card">
              <a class="text-nord13" href="/cn/genres/高清">高清 292552 条影片</a>
            </div>
            <div class="card">
              <a href="/cn/actresses/波多野结衣?page=2">下一页</a>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    @Test
    fun parsesActressesAndStripsCountSuffix() {
        val result = DirectoryParser.parse(actresses, "actresses", page = 1)

        val names = result.items.map { it.name }
        assertEquals(listOf("波多野结衣", "大槻响", "未"), names)
        // 「5714 条影片 2008 出道」在解析层就拆成条数与出道两段：UI 要分两行排。
        assertEquals("5714 条影片", result.items[0].countText)
        assertEquals("2008 出道", result.items[0].debutText)
        assertEquals(2, result.nextPage)
    }

    @Test
    fun keepsImageAbsoluteForProtocolRelativeUrl() {
        val result = DirectoryParser.parse(actresses, "actresses", page = 1)
        assertEquals("https://fourhoi.com/actress/hatano.jpg", result.items[0].imageUrl)
    }

    @Test
    fun fallsBackToParentTextWhenCountSitsOutsideTheAnchor() {
        // 真实结构：类型/厂商的锚文本只有名字，条数在父元素里（真机实测「高清 / 292596 条影片」）。
        // 这条路径曾经因为只认锚文本而让三列格子只剩名字，必须锁住。
        val html = """
            <html><body>
              <div class="card">
                <a class="text-nord13" href="/cn/genres/高清">高清</a>
                <span>292596 条影片</span>
              </div>
            </body></html>
        """.trimIndent()

        val result = DirectoryParser.parse(html, "genres", page = 1)

        assertEquals("高清", result.items[0].name)
        assertEquals("292596 条影片", result.items[0].countText)
        assertEquals(null, result.items[0].debutText)
    }

    @Test
    fun skipsRankingAndOtherKinds() {
        val result = DirectoryParser.parse(actresses, "actresses", page = 1)
        assertTrue("排行榜要排除", result.items.none { it.path.endsWith("/ranking") })
        assertTrue("别 kinds 的链接要排除", result.items.none { it.path.contains("/genres/") })
    }

    @Test
    fun prefersLongestNameForSamePath() {
        // 无头像条目站点会先渲染一个单字占位链接，规则要求同路径取名字最长的那条。
        val html = """
            <html><body>
              <a class="text-nord13" href="/cn/actresses/abc">某</a>
              <a class="text-nord13" href="/cn/actresses/abc">某女优全名 12 条影片</a>
            </body></html>
        """.trimIndent()

        val result = DirectoryParser.parse(html, "actresses", page = 1)
        assertEquals(1, result.items.size)
        assertEquals("某女优全名", result.items[0].name)
    }

    @Test
    fun lastPageHasNoNext() {
        val result = DirectoryParser.parse(actresses, "actresses", page = 99)
        assertEquals(null, result.nextPage)
    }

    @Test
    fun splitsSuffixInOtherLanguages() {
        // 同一套规则要能吃 條影片 / 本の動画 / videos —— 这也是切到日文/英文后最容易崩的地方。
        val html = """
            <html><body>
              <a class="text-nord13" href="/actresses/a">Aoi 123 條影片</a>
              <a class="text-nord13" href="/ja/actresses/b">葵 45 本の動画</a>
              <a class="text-nord13" href="/en/actresses/c">Aoi 67 videos</a>
            </body></html>
        """.trimIndent()

        val names = DirectoryParser.parse(html, "actresses", page = 1).items.map { it.name }
        assertEquals(listOf("Aoi", "葵", "Aoi"), names)
    }
    @Test
    fun parsesRealMakersDirectory() {
        // fixture 是真机抓的 /cn/makers（200，37 个条目）——证明厂商是真实存在的可浏览维度。
        val html = javaClass.classLoader!!.getResourceAsStream("directory-makers.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val result = DirectoryParser.parse(html, "makers", page = 1)
        println("makers=${result.items.size}")
        result.items.take(3).forEach { println("  ${it.path} | ${it.name} | ${it.countText} | ${it.debutText}") }

        assertTrue("厂商目录应有多个条目", result.items.size >= 10)
        assertTrue("路径必须是 /makers/{名}", result.items.all { it.path.startsWith("/makers/") })
        assertTrue("名字不能为空", result.items.all { it.name.isNotBlank() })
    }
}
