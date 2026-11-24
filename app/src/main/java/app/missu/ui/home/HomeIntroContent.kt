package app.missu.ui.home

import androidx.annotation.StringRes
import app.missu.R
import app.missu.data.store.UsageCategory
import app.missu.data.store.UsageDay
import java.time.LocalDate
import java.time.LocalTime

private enum class HomeIntroScenario {
    MORNING,
    DAYTIME,
    EVENING,
    NIGHT,
    LITERATURE,
    VIDEOS,
    COMICS,
    BALANCED,
}

data class HomeIntroContent(
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
)

fun selectHomeIntro(
    date: LocalDate,
    time: LocalTime,
    usageDays: List<UsageDay>,
): HomeIntroContent {
    val scenario = contentScenario(date, usageDays) ?: timeScenario(time)
    return when (scenario to variant(date, scenario)) {
        HomeIntroScenario.MORNING to 0 -> HomeIntroContent(R.string.home_intro_title, R.string.home_intro_body)
        HomeIntroScenario.MORNING to 1 -> HomeIntroContent(
            R.string.home_intro_morning_2_title,
            R.string.home_intro_morning_2_body,
        )
        HomeIntroScenario.DAYTIME to 0 -> HomeIntroContent(
            R.string.home_intro_daytime_1_title,
            R.string.home_intro_daytime_1_body,
        )
        HomeIntroScenario.DAYTIME to 1 -> HomeIntroContent(
            R.string.home_intro_daytime_2_title,
            R.string.home_intro_daytime_2_body,
        )
        HomeIntroScenario.EVENING to 0 -> HomeIntroContent(
            R.string.home_intro_evening_1_title,
            R.string.home_intro_evening_1_body,
        )
        HomeIntroScenario.EVENING to 1 -> HomeIntroContent(
            R.string.home_intro_evening_2_title,
            R.string.home_intro_evening_2_body,
        )
        HomeIntroScenario.NIGHT to 0 -> HomeIntroContent(
            R.string.home_intro_night_1_title,
            R.string.home_intro_night_1_body,
        )
        HomeIntroScenario.NIGHT to 1 -> HomeIntroContent(
            R.string.home_intro_night_2_title,
            R.string.home_intro_night_2_body,
        )
        HomeIntroScenario.LITERATURE to 0 -> HomeIntroContent(
            R.string.home_intro_literature_1_title,
            R.string.home_intro_literature_1_body,
        )
        HomeIntroScenario.LITERATURE to 1 -> HomeIntroContent(
            R.string.home_intro_literature_2_title,
            R.string.home_intro_literature_2_body,
        )
        HomeIntroScenario.VIDEOS to 0 -> HomeIntroContent(
            R.string.home_intro_videos_1_title,
            R.string.home_intro_videos_1_body,
        )
        HomeIntroScenario.VIDEOS to 1 -> HomeIntroContent(
            R.string.home_intro_videos_2_title,
            R.string.home_intro_videos_2_body,
        )
        HomeIntroScenario.COMICS to 0 -> HomeIntroContent(
            R.string.home_intro_comics_1_title,
            R.string.home_intro_comics_1_body,
        )
        HomeIntroScenario.COMICS to 1 -> HomeIntroContent(
            R.string.home_intro_comics_2_title,
            R.string.home_intro_comics_2_body,
        )
        HomeIntroScenario.BALANCED to 0 -> HomeIntroContent(
            R.string.home_intro_balanced_1_title,
            R.string.home_intro_balanced_1_body,
        )
        else -> HomeIntroContent(
            R.string.home_intro_balanced_2_title,
            R.string.home_intro_balanced_2_body,
        )
    }
}

private fun contentScenario(date: LocalDate, usageDays: List<UsageDay>): HomeIntroScenario? {
    val recent = usageDays.filter {
        it.category != UsageCategory.OTHER && !it.date.isBefore(date.minusDays(6)) &&
            !it.date.isAfter(date) && it.durationMillis > 0L
    }
    if (recent.map { it.date }.distinct().size < MIN_ACTIVE_DAYS) return null
    if (recent.sumOf { it.durationMillis } < MIN_CONTENT_MILLIS) return null

    val totals = contentCategories.map { category ->
        category to recent.filter { it.category == category }.sumOf { it.durationMillis }
    }
    val total = totals.sumOf { it.second }
    val dominant = totals.maxByOrNull { it.second }?.takeIf { it.second * 100 >= total * DOMINANT_PERCENT }
        ?: return HomeIntroScenario.BALANCED
    return when (dominant.first) {
        UsageCategory.LITERATURE -> HomeIntroScenario.LITERATURE
        UsageCategory.VIDEOS -> HomeIntroScenario.VIDEOS
        UsageCategory.COMICS -> HomeIntroScenario.COMICS
        UsageCategory.OTHER -> null
    }
}

private fun timeScenario(time: LocalTime): HomeIntroScenario = when (time.hour) {
    in 5..10 -> HomeIntroScenario.MORNING
    in 11..17 -> HomeIntroScenario.DAYTIME
    in 18..22 -> HomeIntroScenario.EVENING
    else -> HomeIntroScenario.NIGHT
}

private fun variant(date: LocalDate, scenario: HomeIntroScenario): Int =
    ((date.toEpochDay() + scenario.ordinal) % VARIANT_COUNT).toInt()

private val contentCategories = listOf(
    UsageCategory.LITERATURE,
    UsageCategory.VIDEOS,
    UsageCategory.COMICS,
)

private const val MIN_ACTIVE_DAYS = 3
private const val MIN_CONTENT_MILLIS = 30L * 60_000L
private const val DOMINANT_PERCENT = 60L
private const val VARIANT_COUNT = 2
