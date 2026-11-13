package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 片单页解析。fixture 是真机上抓的真实 `/playlists` 页面（登录态，200，148 KB）。
 *
 * WARNING: 站点没有片单列表/详情的 JSON 接口，这两个解析器是唯一取数途径，
 * 所以规则必须被测试锁住：改选择器时先跑这里。
 */
class PlaylistParserTest {

    private val playlistsHtml: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("playlists.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun parsesRealPlaylistsPage() {
        val playlists = PlaylistParser.parsePlaylists(playlistsHtml)

        println("playlists=${playlists.size}")
        playlists.forEach { println("  ${it.path} | ${it.name} | ${it.visibility} | ${it.updatedAt} | ${it.owner}") }

        assertTrue("真实页面应至少解析出一个片单", playlists.isNotEmpty())
        playlists.forEach { playlist ->
            assertTrue("路径必须是 /playlists/{id}", Regex("^/playlists/[^/]+$").matches(playlist.path))
            assertTrue("名字不能为空", playlist.name.isNotBlank())
            assertTrue("不能把 create 当成片单", playlist.path != "/playlists/create")
            assertTrue("id 不能带斜杠", !playlist.id.contains("/"))
        }
    }

    @Test
    fun parsesVisibilityAndUpdatedDate() {
        val playlists = PlaylistParser.parsePlaylists(playlistsHtml)
        val first = playlists.first()
        // 真实页面每行含「私人/公開」与「最后更新于 yyyy-MM-dd」，两个都要切出来。
        assertNotNull("应解析出可见性，实际 ${first.visibility}", first.visibility)
        assertNotNull("应解析出更新时间，实际 ${first.updatedAt}", first.updatedAt)
        assertTrue("更新时间是 yyyy-MM-dd", Regex("\\d{4}-\\d{2}-\\d{2}").matches(first.updatedAt!!))
        assertTrue("名字里不应包含可见性文案", !first.name.contains(first.visibility!!))
    }

    @Test
    fun parsesPlaylistDetailTitleAndVideos() {
        // 详情页结构：h1 标题（可能带「片单：」前缀）+ 与列表页相同的影片卡片。
        val html = """
            <html><body>
              <h1>片单：我的精选</h1>
              <div>
                <div class="thumbnail group">
                  <a href="/cn/sone-955">
                    <img data-src="//fourhoi.com/sone-955/cover-n.jpg">
                    <span class="bottom-1 left-1">中文字幕</span>
                    <span class="bottom-1 right-1">1:58:57</span>
                  </a>
                </div>
                <a class="text-secondary" href="/cn/sone-955">SONE-955 示例标题</a>
              </div>
            </body></html>
        """.trimIndent()

        val detail = PlaylistParser.parsePlaylistDetail(html)
        assertEquals("我的精选", detail.name)
        assertEquals(1, detail.videos.size)
        assertEquals("sone-955", detail.videos[0].id)
        assertEquals("1:58:57", detail.videos[0].duration)
    }
}
