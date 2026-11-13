package app.missu.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 真实站点诊断（需要联网，默认跳过）：打印状态码、关键响应头与页面标题，
 * 用来区分「被 Cloudflare 挡」和「解析写错」。
 *
 * 运行：`$env:MISSU_LIVE='1'; .\gradlew.bat :app:testDebugUnitTest --tests "*LiveDiagnosticsTest*"`
 */
class LiveDiagnosticsTest {

    @Test
    fun printsRealResponseFacts() {
        assumeTrue("未设置 MISSU_LIVE=1，跳过联网诊断", System.getenv("MISSU_LIVE") == "1")

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        for (url in listOf(SourceConfig.BASE_URL + "/", SourceConfig.BASE_URL + "/cn")) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", SourceConfig.DEFAULT_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                    .find(body)?.groupValues?.get(1)?.trim()?.take(120)
                println("URL=$url")
                println("  code=${response.code}")
                println("  server=${response.header("server")}")
                println("  cf-mitigated=${response.header("cf-mitigated")}")
                println("  contentType=${response.header("content-type")}")
                println("  title=$title")
                println("  bodyLength=${body.length}")
            }
        }

        println("---- 走 API 层 ----")
        runCatching { runBlocking { MissavApi().getHome() } }
            .onSuccess { println("OK: ${it.size} 个板块") }
            .onFailure { println("FAILED: ${it::class.simpleName}: ${it.message}") }
    }
}
