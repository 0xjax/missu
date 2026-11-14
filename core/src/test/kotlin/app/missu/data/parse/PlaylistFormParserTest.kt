package app.missu.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 片单表单解析。两个 fixture 都是真机抓的真实页面（登录态，200）：
 * `playlist-create.html`（新建）与 `playlist-edit.html`（修改，含删除表单）。
 *
 * NOTE: 断言里**不打印** `_token`，只检查它存在且非空。
 * WARNING: 片单的写操作站点只有表单，没有 JSON 接口——这些规则是唯一取数/写数途径。
 */
class PlaylistFormParserTest {

    private val createHtml: String by lazy { resource("playlist-create.html") }
    private val editHtml: String by lazy { resource("playlist-edit.html") }

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun parsesCreateForm() {
        val form = PlaylistFormParser.parseCreate(createHtml)
        assertNotNull(form)
        form!!

        assertEquals("name", form.nameField)
        assertTrue("必须带 CSRF _token", form.fields["_token"]?.isNotBlank() == true)
        assertEquals("默认选中私人", "0", form.fields["is_public"])
        assertTrue("描述字段应被收集", form.fields.containsKey("description"))
        assertTrue("action 为空表示提交回当前页", form.action.isBlank() || form.action.startsWith("/"))
    }

    @Test
    fun parsesEditPageCurrentValues() {
        val page = PlaylistFormParser.parseEdit(editHtml)
        assertNotNull(page)
        page!!

        // 真实页面上这部片单叫 test，默认私人。
        assertEquals("test", page.name)
        assertFalse(page.isPublic)
    }

    @Test
    fun parsesUpdateFormWithoutMethodSpoofing() {
        val page = PlaylistFormParser.parseEdit(editHtml)!!
        assertEquals("name", page.update.nameField)
        assertTrue(page.update.fields["_token"]?.isNotBlank() == true)
        // 更新表单没有 _method，action 为空 = POST 回编辑页自身。
        assertFalse("更新表单不应带 _method", page.update.fields.containsKey("_method"))
        assertTrue(page.update.action.isBlank())
    }

    @Test
    fun parsesDeleteFormAsMethodSpoofedPost() {
        val page = PlaylistFormParser.parseEdit(editHtml)!!
        assertEquals("delete", page.deleteFields["_method"])
        assertTrue(page.deleteFields["_token"]?.isNotBlank() == true)
        // 删除表单 action 就是片单自身 URL。
        assertTrue(
            "删除 action 应指向片单 URL，实际 ${page.deleteAction}",
            page.deleteAction.endsWith("/playlists/") || Regex("/playlists/[A-Za-z0-9]+$").containsMatchIn(page.deleteAction),
        )
    }

    @Test
    fun returnsNullForPagesWithoutForm() {
        assertEquals(null, PlaylistFormParser.parseCreate("<html><body>没有表单</body></html>"))
        assertEquals(null, PlaylistFormParser.parseEdit("<html><body>没有表单</body></html>"))
    }
}
