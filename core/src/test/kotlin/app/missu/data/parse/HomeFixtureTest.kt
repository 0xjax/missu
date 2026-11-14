package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真实抓下来的首页 HTML** 验证解析器（fixture 来源：`.local/missav-api/home.har`，
 * 抓的是 `https://missav.ai/dm339`，200 OK）。这条测试不联网，但校验的是站点当前的真实结构。
 *
 * 重新抓 fixture：用浏览器打开首页、导 HAR，再按 README 的说明替换 `resources/home-cn.html`。
 */
class HomeFixtureTest {

    private val html: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("home-cn.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun parsesRealHomePage() {
        val sections = HomeParser.parseHome(html)

        println("板块: " + sections.joinToString { "${it.id}(${it.videos.size})" })
        assertTrue("真实首页应至少解析出一个板块", sections.isNotEmpty())
        assertTrue("每个板块都应有视频", sections.all { it.videos.isNotEmpty() })

        val videos = sections.flatMap { it.videos }
        assertTrue("视频总数应足够多（实际 ${videos.size}）", videos.size >= 10)
        videos.take(3).forEach { println("  ${it.id} | ${it.duration} | ${it.badges} | ${it.title.take(30)}") }
    }

    @Test
    fun normalizesRealCards() {
        val videos = HomeParser.parseHome(html).flatMap { it.videos }

        assertTrue("id 应剥掉 /dmNNN 前缀（取样）：${videos.take(5).map { it.id }}",
            videos.none { it.id.startsWith("dm") })
        assertTrue("pageUrl 必须是绝对地址",
            videos.all { it.pageUrl.startsWith("https://missav.ai/") })
        assertTrue("缩略图必须是绝对地址",
            videos.all { it.thumbnailUrl == null || it.thumbnailUrl.startsWith("https://") })

        val durations = videos.mapNotNull { it.duration }
        assertTrue("应有带时长的卡片", durations.isNotEmpty())
        assertTrue("时长格式应与站点一致：$durations",
            durations.all { Regex("^\\d+:\\d{2}(:\\d{2})?$").matches(it) })

        // NOTE: 角标来自封面左下角（中文字幕 / 無碼影片），不是所有卡片都有。
        println("带角标的卡片: " + videos.count { it.badges.isNotEmpty() } + " / " + videos.size)
        println("角标取值样本: " + videos.flatMap { it.badges }.distinct().take(5))
    }

    /**
     * 长按预览用的那段 mp4：卡片 HTML 里就带着 `data-src`，不是我们按番号拼出来的。
     * 这条测试锁两件事——绝大多数卡片都有它，以及路径里就是卡片自己的 id（变体各有自己的预览轨）。
     */
    @Test
    fun parsesCardPreviewClips() {
        val videos = HomeParser.parseHome(html).flatMap { it.videos }
        val withPreview = videos.filter { it.previewUrl != null }

        println("带预览片的卡片: ${withPreview.size} / ${videos.size}")
        withPreview.take(3).forEach { println("  ${it.id} -> ${it.previewUrl}") }

        assertTrue("真实首页应解析出预览片（实际 ${withPreview.size}）", withPreview.size >= 10)
        assertTrue("预览片必须是绝对地址", withPreview.all { it.previewUrl!!.startsWith("https://") })
        assertTrue("预览片应以 /preview.mp4 结尾",
            withPreview.all { it.previewUrl!!.substringBefore('?').endsWith("/preview.mp4") })

        val mismatched = withPreview.filterNot { it.previewUrl!!.contains("/${it.id}/") }
        assertTrue("预览片路径应包含卡片 id，否则就是猜出来的地址：$mismatched", mismatched.isEmpty())

        assertEquals(
            "https://fourhoi.com/jimmy-015/preview.mp4",
            videos.first { it.id == "jimmy-015" }.previewUrl,
        )
    }
}
