package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站点导航解析（「频道」页签的数据源），用真实抓下来的首页 HTML 验证。
 *
 * 结构是 Alpine.js 的两段式：标题 `<a href="#" @click.prevent="showCollapse = … 'key' …">`，
 * 项目在紧跟的 `x-show="showCollapse === 'key'"` 那个 `<span>` 里。同一份导航在页面里出现两次
 * （桌面 + 移动），所以解析必须去重。
 *
 * 重新抓 fixture：用浏览器打开首页、导 HAR，再按 README 的说明替换 `resources/home-cn.html`。
 */
class NavFixtureTest {

    private val html: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("home-cn.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun parsesRealNavigation() {
        val groups = HomeParser.parseNav(html)

        println("导航分组: " + groups.joinToString { "${it.title}(${it.items.size})" })
        groups.forEach { group -> println("  ${group.title}: " + group.items.map { it.path }) }

        assertTrue("应解析出导航分组（实际 ${groups.size}）", groups.isNotEmpty())
        // 桌面与移动两份导航必须合成一份
        assertEquals("分组不应重复（桌面/移动同内容）", groups.map { it.title }.distinct().size, groups.size)

        val titles = groups.map { it.title }
        for (expected in listOf("觀看日本 AV", "素人", "無碼影片", "亞洲 AV")) {
            assertTrue("缺少分组「$expected」：$titles", titles.contains(expected))
        }
    }

    @Test
    fun normalizesNavPaths() {
        val groups = HomeParser.parseNav(html)
        val all = groups.flatMap { it.items }

        assertTrue("导航项不应为空", all.isNotEmpty())
        assertTrue("路径必须已剥掉 /dmNNN 前缀与语言段：${all.map { it.path }.filter { p -> !p.startsWith("/") || p.contains("/dm") }}",
            all.all { it.path.startsWith("/") && !it.path.substringAfter('/').matches(Regex("^dm\\d+.*")) })
        assertTrue("名称不应为空", all.all { it.name.isNotBlank() })
        assertTrue("外链必须被滤掉：${all.filter { it.path.contains("bit.ly") }}",
            all.none { it.path.contains("bit.ly") || it.path.startsWith("http") })
        assertTrue("同一分组内路径不应重复", groups.all { group -> group.items.map { it.path }.distinct().size == group.items.size })

        // 分组内容抽查：这几条是「观看日本 AV」与三个频道组各自的代表项。
        fun pathsOf(title: String) = groups.first { it.title == title }.items.map { it.path }
        assertTrue("jav 组应含 /new 与女优排行", pathsOf("觀看日本 AV").containsAll(listOf("/new", "/actresses/ranking")))
        assertTrue("素人组应含 /siro", pathsOf("素人").contains("/siro"))
        assertTrue("无码组应含 /fc2 与 /heyzo", pathsOf("無碼影片").containsAll(listOf("/fc2", "/heyzo")))
        assertTrue("亚洲 AV 组应含直播两项", pathsOf("亞洲 AV").containsAll(listOf("/klive", "/clive")))
    }
}
