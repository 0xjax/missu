package app.missu.ui.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.store.UsageCategory
import app.missu.data.store.UsageDay
import app.missu.data.store.usageAverage
import app.missu.data.store.usageTotal
import app.missu.ui.components.GroupSurface
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.TooltipIconButton
import app.missu.ui.list.VideoControlButton
import app.missu.ui.theme.LocalDarkTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun UsageStatsScreen(
    days: List<UsageDay>,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var selectedCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedCategory = UsageCategory.entries.firstOrNull { it.name == selectedCategoryName }
    val today = LocalDate.now()

    Column(modifier.fillMaxSize()) {
        PageTopBar(
            title = stringResource(R.string.usage_title),
            onBack = onBack,
            actions = {
                TooltipIconButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.common_clear),
                    onClick = { showClearDialog = true },
                    enabled = days.isNotEmpty(),
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UsageTodayCard(days = days, today = today)
            UsageCategoryPicker(
                selected = selectedCategory,
                onSelect = { selectedCategoryName = it?.name },
            )
            UsagePeriod(days = days, today = today, dayCount = 7, category = selectedCategory)
            UsagePeriod(days = days, today = today, dayCount = 30, category = selectedCategory)

            Text(
                text = stringResource(R.string.usage_privacy_note),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.usage_clear_dialog_title)) },
            text = { Text(stringResource(R.string.usage_clear_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClear()
                        selectedCategoryName = null
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.common_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun UsageTodayCard(days: List<UsageDay>, today: LocalDate) {
    val values = UsageCategory.entries.map { it to usageTotal(days, today, 1, it) }
    GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.usage_today),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatUsageDuration(usageTotal(days, today, 1)),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.usage_daily_average,
                    formatUsageDuration(usageAverage(days, today, 7)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsageDonut(values)
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    values.forEach { (category, duration) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(usageCategoryColor(category), CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = usageCategoryLabel(category),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = formatUsageDuration(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageDonut(values: List<Pair<UsageCategory, Long>>) {
    val total = values.sumOf { it.second }.coerceAtLeast(1L)
    val segments = values.map { usageCategoryColor(it.first) to it.second }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val strokeWidth = 12.dp
    Canvas(modifier = Modifier.size(104.dp)) {
        val stroke = Stroke(width = strokeWidth.toPx())
        val diameter = minOf(size.width, size.height) - stroke.width
        val topLeft = Offset(stroke.width / 2f, stroke.width / 2f)
        val arcSize = Size(diameter, diameter)
        if (values.all { it.second == 0L }) {
            drawCircle(
                color = trackColor,
                radius = diameter / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = stroke,
            )
            return@Canvas
        }
        var startAngle = -90f
        segments.forEach { (color, duration) ->
            if (duration > 0L) {
                val sweep = (duration.toDouble() * 360.0 / total).toFloat()
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
    }
}

@Composable
private fun usageCategoryColor(category: UsageCategory): Color {
    val dark = LocalDarkTheme.current
    return when (category) {
        UsageCategory.LITERATURE -> if (dark) Color(0xFFFF8CA5) else Color(0xFF9B2C4D)
        UsageCategory.VIDEOS -> if (dark) Color(0xFF7AB8FF) else Color(0xFF00629E)
        UsageCategory.COMICS -> if (dark) Color(0xFFF2C14E) else Color(0xFF805900)
        UsageCategory.OTHER -> if (dark) Color(0xFFA9ABB3) else Color(0xFF666A72)
    }
}

@Composable
private fun UsageCategoryPicker(
    selected: UsageCategory?,
    onSelect: (UsageCategory?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = UsageCategory.entries
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.usage_trend_scope),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Box {
            VideoControlButton(
                text = usageCategoryLabel(selected),
                expanded = expanded,
                onClick = { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.usage_category_all),
                            color = if (selected == null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = usageCategoryLabel(category),
                                color = if (selected == category) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(category)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UsagePeriod(
    days: List<UsageDay>,
    today: LocalDate,
    dayCount: Int,
    category: UsageCategory?,
) {
    val titleRes = if (dayCount == 7) R.string.usage_last_7_days else R.string.usage_last_30_days
    GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                text = stringResource(titleRes),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.usage_period_summary,
                    formatUsageDuration(usageTotal(days, today, dayCount, category)),
                    formatUsageDuration(usageAverage(days, today, dayCount, category)),
                ),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UsageBarChart(days = days, today = today, dayCount = dayCount, category = category)
        }
    }
}

private data class UsageChartValue(val date: LocalDate, val durationMillis: Long)

@Composable
private fun UsageBarChart(
    days: List<UsageDay>,
    today: LocalDate,
    dayCount: Int,
    category: UsageCategory?,
) {
    val values = (dayCount - 1 downTo 0).map { offset ->
        val date = today.minusDays(offset.toLong())
        UsageChartValue(date, usageTotal(days, date, 1, category))
    }
    val maximum = values.maxOf { it.durationMillis }.coerceAtLeast(1L)
    val locale = LocalConfiguration.current.locales[0]
    val dayFormatter = DateTimeFormatter.ofPattern("E", locale)
    val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val spacing = if (dayCount == 7) 8.dp else 2.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (dayCount == 7) 144.dp else 104.dp)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { day ->
            val description = stringResource(
                R.string.usage_day_description,
                day.date.format(dateFormatter),
                formatUsageDuration(day.durationMillis),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics(mergeDescendants = true) { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (day.durationMillis > 0L) {
                        val fraction = (day.durationMillis.toDouble() / maximum)
                            .toFloat().coerceIn(0.04f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (dayCount == 7) 0.64f else 1f)
                                .fillMaxHeight(fraction)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (day.date == today) 1f else 0.5f,
                                    ),
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
                if (dayCount == 7) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = day.date.format(dayFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    if (dayCount == 30) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = values.first().date.format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = values.last().date.format(dateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun usageCategoryLabel(category: UsageCategory?): String = when (category) {
    null -> stringResource(R.string.usage_category_all)
    UsageCategory.LITERATURE -> stringResource(R.string.nav_literature)
    UsageCategory.VIDEOS -> stringResource(R.string.nav_videos)
    UsageCategory.COMICS -> stringResource(R.string.nav_comics)
    UsageCategory.OTHER -> stringResource(R.string.usage_category_other)
}

@Composable
fun formatUsageDuration(durationMillis: Long): String {
    val minutes = durationMillis / 60_000L
    if (durationMillis > 0L && minutes == 0L) {
        return stringResource(R.string.usage_duration_less_than_minute)
    }
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return when {
        hours > 0L && remainingMinutes > 0L -> stringResource(
            R.string.usage_duration_hours_minutes,
            hours,
            remainingMinutes,
        )
        hours > 0L -> stringResource(R.string.usage_duration_hours, hours)
        else -> stringResource(R.string.usage_duration_minutes, minutes)
    }
}
