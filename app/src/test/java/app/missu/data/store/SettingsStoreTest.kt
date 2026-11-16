package app.missu.data.store

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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

    private fun preferences() = context.getSharedPreferences("missu:settings", Context.MODE_PRIVATE)
}
