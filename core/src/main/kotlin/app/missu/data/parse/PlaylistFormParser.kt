package app.missu.data.parse

import org.jsoup.Jsoup

/** 片单表单（新建 / 更新的字段集合）。`fields` 里含 CSRF `_token`。 */
data class PlaylistForm(
    val action: String,
    val fields: Map<String, String>,
    val nameField: String,
)

/** 「修改片单」页上的两件事：更新表单 + 删除表单。 */
data class PlaylistEditPage(
    val name: String,
    val description: String,
    val isPublic: Boolean,
    val update: PlaylistForm,
    val deleteAction: String,
    val deleteFields: Map<String, String>,
)

/**
 * 片单表单解析（新建页 + 修改页）。
 *
 * WARNING: 片单的写操作站点**只有表单**（`/api/playlists/…` 只有针对某部影片的 view/add/remove）：
 * - 新建：`POST /playlists/create`；
 * - 更新：`POST /playlists/{id}/edit`（表单 `action` 为空 = 提交回当前页，**没有** `_method` 伪装）；
 * - 删除：`POST /playlists/{id}` + `_method=delete`（表单 `action` 就是片单自身 URL）。
 *
 * 字段名一律从页面读，不写死；站点改表单不需要改代码。
 * NOTE: `_token` 只用于本次提交，**绝不写日志或注释**。注释里也不要出现斜杠星号序列（会导致 Kotlin 嵌套注释）。
 */
object PlaylistFormParser {

    /** 新建页：取第一个带 `input[name]` 的表单。 */
    fun parseCreate(html: String): PlaylistForm? {
        val form = Jsoup.parse(html).select("form").firstOrNull { it.selectFirst("input[name]") != null }
            ?: return null
        return toForm(form) ?: return null
    }

    /**
     * 修改页：更新表单 + 删除表单，外加页面上的当前值（名字 / 描述 / 是否公开）。
     * 删除表单用 `input[name=_method]` 的值等于 `delete` 来识别（不依赖表单顺序）。
     */
    fun parseEdit(html: String): PlaylistEditPage? {
        val doc = Jsoup.parse(html)

        val updateElement = doc.select("form").firstOrNull { form ->
            form.selectFirst("input[name]") != null && form.selectFirst("input[name=_method]") == null
        } ?: return null
        val update = toForm(updateElement) ?: return null

        val deleteElement = doc.select("form").firstOrNull { form ->
            form.selectFirst("input[name=_method]")?.attr("value")?.equals("delete", ignoreCase = true) == true
        }

        val name = updateElement.selectFirst("input[name]")?.attr("value").orEmpty()
        val description = updateElement.selectFirst("textarea[name]")?.text().orEmpty()
        val isPublic = updateElement.select("input[type=radio][name=is_public]").any {
            it.attr("value") == "1" && it.hasAttr("checked")
        }

        return PlaylistEditPage(
            name = name,
            description = description,
            isPublic = isPublic,
            update = update,
            deleteAction = deleteElement?.attr("action").orEmpty(),
            deleteFields = deleteElement?.let { collectFields(it) }.orEmpty(),
        )
    }

    private fun toForm(form: org.jsoup.nodes.Element): PlaylistForm? {
        val nameField = form.select("input").firstOrNull { input ->
            val type = input.attr("type").ifEmpty { "text" }
            (type == "text" || type == "search") && input.attr("name").isNotEmpty()
        }?.attr("name") ?: return null

        return PlaylistForm(
            action = form.attr("action"),
            fields = collectFields(form),
            nameField = nameField,
        )
    }

    private fun collectFields(form: org.jsoup.nodes.Element): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        form.select("input").forEach { input ->
            val name = input.attr("name")
            if (name.isEmpty()) return@forEach
            val type = input.attr("type").ifEmpty { "text" }
            when (type) {
                "submit", "button", "file" -> return@forEach
                "checkbox", "radio" -> if (input.hasAttr("checked")) {
                    fields[name] = input.attr("value").ifEmpty { "on" }
                }
                else -> fields[name] = input.attr("value")
            }
        }
        // 描述是 textarea，不在 input 里，单独收。
        form.select("textarea[name]").forEach { textarea ->
            fields[textarea.attr("name")] = textarea.text()
        }
        return fields
    }
}
