package app.missu.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条观看记录。`positionMs` 是上次播放位置，用于「继续播放」。 */
data class HistoryEntry(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val duration: String?,
    val positionMs: Long,
    val updatedAt: Long,
)

/**
 * 本地观看历史。
 *
 * NOTE: 历史只存本地，不依赖站点接口——站点没有「观看进度」这种接口，legacy 也是纯本地。
 * NOTE: 用 SharedPreferences + JSON 数组，条目数有上限；TODO: 条目多了或要按片单/时间筛选时迁到 Room。
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:history", Context.MODE_PRIVATE)

    fun get(id: String): HistoryEntry? = readAll().firstOrNull { it.id == id }

    fun recent(limit: Int = DEFAULT_LIMIT): List<HistoryEntry> =
        readAll().sortedByDescending { it.updatedAt }.take(limit)

    fun record(entry: HistoryEntry) {
        val others = readAll().filterNot { it.id == entry.id }
        val next = (listOf(entry) + others).sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        writeAll(next)
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun readAll(): List<HistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id").ifEmpty { return@mapNotNull null }
                HistoryEntry(
                    id = id,
                    title = json.optString("title"),
                    thumbnailUrl = json.optString("thumbnailUrl").ifEmpty { null },
                    duration = json.optString("duration").ifEmpty { null },
                    positionMs = json.optLong("positionMs"),
                    updatedAt = json.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(entries: List<HistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("thumbnailUrl", entry.thumbnailUrl.orEmpty())
                    put("duration", entry.duration.orEmpty())
                    put("positionMs", entry.positionMs)
                    put("updatedAt", entry.updatedAt)
                },
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 200
        const val DEFAULT_LIMIT = 100
    }
}
