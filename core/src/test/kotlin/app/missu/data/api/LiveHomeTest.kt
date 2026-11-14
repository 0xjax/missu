package app.missu.data.api

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真实站点抓取验证（需要联网，默认跳过）。
 *
 * 运行：`$env:MISSU_LIVE='1'; .\gradlew.bat :app:testDebugUnitTest --tests "*LiveHomeTest*"`
 *
 * NOTE: 这是唯一能证明「OkHttp + Jsoup 打得开站点」的检查。若返回 403 / ChallengeException，
 * 说明需要 WebView 过一遍人机验证并共享 Cookie，不是解析写错了。
 */
class LiveHomeTest {

    @Test
    fun fetchesRealHomePage() {
        assumeTrue("未设置 MISSU_LIVE=1，跳过联网验证", System.getenv("MISSU_LIVE") == "1")

        val sections = runBlocking { MissavApi().getHome() }

        println("抓到 ${sections.size} 个板块")
        sections.forEach { section ->
            println("  [${section.id}] ${section.title} -> ${section.videos.size} 条, more=${section.morePath}")
            section.videos.take(2).forEach { video ->
                println("      ${video.id} | ${video.duration ?: "-"} | ${video.badges} | ${video.title.take(40)}")
            }
        }

        assertTrue("首页应至少解析出一个板块", sections.isNotEmpty())
        assertTrue("板块里应有视频", sections.any { it.videos.isNotEmpty() })
    }
}
