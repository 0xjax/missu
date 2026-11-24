package app.missu.data.literature

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

class LiteratureRepository(initialDatabase: LiteratureDatabase) {
    private val mutex = Mutex()
    private var database = initialDatabase
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    suspend fun warmUp() = mutex.withLock {
        database.dao().warmUp()
        Unit
    }

    suspend fun replaceDatabase(newDatabase: LiteratureDatabase) = mutex.withLock {
        val oldDatabase = database
        database = newDatabase
        oldDatabase.close()
        _generation.value++
    }

    suspend fun findById(id: String): LiteraryWork? = mutex.withLock {
        database.dao().findById(id)?.toModel()
    }

    suspend fun getPage(
        filter: LiteratureFilter,
        query: String,
        favorites: Set<String>,
        offset: Int,
        limit: Int = PAGE_SIZE,
        sort: LiteratureSort = LiteratureSort.PROMINENCE,
    ): LiteraturePage {
        if (filter == LiteratureFilter.FAVORITES && favorites.isEmpty()) {
            return LiteraturePage(emptyList(), nextOffset = null)
        }
        val kind = when (filter) {
            LiteratureFilter.POEM -> LiteraryKind.POEM.name
            LiteratureFilter.CI -> LiteraryKind.CI.name
            LiteratureFilter.QU -> LiteraryKind.QU.name
            LiteratureFilter.PROSE -> LiteraryKind.PROSE.name
            LiteratureFilter.BOOK -> LiteraryKind.BOOK.name
            else -> null
        }
        val rows = mutex.withLock {
            database.dao().queryWorkSummaries(
                kind = kind,
                favoriteOnly = filter == LiteratureFilter.FAVORITES,
                favorites = favorites.sorted(),
                search = searchExpression(query),
                sort = sort.name,
                limit = limit + 1,
                offset = offset,
            )
        }
        return LiteraturePage(
            works = rows.take(limit).map { it.toModel() },
            nextOffset = if (rows.size > limit) offset + limit else null,
        )
    }

    private fun LiteraryWorkSummaryEntity.toModel(): LiteraryWorkSummary = LiteraryWorkSummary(
        id = id,
        kind = LiteraryKind.valueOf(kind),
        title = title,
        author = author,
        themes = Json.decodeFromString(theme),
    )

    private fun LiteraryWorkEntity.toModel(): LiteraryWork = LiteraryWork(
        id = id,
        kind = LiteraryKind.valueOf(kind),
        title = title,
        author = author,
        text = text,
        chapters = parseChapters(chapters),
        source = source,
        prominence = prominence,
        themes = Json.decodeFromString(theme),
        notes = Json.decodeFromString(notes),
        translations = Json.decodeFromString(translation),
        appreciations = Json.decodeFromString(appreciation),
    )

    private companion object {
        const val PAGE_SIZE = 50
    }
}

private fun searchExpression(query: String): String? {
    if (query.isBlank()) return null
    return tokenize(query).joinToString(" AND ") { "\"$it\"" }.ifEmpty { "\"\"" }
}

private fun tokenize(value: String): Set<String> {
    val tokens = linkedSetOf<String>()
    Regex("[\\p{script=Han}]+|[\\p{L}\\p{N}]+").findAll(value.lowercase(Locale.ROOT)).forEach { match ->
        val part = match.value
        if (part.firstOrNull()?.let { Character.UnicodeScript.of(it.code) } == Character.UnicodeScript.HAN) {
            val characters = part.codePoints().toArray().map { String(Character.toChars(it)) }
            tokens += characters
            characters.windowed(2).forEach { tokens += it.joinToString("") }
        } else {
            tokens += part
        }
    }
    return tokens
}


private fun parseChapters(value: String): List<LiteraryChapter> =
    Json.parseToJsonElement(value).jsonArray.map { item ->
        val chapter = item.jsonObject
        LiteraryChapter(
            id = chapter.getValue("id").jsonPrimitive.content,
            title = chapter.getValue("title").jsonPrimitive.content,
            text = chapter.getValue("text").jsonPrimitive.content,
        )
    }
