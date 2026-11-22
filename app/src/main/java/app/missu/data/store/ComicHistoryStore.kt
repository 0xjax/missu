package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import app.missu.data.comic.ComicImage
import app.missu.data.comic.ComicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ComicHistoryEntry(
    val source: ComicSource,
    val mangaId: String,
    val title: String,
    val cover: ComicImage?,
    val chapterId: String,
    val chapterTitle: String,
    val page: Int,
    val updatedAt: Long,
)

class ComicHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("missu:comic-history", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(readAll())
    val entries: StateFlow<List<ComicHistoryEntry>> = _entries.asStateFlow()

    fun record(entry: ComicHistoryEntry) {
        val next = (listOf(entry) + _entries.value.filterNot {
            it.source == entry.source && it.mangaId == entry.mangaId
        }).sortedByDescending { it.updatedAt }.take(MAX_ENTRIES)
        write(next)
    }

    fun updatePosition(source: ComicSource, mangaId: String, chapterId: String, page: Int) {
        val current = _entries.value.firstOrNull { it.source == source && it.mangaId == mangaId } ?: return
        record(
            current.copy(
                chapterId = chapterId,
                page = page.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun clear() {
        preferences.edit { remove(KEY) }
        _entries.value = emptyList()
    }

    private fun readAll(): List<ComicHistoryEntry> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val mangaId = json.optString("mangaId").ifEmpty { return@mapNotNull null }
                val chapterId = json.optString("chapterId").ifEmpty { return@mapNotNull null }
                val coverUrl = json.optString("coverUrl").ifEmpty { null }
                ComicHistoryEntry(
                    source = ComicSource.fromId(json.optString("source")),
                    mangaId = mangaId,
                    title = json.optString("title"),
                    cover = coverUrl?.let {
                        ComicImage(
                            url = it,
                            referer = json.optString("coverReferer").ifEmpty { null },
                            scrambleId = json.optLong("scrambleId").takeIf { value -> value > 0L },
                        )
                    },
                    chapterId = chapterId,
                    chapterTitle = json.optString("chapterTitle"),
                    page = json.optInt("page"),
                    updatedAt = json.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(entries: List<ComicHistoryEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("source", entry.source.id)
                    put("mangaId", entry.mangaId)
                    put("title", entry.title)
                    put("coverUrl", entry.cover?.url.orEmpty())
                    put("coverReferer", entry.cover?.referer.orEmpty())
                    put("scrambleId", entry.cover?.scrambleId ?: 0L)
                    put("chapterId", entry.chapterId)
                    put("chapterTitle", entry.chapterTitle)
                    put("page", entry.page)
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
