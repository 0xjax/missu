package app.missu.data.store

import android.content.Context
import app.missu.data.api.VideoListOptions
import app.missu.data.model.PinnedHomeList
import app.missu.data.model.VideoListQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// Windows SAC 会拦 Robolectric 解压的未签名 Conscrypt DLL；本类不测 TLS。
@ConscryptMode(ConscryptMode.Mode.OFF)
class SettingsStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearSettings() {
        preferences().edit().clear().commit()
    }

    @After
    fun cleanUp() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `single column preference defaults to double and persists`() {
        assertFalse(SettingsStore(context).singleColumnVideos())

        SettingsStore(context).setSingleColumnVideos(true)

        assertTrue(SettingsStore(context).singleColumnVideos())
    }

    @Test
    fun `pinned home list persists its complete query`() {
        val store = SettingsStore(context)
        assertNull(store.pinnedHomeList())
        val pinned = PinnedHomeList(
            title = "Filtered",
            query = VideoListQuery(
                path = "/cn/new",
                sort = VideoListOptions.Sort.VIEWS,
                filter = VideoListOptions.Filter.CHINESE_SUBTITLE,
            ),
        )

        store.setPinnedHomeList(pinned)

        assertEquals(pinned, SettingsStore(context).pinnedHomeList())
    }

    private fun preferences() = context.getSharedPreferences("missu:settings", Context.MODE_PRIVATE)
}
