package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import app.missu.data.api.SourceConfig
import app.missu.data.api.VideoListOptions
import app.missu.data.model.VideoListQuery
import org.json.JSONArray
import org.json.JSONObject

/** 一条列表访问记录；只保存恢复入口所需的标题与口径，不保存列表内容。 */
data class ListHistoryEntry(
    val title: String,
    val query: VideoListQuery,
    val updatedAt: Long,
)

/** 本地最近列表：同一路径去重并更新口径，最多保留 20 条。 */
class ListHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:list-history", Context.MODE_PRIVATE)

    fun recent(): List<ListHistoryEntry> = readAll().sortedByDescending { it.updatedAt }

    fun record(entry: ListHistoryEntry) {
        val path = normalizedPath(entry.query.path)
        val next = (listOf(entry) + readAll().filterNot { normalizedPath(it.query.path) == path })
            .sortedByDescending { it.updatedAt }
            .take(MAX_ENTRIES)
        writeAll(next)
    }

    fun clear() = prefs.edit { remove(KEY) }

    private fun readAll(): List<ListHistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val title = json.optString("title").ifEmpty { return@mapNotNull null }
                val path = json.optString("path").ifEmpty { return@mapNotNull null }
                ListHistoryEntry(
                    title = title,
                    query = VideoListQuery(
                        path = path,
                        sort = VideoListOptions.Sort.fromKey(json.optString("sort").ifEmpty { null }),
                        filter = VideoListOptions.Filter.fromKey(json.optString("filter").ifEmpty { null }),
                    ),
                    updatedAt = json.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(entries: List<ListHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("path", entry.query.path)
                    put("sort", entry.query.sort?.name.orEmpty())
                    put("filter", entry.query.filter?.name.orEmpty())
                    put("updatedAt", entry.updatedAt)
                },
            )
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private fun normalizedPath(path: String) = SourceConfig.contentPath(path.substringBefore('?'))

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 20
    }
}
