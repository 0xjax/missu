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
class ListQueryStoreTest {
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
    fun `query persists for each normalized list group`() {
        val store = ListQueryStore(context)
        val query = VideoListQuery(
            path = "/cn/new",
            sort = VideoListOptions.Sort.SAVED,
            filter = VideoListOptions.Filter.CHINESE_SUBTITLE,
        )

        store.put(query)

        assertEquals(query, store.get("/cn/new"))
        assertEquals(query.copy(path = "/new"), store.get("/new"))
        assertEquals(VideoListQuery("/latest"), store.get("/latest"))
    }

    @Test
    fun `actress genre and maker lists each share preferences within their kind`() {
        val store = ListQueryStore(context)
        val actressQuery = VideoListQuery(
            path = "/actresses/one",
            sort = VideoListOptions.Sort.SAVED,
            filter = VideoListOptions.Filter.CHINESE_SUBTITLE,
        )
        val genreQuery = VideoListQuery(
            path = "/genres/one",
            sort = VideoListOptions.Sort.VIEWS,
            filter = VideoListOptions.Filter.INDIVIDUAL,
        )
        val makerQuery = VideoListQuery(
            path = "/makers/one",
            sort = VideoListOptions.Sort.RELEASED_AT,
            filter = VideoListOptions.Filter.MULTIPLE,
        )

        store.put(actressQuery)
        store.put(genreQuery)
        store.put(makerQuery)

        assertEquals(actressQuery.copy(path = "/actresses/two"), store.get("/actresses/two"))
        assertEquals(genreQuery.copy(path = "/genres/two"), store.get("/genres/two"))
        assertEquals(makerQuery.copy(path = "/makers/two"), store.get("/makers/two"))
    }

    @Test
    fun `clearing a query option removes its stored value`() {
        val store = ListQueryStore(context)
        store.put(
            VideoListQuery(
                path = "/new",
                sort = VideoListOptions.Sort.SAVED,
                filter = VideoListOptions.Filter.CHINESE_SUBTITLE,
            ),
        )

        store.put(VideoListQuery("/new"))

        assertEquals(VideoListQuery("/new"), store.get("/new"))
    }

    private fun preferences() = context.getSharedPreferences("missu:list-query", Context.MODE_PRIVATE)
}
