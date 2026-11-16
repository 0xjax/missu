package app.missu.data.parse

import app.missu.data.model.SeekPreviewSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实抓下来的详情页验证解析（fixture 来源：`.local/missav-api/detail.har`，抓的是 `https://missav.ai/sone-955`，200 OK）。
 * 重点校验播放地址：它藏在页面内联的 packed 脚本里，必须解包后取出 `.m3u8`。
 */
class DetailFixtureTest {

    private val html: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("detail.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun parsesRealDetailPage() {
        val detail = DetailParser.parse(html, "sone-955")

        println("title=${detail.title}")
        println("duration=${detail.duration} releaseDate=${detail.releaseDate} internalId=${detail.internalId}")
        println("actors=${detail.actors}")
        println("playback=${detail.playbackUrl}")
        detail.info.forEach { row -> println("  ${row.label} = ${row.values.joinToString { it.text }}") }

        assertTrue("标题不应回退成 id", detail.title != "sone-955" && detail.title.isNotBlank())
        assertTrue("应解析出 HLS 播放地址，实际 ${detail.playbackUrl}", detail.playbackUrl?.endsWith(".m3u8") == true)
        assertTrue("信息行应足够多，实际 ${detail.info.size}", detail.info.size >= 5)
        assertTrue("fixture 页面里应带 internalId", detail.internalId != null)
        assertTrue("缩略图应为绝对地址", detail.thumbnailUrl?.startsWith("https://") == true)

        val preview = detail.seekPreview!!
        assertEquals(3575, preview.frameCount)
        assertEquals(300, preview.frameWidth)
        assertEquals(168, preview.frameHeight)
        assertEquals(6, preview.columns)
        assertEquals(6, preview.rows)
        assertEquals(100, preview.spriteUrls.size)
        assertEquals(
            "https://surrit.com/99c34382-0963-4113-89ab-58dc7c3ea346/seek/_0.jpg",
            preview.spriteUrls.first(),
        )
    }

    @Test
    fun mapsSeekPositionToSpriteFrame() {
        val spec = SeekPreviewSpec(
            frameCount = 100,
            frameWidth = 300,
            frameHeight = 168,
            columns = 2,
            rows = 2,
            spriteUrls = List(25) { "https://example.com/_$it.jpg" },
        )

        assertEquals("https://example.com/_0.jpg", spec.frameAt(0, 100_000)?.url)
        val frame = spec.frameAt(12_500, 100_000)!!
        assertEquals("https://example.com/_3.jpg", frame.url)
        assertEquals(0, frame.x)
        assertEquals(0, frame.y)
        val last = spec.frameAt(100_000, 100_000)!!
        assertEquals("https://example.com/_24.jpg", last.url)
        assertEquals(300, last.x)
        assertEquals(168, last.y)
    }
}
