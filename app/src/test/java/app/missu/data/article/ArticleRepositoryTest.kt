package app.missu.data.article

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ArticleRepositoryTest {
    @Test
    fun `parser reads the public article catalog`() {
        val articles = parseArticles(
            """
            {
              "articles": [
                {
                  "id": "example",
                  "category": "Category",
                  "title": "Title",
                  "summary": "Summary",
                  "paragraphs": ["First", "Second"]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf(Article("example", "Category", "Title", "Summary", listOf("First", "Second"))), articles)
    }

    @Test
    fun `selection avoids current and recent articles before resetting`() {
        val articles = listOf(article("a"), article("b"), article("c"))
        val random = Random(7)
        val first = selectNextArticle(articles, null, emptyList(), random)!!
        val second = selectNextArticle(articles, first.id, listOf(first.id), random)!!
        val third = selectNextArticle(articles, second.id, listOf(first.id, second.id), random)!!

        assertEquals(setOf("a", "b", "c"), setOf(first.id, second.id, third.id))

        val fourth = selectNextArticle(articles, third.id, listOf(first.id, second.id, third.id), random)!!

        assertNotEquals(third.id, fourth.id)
        assertTrue(fourth.id in articles.map { it.id })
    }

    @Test
    fun `single article remains selectable`() {
        val articles = listOf(article("only"))

        assertEquals(articles.single(), selectNextArticle(articles, "only", listOf("only")))
    }

    private fun article(id: String) = Article(id, "Category", id, "Summary", listOf("Body"))
}
