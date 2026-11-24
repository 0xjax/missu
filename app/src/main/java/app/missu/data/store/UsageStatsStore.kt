package app.missu.data.store

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UsageCategory {
    LITERATURE,
    VIDEOS,
    COMICS,
    OTHER,
}

data class UsageDay(
    val date: LocalDate,
    val category: UsageCategory,
    val durationMillis: Long,
)

fun usageTotal(
    days: List<UsageDay>,
    endDate: LocalDate,
    dayCount: Int,
    category: UsageCategory? = null,
): Long {
    val startDate = endDate.minusDays(dayCount - 1L)
    return days.filter {
        !it.date.isBefore(startDate) && !it.date.isAfter(endDate) &&
            (category == null || it.category == category)
    }.sumOf { it.durationMillis }
}

fun usageAverage(
    days: List<UsageDay>,
    endDate: LocalDate,
    dayCount: Int,
    category: UsageCategory? = null,
): Long = usageTotal(days, endDate, dayCount, category) / dayCount

class UsageStatsStore internal constructor(
    context: Context,
    private val currentDate: () -> LocalDate = { LocalDate.now() },
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val preferences = context.getSharedPreferences("missu:usage-stats", Context.MODE_PRIVATE)
    private val _days = MutableStateFlow(readDays())
    val days: StateFlow<List<UsageDay>> = _days.asStateFlow()

    private var sessionStartedAt: Long? = null
    private var sessionCategory = UsageCategory.OTHER

    fun startSession() {
        if (sessionStartedAt == null) sessionStartedAt = elapsedRealtime()
    }

    fun setCategory(category: UsageCategory) {
        if (sessionCategory == category) return
        checkpoint()
        sessionCategory = category
    }

    fun checkpoint() {
        val startedAt = sessionStartedAt ?: return
        val now = elapsedRealtime()
        val duration = (now - startedAt).coerceAtLeast(0L)
        sessionStartedAt = now
        if (duration > 0L) addDuration(duration, sessionCategory)
    }

    fun endSession() {
        checkpoint()
        sessionStartedAt = null
    }

    fun clear() {
        preferences.edit {
            preferences.all.keys.filter { it.startsWith(DAY_PREFIX) }.forEach(::remove)
        }
        _days.value = emptyList()
        if (sessionStartedAt != null) sessionStartedAt = elapsedRealtime()
    }

    private fun addDuration(duration: Long, category: UsageCategory) {
        val date = currentDate()
        val key = dayKey(date, category)
        val total = preferences.getLong(key, 0L) + duration
        preferences.edit { putLong(key, total) }
        prune()
        _days.value = (
            _days.value.filterNot { it.date == date && it.category == category } +
                UsageDay(date, category, total)
            )
            .filterNot { it.date.isBefore(retentionCutoff()) }
            .sortedWith(compareByDescending<UsageDay> { it.date }.thenBy { it.category })
    }

    private fun readDays(): List<UsageDay> {
        migrateLegacyEntries()
        prune()
        return preferences.all.mapNotNull { (key, value) ->
            if (value !is Long || value < 0L) return@mapNotNull null
            val (date, category) = parseDayKey(key) ?: return@mapNotNull null
            UsageDay(date, category, value)
        }.sortedWith(compareByDescending<UsageDay> { it.date }.thenBy { it.category })
    }

    private fun migrateLegacyEntries() {
        val migrated = mutableMapOf<LocalDate, Long>()
        val oldKeys = mutableListOf<String>()
        preferences.all.forEach { (key, value) ->
            if (!key.startsWith(DAY_PREFIX) || value !is Long || value < 0L) return@forEach
            val rawValue = key.removePrefix(DAY_PREFIX)
            val isDailyTotal = '.' !in rawValue
            val isUnclassified = rawValue.endsWith(".UNCLASSIFIED")
            if (!isDailyTotal && !isUnclassified) return@forEach
            val date = runCatching { LocalDate.parse(rawValue.substringBefore('.')) }.getOrNull()
                ?: return@forEach
            migrated[date] = migrated.getOrElse(date) { 0L } + value
            oldKeys += key
        }
        preferences.edit {
            oldKeys.forEach(::remove)
            migrated.forEach { (date, duration) ->
                val key = dayKey(date, UsageCategory.OTHER)
                putLong(key, preferences.getLong(key, 0L) + duration)
            }
        }
    }

    private fun prune() {
        val cutoff = retentionCutoff()
        preferences.edit {
            preferences.all.keys.filter { key ->
                if (!key.startsWith(DAY_PREFIX)) return@filter false
                val entry = parseDayKey(key)
                entry == null || entry.first.isBefore(cutoff)
            }.forEach(::remove)
        }
    }

    private fun parseDayKey(key: String): Pair<LocalDate, UsageCategory>? {
        if (!key.startsWith(DAY_PREFIX)) return null
        val value = key.removePrefix(DAY_PREFIX)
        val date = runCatching { LocalDate.parse(value.substringBefore('.')) }.getOrNull() ?: return null
        val category = UsageCategory.entries.firstOrNull { it.name == value.substringAfter('.', "") }
            ?: return null
        return date to category
    }

    private fun dayKey(date: LocalDate, category: UsageCategory) = "$DAY_PREFIX$date.${category.name}"

    private fun retentionCutoff(): LocalDate = currentDate().minusDays(RETAINED_DAYS - 1L)

    private companion object {
        const val DAY_PREFIX = "day."
        const val RETAINED_DAYS = 90L
    }
}
