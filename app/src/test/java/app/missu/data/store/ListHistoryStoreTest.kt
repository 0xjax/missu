package app.missu.data.store

import android.content.Context
import app.missu.data.api.VideoListOptions
import app.missu.data.model.VideoListQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Windows SAC 会拦 Robolectric 解压的未签名 Conscrypt DLL；本类不测 TLS。
@ConscryptMode(ConscryptMode.Mode.OFF)
class ListHistoryStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearPreferences() {
        preferences().edit().clear().commit()
    }

    @After
    fun cleanUp() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `different queries for a normalized path remain separate`() {
        val store = ListHistoryStore(context)
        store.record(ListHistoryEntry("Other", VideoListQuery("/latest"), updatedAt = 1))
        store.record(ListHistoryEntry("New", VideoListQuery("/new"), updatedAt = 2))
        val latest = ListHistoryEntry(
            title = "New",
            query = VideoListQuery(
                path = "/cn/new",
                sort = VideoListOptions.Sort.SAVED,
                filter = VideoListOptions.Filter.CHINESE_SUBTITLE,
            ),
            updatedAt = 3,
        )

        store.record(latest)

        assertEquals(
            listOf(
                latest,
                ListHistoryEntry("New", VideoListQuery("/new"), 2),
                ListHistoryEntry("Other", VideoListQuery("/latest"), 1),
            ),
            store.recent(),
        )
    }

    @Test
    fun `revisiting a pinned query preserves its pin across reloads`() {
        val store = ListHistoryStore(context)
        store.record(ListHistoryEntry("Other", VideoListQuery("/latest"), updatedAt = 1))
        store.record(ListHistoryEntry("New", VideoListQuery("/new"), updatedAt = 2))
        store.togglePin(VideoListQuery("/en/new"))
        val revisited = ListHistoryEntry("New", VideoListQuery("/cn/new"), updatedAt = 3, pinned = true)

        store.record(ListHistoryEntry("New", VideoListQuery("/cn/new"), updatedAt = 3))

        assertEquals(listOf(revisited, ListHistoryEntry("Other", VideoListQuery("/latest"), 1)), store.recent())
        assertEquals(listOf(revisited), ListHistoryStore(context).recent().take(1))

        store.togglePin(revisited.query)

        assertEquals(ListHistoryEntry("New", VideoListQuery("/cn/new"), 3), store.recent().first())
    }

    @Test
    fun `history keeps only the twenty most recent unpinned lists and can be cleared`() {
        val store = ListHistoryStore(context)
        (1..21).forEach { index ->
            store.record(ListHistoryEntry("List $index", VideoListQuery("/list/$index"), index.toLong()))
        }

        assertEquals(20, store.recent().size)
        assertEquals("List 21", store.recent().first().title)
        assertEquals("List 2", store.recent().last().title)

        store.clear()

        assertEquals(emptyList<ListHistoryEntry>(), store.recent())
    }

    @Test
    fun `pinned lists survive the recent history limit`() {
        val store = ListHistoryStore(context)
        store.record(ListHistoryEntry("Pinned", VideoListQuery("/list/1"), updatedAt = 1))
        store.togglePin(VideoListQuery("/list/1"))
        (2..21).forEach { index ->
            store.record(ListHistoryEntry("List $index", VideoListQuery("/list/$index"), index.toLong()))
        }

        assertEquals(21, store.recent().size)
        assertEquals(ListHistoryEntry("Pinned", VideoListQuery("/list/1"), 1, pinned = true), store.recent().first())
        assertEquals(20, store.recent().count { !it.pinned })
    }

    private fun preferences() = context.getSharedPreferences("missu:list-history", Context.MODE_PRIVATE)
}
