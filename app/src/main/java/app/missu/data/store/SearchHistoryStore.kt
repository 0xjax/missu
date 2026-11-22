package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * 搜索历史：本地最近关键词，最新的在前，重复的提到最前面。
 *
 * NOTE: 只存关键词字符串，不存结果；上限 20 条，够用且不会让 SharedPreferences 膨胀。
 * TODO: 和观看历史一样，等条目更多或要按时间分组时迁到 Room。
 */
interface SearchHistory {
    fun recent(): List<String>
    fun record(keyword: String)
    fun clear()
}

class SearchHistoryStore(
    context: Context,
    preferencesName: String = "missu:search-history",
) : SearchHistory {

    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun recent(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    override fun record(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        val next = (listOf(normalized) + recent().filterNot { it.equals(normalized, ignoreCase = true) })
            .take(MAX_ENTRIES)
        write(next)
    }

    override fun clear() = prefs.edit { remove(KEY) }

    private fun write(entries: List<String>) {
        val array = JSONArray()
        entries.forEach { array.put(it) }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private companion object {
        const val KEY = "keywords"
        const val MAX_ENTRIES = 20
    }
}
