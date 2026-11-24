package app.missu.ui.home

import app.missu.R
import app.missu.data.store.UsageCategory
import app.missu.data.store.UsageDay
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIntroContentTest {
    private val date = LocalDate.of(2026, 11, 24)

    @Test
    fun `insufficient history uses the time of day`() {
        val intro = selectHomeIntro(
            date = date,
            time = LocalTime.of(8, 0),
            usageDays = usage(UsageCategory.VIDEOS, days = 2, minutesPerDay = 40),
        )

        assertTrue(intro.titleRes == R.string.home_intro_title || intro.titleRes == R.string.home_intro_morning_2_title)
    }

    @Test
    fun `dominant recent content selects its own copy`() {
        val intro = selectHomeIntro(
            date = date,
            time = LocalTime.of(8, 0),
            usageDays = usage(UsageCategory.VIDEOS, days = 3, minutesPerDay = 20),
        )

        assertTrue(
            intro.titleRes == R.string.home_intro_videos_1_title ||
                intro.titleRes == R.string.home_intro_videos_2_title,
        )
    }

    @Test
    fun `balanced content uses the balanced fallback`() {
        val usage = UsageCategory.entries.filter { it != UsageCategory.OTHER }.flatMap { category ->
            usage(category, days = 3, minutesPerDay = 20)
        }

        val intro = selectHomeIntro(date, LocalTime.of(23, 0), usage)

        assertTrue(
            intro.titleRes == R.string.home_intro_balanced_1_title ||
                intro.titleRes == R.string.home_intro_balanced_2_title,
        )
    }

    @Test
    fun `the same date and scenario keep the same copy`() {
        val usage = usage(UsageCategory.LITERATURE, days = 3, minutesPerDay = 20)
        val time = LocalTime.of(20, 0)

        assertEquals(
            selectHomeIntro(date, time, usage),
            selectHomeIntro(date, time, usage),
        )
    }

    private fun usage(category: UsageCategory, days: Int, minutesPerDay: Long): List<UsageDay> =
        (0 until days).map { offset ->
            UsageDay(date.minusDays(offset.toLong()), category, minutesPerDay * 60_000L)
        }
}
