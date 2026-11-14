package app.missu.data.parse

import app.missu.data.api.VideoListOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 站点声明的「当前排序」解析（离线，用合成标记锁结构）。
 *
 * 关键点：显示名随内容语言变化、取值不变，所以解析走「拿头部文案里的显示名去匹配下拉里的那一项」，
 * 而不是认识任何语言的词。
 */
class ListSortParseTest {

    private fun page(headerText: String, items: List<String>): String = """
        <div>
            <span>$headerText</span>
            <svg x-show="showDropdown !== 'sort'" class="ml-1 h-5 w-5"></svg>
        </div>
        <div x-cloak x-show="showDropdown === 'sort'">
            ${items.joinToString("\n            ") { it }}
        </div>
    """.trimIndent()

    /**
     * 真实标记是**多行**的：`<a` 换行、`href=` 另起一行、标签文字也在自己一行。
     * 第一版测试用了单行写法，于是正则只在测试里对、在真机上全 miss——这里必须照站点来。
     */
    private fun item(value: String, label: String): String = """
        <a
            href="https://missav.ai/cn/siro?sort=$value"
            class="text-nord0 block px-4 py-2 text-sm leading-5 hover:bg-nord4"
        >
            $label
        </a>
    """.trimIndent()

    private val zhItems = listOf(
        item("released_at", "发行日期"),
        item("published_at", "最近更新"),
        item("saved", "收藏数"),
        item("today_views", "今日浏览数"),
        item("weekly_views", "本週浏览数"),
        item("monthly_views", "本月浏览数"),
        item("views", "总浏览数"),
    )

    @Test
    fun mapsTheHeaderLabelToItsValue() {
        val html = page("排序: 发行日期", zhItems)
        assertEquals(VideoListOptions.Sort.RELEASED_AT, HomeParser.parseListSort(html))

        val html2 = page("排序: 本週浏览数", zhItems)
        assertEquals(VideoListOptions.Sort.WEEKLY_VIEWS, HomeParser.parseListSort(html2))
    }

    @Test
    fun survivesALocalizedPrefix() {
        // 前缀文案随语言变化（这里换成日文），显示名仍是那一项 → 照样能对上。
        val html = page("並び替え: 本月浏览数", zhItems)
        assertEquals(VideoListOptions.Sort.MONTHLY_VIEWS, HomeParser.parseListSort(html))
    }

    /**
     * 筛选生效时站点把 `sort=` 排到第二个参数、并用 `&amp;` 转义（2026-11-15 实测，
     * `/cn/today-hot?filters=chinese-subtitle`）。第一版正则只认 `?sort=` / `&sort=`，
     * 于是「一加筛选，排序名就退回默认」——真机踩到。
     */
    @Test
    fun findsTheValueWhenAnotherParameterComesFirst() {
        val filtered = zhItems.map { it.replace("?sort=", "?filters=chinese-subtitle&amp;sort=") }
        assertEquals(
            VideoListOptions.Sort.TODAY_VIEWS,
            HomeParser.parseListSort(page("排序: 今日浏览数", filtered)),
        )
    }

    @Test
    fun returnsNullWhenLabelIsUnknown() {
        assertNull(HomeParser.parseListSort(page("排序: 不存在的档", zhItems)))
    }

    @Test
    fun returnsNullWhenMenuIsMissing() {
        assertNull(HomeParser.parseListSort("<div><span>排序: 发行日期</span></div>"))
        assertNull(HomeParser.parseListSort(""))
    }

    @Test
    fun realHomePageHasNoSortHeader() {
        val html = javaClass.classLoader!!.getResourceAsStream("home-cn.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        assertNull("首页没有列表排序控件，解析必须返回 null 而不是乱猜", HomeParser.parseListSort(html))
    }
}
