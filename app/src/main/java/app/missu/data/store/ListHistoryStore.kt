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
    val pinned: Boolean = false,
)

/** 本地列表入口：置顶项始终保留，另按完整口径最多保存 20 条最近记录。 */
class ListHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:list-history", Context.MODE_PRIVATE)

    fun recent(): List<ListHistoryEntry> = readAll().sortedWith(historyOrder)

    fun record(entry: ListHistoryEntry) {
        val entries = readAll()
        val pinned = entries.firstOrNull { sameQuery(it.query, entry.query) }?.pinned == true
        val next = listOf(entry.copy(pinned = pinned)) +
            entries.filterNot { sameQuery(it.query, entry.query) }
        val pinnedEntries = next.filter { it.pinned }
        val recentEntries = next.filterNot { it.pinned }.sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        writeAll(pinnedEntries + recentEntries)
    }

    fun togglePin(query: VideoListQuery) {
        writeAll(
            readAll().map { entry ->
                if (sameQuery(entry.query, query)) entry.copy(pinned = !entry.pinned) else entry
            },
        )
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
                    pinned = json.optBoolean("pinned"),
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
                    put("pinned", entry.pinned)
                },
            )
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private fun sameQuery(first: VideoListQuery, second: VideoListQuery): Boolean =
        normalizedPath(first.path) == normalizedPath(second.path) &&
            first.sort == second.sort && first.filter == second.filter

    private fun normalizedPath(path: String) = SourceConfig.contentPath(path.substringBefore('?'))

    private companion object {
        val historyOrder = compareByDescending<ListHistoryEntry> { it.pinned }
            .thenByDescending { it.updatedAt }
        const val KEY = "entries"
        const val MAX_ENTRIES = 20
    }
}
