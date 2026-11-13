package app.missu.data.parse

import org.jsoup.Jsoup

/**
 * 新建片单表单的解析结果。
 * `fields` 是表单里的隐藏字段（含 CSRF `_token`），`nameField` 是「片单名」输入框的 name。
 */
data class PlaylistCreateForm(
    val action: String,
    val fields: Map<String, String>,
    val nameField: String,
)

/**
 * 新建片单页（`/playlists/create`）解析。
 *
 * WARNING: 站点没有「新建片单」的 JSON 接口（`/api/playlists/…` 只有针对某部影片的 view/add/remove）。
 * 这里按 legacy `parsePlaylistCreateForm` 的规则读表单：找第一个带 `input[name]` 的 form，
 * 第一个 text/search 输入框当片单名，其余 input 收作字段（submit/button/file 跳过，radio/checkbox 只收 checked 的）。
 * 站点改表单字段时，代码不需要跟着改。
 *
 * NOTE: `_token` 只用于本次提交，**绝不能写日志或注释**。
 */
object PlaylistCreateParser {

    fun parse(html: String): PlaylistCreateForm? {
        val doc = Jsoup.parse(html)
        val form = doc.select("form").firstOrNull { it.selectFirst("input[name]") != null } ?: return null
        val inputs = form.select("input")

        val nameField = inputs.firstOrNull { input ->
            val type = input.attr("type").ifEmpty { "text" }
            (type == "text" || type == "search") && input.attr("name").isNotEmpty()
        }?.attr("name") ?: return null

        val fields = LinkedHashMap<String, String>()
        inputs.forEach { input ->
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

        return PlaylistCreateForm(
            action = form.attr("action"),
            fields = fields,
            nameField = nameField,
        )
    }
}
