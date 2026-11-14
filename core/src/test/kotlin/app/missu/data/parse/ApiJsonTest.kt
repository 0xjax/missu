package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 站点 JSON 接口的解析。用固定 JSON 断言兜底行为——这些是联网时才暴露的边界，
 * 不写死就会在真机上安静地错（比如片单缺 key 后被静默丢一半）。
 */
class ApiJsonTest {

    @Test
    fun savedStateReadsBoolean() {
        assertTrue(ApiJson.parseSavedState("""{"saved":true}"""))
        assertFalse(ApiJson.parseSavedState("""{"saved":false}"""))
    }

    @Test
    fun savedStateFallsBackToFalse() {
        // 字段缺失、类型不对、整体不是 JSON —— 一律按「未收藏」，不能崩。
        assertFalse(ApiJson.parseSavedState("""{}"""))
        assertFalse(ApiJson.parseSavedState("""{"saved":"yes"}"""))
        assertFalse(ApiJson.parseSavedState("<html>challenge</html>"))
        assertFalse(ApiJson.parseSavedState(""))
    }

    @Test
    fun playlistsReadAllFields() {
        val json = """
            {"data":[
              {"key":"k1","name":"精选","is_added":true},
              {"key":"k2","name":"想看","is_added":false}
            ]}
        """.trimIndent()

        val result = ApiJson.parseVideoPlaylists(json)
        assertEquals(2, result.size)
        assertEquals("k1", result[0].key)
        assertEquals("精选", result[0].name)
        assertTrue(result[0].added)
        assertFalse(result[1].added)
    }

    @Test
    fun playlistsUseKeyAsNameAndDefaultAdded() {
        val json = """{"data":[{"key":"only-key"}]}"""
        val result = ApiJson.parseVideoPlaylists(json)
        assertEquals(1, result.size)
        assertEquals("only-key", result[0].name)
        assertFalse("缺 is_added 时按未加入", result[0].added)
    }

    @Test
    fun playlistsDropEntriesWithoutKey() {
        // 没有 key 的条目无法写回，必须丢弃而不是给个假 key。
        val json = """{"data":[{"name":"没有 key"},{"key":"ok","name":"有 key"}]}"""
        val result = ApiJson.parseVideoPlaylists(json)
        assertEquals(1, result.size)
        assertEquals("ok", result[0].key)
    }

    @Test
    fun playlistsTolerateMalformedPayload() {
        assertEquals(0, ApiJson.parseVideoPlaylists("""{"data":null}""").size)
        assertEquals(0, ApiJson.parseVideoPlaylists("""{}""").size)
        assertEquals(0, ApiJson.parseVideoPlaylists("not json").size)
    }
}
