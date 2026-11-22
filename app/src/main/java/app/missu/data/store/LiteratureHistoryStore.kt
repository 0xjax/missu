package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import app.missu.data.literature.LiteraryKind
import app.missu.data.literature.LiteraryWork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class LiteratureHistoryEntry(
    val id: String,
    val title: String,
    val author: String,
    val kind: LiteraryKind,
    val updatedAt: Long,
)

class LiteratureHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("missu:literature-history", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(readAll())
    val entries: StateFlow<List<LiteratureHistoryEntry>> = _entries.asStateFlow()

    fun record(work: LiteraryWork) {
        val entry = LiteratureHistoryEntry(
            id = work.id,
            title = work.title,
            author = work.author,
            kind = work.kind,
            updatedAt = System.currentTimeMillis(),
        )
        val next = (listOf(entry) + _entries.value.filterNot { it.id == work.id }).take(MAX_ENTRIES)
        write(next)
    }

    fun clear() {
        preferences.edit { remove(KEY) }
        _entries.value = emptyList()
    }

    private fun readAll(): List<LiteratureHistoryEntry> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val id = json.optString("id").ifEmpty { return@mapNotNull null }
                LiteratureHistoryEntry(
                    id = id,
                    title = json.optString("title"),
                    author = json.optString("author"),
                    kind = runCatching { LiteraryKind.valueOf(json.optString("kind")) }
                        .getOrDefault(LiteraryKind.POEM),
                    updatedAt = json.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(entries: List<LiteratureHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("title", entry.title)
                    put("author", entry.author)
                    put("kind", entry.kind.name)
                    put("updatedAt", entry.updatedAt)
                },
            )
        }
        preferences.edit { putString(KEY, array.toString()) }
        _entries.value = entries
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 200
    }
}
