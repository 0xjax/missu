package app.missu.data.parse

import app.missu.data.model.VideoPlaylist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 站点 JSON 接口的解析（与网络无关，纯函数，可单测）。
 *
 * NOTE: 抽出来的原因：这段逻辑原来内联在 [app.missu.data.api.MissavApi] 里，只能靠真机联网验证；
 * 抽成纯函数后就能用固定 JSON 断言字段缺失/类型异常时的兜底行为。
 */
object ApiJson {

    /** `/api/items/{id}/view` → `{"saved":true}`。字段缺失或类型不对按「未收藏」处理。 */
    fun parseSavedState(json: String): Boolean =
        runCatching {
            Json.parseToJsonElement(json).jsonObject["saved"]?.jsonPrimitive?.booleanOrNull ?: false
        }.getOrDefault(false)

    /**
     * `/api/playlists/{dvdId}` → `{"data":[{"key":"…","name":"…","is_added":true}]}`。
     * NOTE: 没有 `key` 的条目直接丢弃——没有 key 就无法写回；`name` 缺失时用 key 兜底。
     */
    fun parseVideoPlaylists(json: String): List<VideoPlaylist> {
        val data = runCatching {
            Json.parseToJsonElement(json).jsonObject["data"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        return data.mapNotNull { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val key = runCatching { item["key"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VideoPlaylist(
                key = key,
                name = runCatching { item["name"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: key,
                added = runCatching { item["is_added"]?.jsonPrimitive?.booleanOrNull }.getOrNull() ?: false,
            )
        }
    }
}
