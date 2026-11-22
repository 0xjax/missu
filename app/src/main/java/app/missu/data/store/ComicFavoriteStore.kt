package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import app.missu.data.comic.ComicDetail
import app.missu.data.comic.ComicImage
import app.missu.data.comic.ComicSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ComicFavoriteEntry(
    val source: ComicSource,
    val mangaId: String,
    val title: String,
    val cover: ComicImage?,
    val updatedAt: Long,
)

class ComicFavoriteStore(context: Context) {
    private val preferences = context.getSharedPreferences("missu:comic-favorites", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(readAll())
    val entries: StateFlow<List<ComicFavoriteEntry>> = _entries.asStateFlow()

    fun contains(source: ComicSource, mangaId: String): Boolean =
        _entries.value.any { it.source == source && it.mangaId == mangaId }

    fun toggle(source: ComicSource, detail: ComicDetail) {
        if (contains(source, detail.id)) {
            write(_entries.value.filterNot { it.source == source && it.mangaId == detail.id })
            return
        }
        val entry = ComicFavoriteEntry(
            source = source,
            mangaId = detail.id,
            title = detail.title,
            cover = detail.cover,
            updatedAt = System.currentTimeMillis(),
        )
        write((listOf(entry) + _entries.value).take(MAX_ENTRIES))
    }

    private fun readAll(): List<ComicFavoriteEntry> {
        val raw = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val mangaId = json.optString("mangaId").ifEmpty { return@mapNotNull null }
                val coverUrl = json.optString("coverUrl").ifEmpty { null }
                ComicFavoriteEntry(
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
                    updatedAt = json.optLong("updatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(entries: List<ComicFavoriteEntry>) {
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
                    put("updatedAt", entry.updatedAt)
                },
            )
        }
        preferences.edit { putString(KEY, array.toString()) }
        _entries.value = entries
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 500
    }
}
