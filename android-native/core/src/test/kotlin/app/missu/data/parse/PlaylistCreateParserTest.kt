package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新建片单表单解析。fixture 是真机抓的真实 `/playlists/create` 页面（登录态，200）。
 *
 * NOTE: 断言里**不打印** `_token` 的值，只检查它存在且非空。
 */
class PlaylistCreateParserTest {

    private val html: String by lazy {
        javaClass.classLoader!!.getResourceAsStream("playlist-create.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun parsesRealCreateForm() {
        val form = PlaylistCreateParser.parse(html)
        assertNotNull("真实页面应能解析出表单", form)
        form!!

        assertEquals("片单名输入框应为 name", "name", form.nameField)
        assertTrue("必须带 CSRF _token", form.fields["_token"]?.isNotBlank() == true)
        // 默认选中「私人」(value=0)，所以只有 0 被收集进来。
        assertEquals("0", form.fields["is_public"])
        assertTrue("描述字段应被收集", form.fields.containsKey("description"))
        // action 为空表示提交回当前路径，调用方会用 /playlists/create。
        assertTrue(form.action.isBlank() || form.action.startsWith("/"))
    }

    @Test
    fun returnsNullForPagesWithoutForm() {
        assertEquals(null, PlaylistCreateParser.parse("<html><body><p>没有表单</p></body></html>"))
    }
}
