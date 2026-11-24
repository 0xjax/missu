package app.missu.data.store

import android.content.Context
import java.time.LocalDate
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
@ConscryptMode(ConscryptMode.Mode.OFF)
class UsageStatsStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private var date = LocalDate.of(2026, 11, 24)
    private var elapsed = 0L

    @Before
    fun clearPreferences() {
        preferences().edit().clear().commit()
    }

    @After
    fun cleanUp() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `foreground sessions accumulate and persist`() {
        val store = store()
        store.startSession()
        elapsed += 65_000L
        store.checkpoint()
        store.endSession()

        assertEquals(65_000L, usageTotal(store.days.value, date, 1))
        assertEquals(65_000L, usageTotal(store().days.value, date, 1))
    }

    @Test
    fun `stopped sessions do not accumulate twice`() {
        val store = store()
        store.startSession()
        elapsed += 60_000L
        store.endSession()
        store.endSession()
        elapsed += 30_000L
        store.startSession()
        elapsed += 30_000L
        store.endSession()

        assertEquals(90_000L, usageTotal(store.days.value, date, 1))
    }

    @Test
    fun `clearing during a session excludes time before the clear`() {
        val store = store()
        store.startSession()
        elapsed += 120_000L

        store.clear()
        elapsed += 1_000L
        store.endSession()

        assertEquals(1_000L, usageTotal(store.days.value, date, 1))
        assertEquals(1, store.days.value.size)
    }

    @Test
    fun `daily totals and averages include zero usage days`() {
        val store = store()
        store.startSession()
        elapsed += 70_000L
        store.endSession()
        date = date.minusDays(6)
        store.startSession()
        elapsed += 70_000L
        store.endSession()

        val today = LocalDate.of(2026, 11, 24)
        assertEquals(140_000L, usageTotal(store.days.value, today, 7))
        assertEquals(20_000L, usageAverage(store.days.value, today, 7))
        assertEquals(70_000L, usageTotal(store.days.value, today, 1))
    }

    @Test
    fun `category changes settle the previous category`() {
        val store = store()
        store.startSession()
        elapsed += 30_000L
        store.setCategory(UsageCategory.VIDEOS)
        elapsed += 60_000L
        store.setCategory(UsageCategory.LITERATURE)
        elapsed += 30_000L
        store.endSession()

        assertEquals(30_000L, usageTotal(store.days.value, date, 1, UsageCategory.OTHER))
        assertEquals(60_000L, usageTotal(store.days.value, date, 1, UsageCategory.VIDEOS))
        assertEquals(30_000L, usageTotal(store.days.value, date, 1, UsageCategory.LITERATURE))
        assertEquals(120_000L, usageTotal(store.days.value, date, 1))
    }

    @Test
    fun `legacy totals migrate and merge into other`() {
        preferences().edit()
            .putLong("day.$date", 45_000L)
            .putLong("day.$date.UNCLASSIFIED", 15_000L)
            .putLong("day.$date.OTHER", 5_000L)
            .commit()

        val store = store()

        assertEquals(65_000L, usageTotal(store.days.value, date, 1))
        assertEquals(65_000L, usageTotal(store.days.value, date, 1, UsageCategory.OTHER))
        assertEquals(UsageCategory.OTHER, store.days.value.single().category)
        assertEquals(65_000L, usageTotal(store().days.value, date, 1))
    }

    private fun store() = UsageStatsStore(context, { date }, { elapsed })

    private fun preferences() = context.getSharedPreferences("missu:usage-stats", Context.MODE_PRIVATE)
}
