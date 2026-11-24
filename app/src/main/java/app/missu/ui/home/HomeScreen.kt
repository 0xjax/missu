package app.missu.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.article.Article
import app.missu.data.store.UsageDay
import app.missu.data.store.usageAverage
import app.missu.data.store.usageTotal
import app.missu.ui.components.LiquidGlassIsland
import app.missu.ui.components.LocalFloatingNavigationBarInset
import app.missu.ui.components.TooltipIconButton
import app.missu.ui.components.LiquidToolbar
import app.missu.ui.search.LocalSearchGlassState
import app.missu.ui.search.SearchBarTopGap
import app.missu.ui.search.SearchTopVeil
import app.missu.ui.search.SearchToolbarChromeHeight
import app.missu.ui.search.SearchTriggerHeight
import app.missu.ui.usage.formatUsageDuration
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.LocalDate

@Composable
fun HomeScreen(
    intro: HomeIntroContent,
    usageDays: List<UsageDay>,
    article: Article?,
    onOpenGuide: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onShuffleArticle: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
) {
    val listState = rememberLazyListState()
    val glassState = rememberHazeState()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
    CompositionLocalProvider(LocalSearchGlassState provides glassState) {
        Box(modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(glassState),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = statusTop + SearchToolbarChromeHeight + SearchBarTopGap + 24.dp,
                    bottom = LocalFloatingNavigationBarInset.current + 32.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") { HomeIntro(intro) }
                item(key = "usage") {
                    val today = LocalDate.now()
                    HomeEntryCard(
                        icon = Icons.Outlined.Schedule,
                        title = stringResource(
                            R.string.usage_home_title,
                            formatUsageDuration(usageTotal(usageDays, today, 1)),
                        ),
                        body = stringResource(
                            R.string.usage_home_body,
                            formatUsageDuration(usageAverage(usageDays, today, 7)),
                        ),
                        onClick = onOpenUsage,
                    )
                }
                item(key = "guides-title") {
                    Text(
                        text = stringResource(R.string.home_guides_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                item(key = "guides") {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        guideCatalog.forEach { guide ->
                            GuideCard(
                                guide = guide,
                                onClick = { onOpenGuide(guide.id) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
                article?.let { current ->
                    item(key = "article-${current.id}") {
                        ArticleCard(
                            article = current,
                            onClick = { onOpenArticle(current.id) },
                            onShuffle = onShuffleArticle,
                        )
                    }
                }
            }
            SearchTopVeil(
                statusTop = statusTop,
                veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                triggerHeight = SearchToolbarChromeHeight,
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = statusTop)) {
                LiquidToolbar {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.weight(1f))
                        LiquidGlassIsland {
                            TooltipIconButton(
                                icon = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.account_settings),
                                onClick = onOpenSettings,
                                modifier = Modifier.size(SearchTriggerHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeIntro(intro: HomeIntroContent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.home_intro_kicker),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = stringResource(intro.titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(intro.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun GuideCard(guide: Guide, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuideIcon(icon = guide.icon)
            Text(
                text = stringResource(guide.categoryRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit, onShuffle: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GuideIcon(icon = Icons.Outlined.MenuBook)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_articles_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = article.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TooltipIconButton(
                    icon = Icons.Outlined.Shuffle,
                    contentDescription = stringResource(R.string.article_shuffle),
                    onClick = onShuffle,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(text = article.title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = article.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeEntryCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuideIcon(icon = icon)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuideIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
