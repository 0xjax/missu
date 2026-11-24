package app.missu.data.article

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

data class Article(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val paragraphs: List<String>,
)

class ArticleRepository(private val context: Context) {
    val articles: List<Article> by lazy {
        context.assets.open("articles.json").bufferedReader().use { parseArticles(it.readText()) }
    }

    fun findById(id: String): Article? = articles.firstOrNull { it.id == id }
}

internal fun parseArticles(value: String): List<Article> =
    Json.parseToJsonElement(value).jsonObject.getValue("articles").jsonArray.map { item ->
        val article = item.jsonObject
        Article(
            id = article.getValue("id").jsonPrimitive.content,
            category = article.getValue("category").jsonPrimitive.content,
            title = article.getValue("title").jsonPrimitive.content,
            summary = article.getValue("summary").jsonPrimitive.content,
            paragraphs = article.getValue("paragraphs").jsonArray.map { it.jsonPrimitive.content },
        )
    }

internal fun selectNextArticle(
    articles: List<Article>,
    currentId: String?,
    recentIds: List<String>,
    random: Random = Random,
): Article? {
    if (articles.isEmpty()) return null
    val unread = articles.filter { it.id != currentId && it.id !in recentIds }
    val candidates = unread.ifEmpty { articles.filter { it.id != currentId } }
    return candidates.ifEmpty { articles }.random(random)
}
