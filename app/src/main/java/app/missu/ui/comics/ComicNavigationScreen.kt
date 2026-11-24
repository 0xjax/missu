@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.comics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.comic.ComicFeed
import app.missu.data.comic.ComicSource
import app.missu.data.comic.KalamCategory
import app.missu.ui.common.text
import app.missu.ui.components.LocalFloatingNavigationBarInset
import app.missu.ui.list.ToolbarOptionsButton
import app.missu.ui.list.ToolbarOptionsSheet
import app.missu.ui.list.ToolbarRadioGroup
import app.missu.ui.list.VideoOptionMenu
import app.missu.ui.components.LiquidToolbar
import app.missu.ui.search.LocalSearchBarInset
import app.missu.ui.search.RecentSearches
import app.missu.ui.search.SearchHostShell
import app.missu.ui.search.SearchTopVeil
import app.missu.ui.search.SearchTriggerHeight
import app.missu.ui.search.searchGlassSource

@Composable
fun ComicNavigationScreen(
    source: ComicSource,
    feed: ComicFeed,
    category: KalamCategory,
    state: ComicListUiState,
    searchState: ComicListUiState,
    aggregateSearchState: Map<ComicSource, ComicListUiState>,
    searchScope: ComicSearchScope,
    keyword: String,
    recent: List<String>,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    onCategoryChange: (KalamCategory) -> Unit,
    onLoad: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onClearSearchHistory: () -> Unit,
    onSearchScopeChange: (ComicSearchScope) -> Unit,
    onOpenSourceSearch: (ComicSource) -> Unit,
    onRetrySourceSearch: (ComicSource) -> Unit,
    onSearchLoadMore: () -> Unit,
    onOpen: (ComicSource, String) -> Unit,
    libraryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
) {
    LaunchedEffect(source, feed, category) { onLoad() }
    SearchHostShell(
        searching = keyword.isNotBlank(),
        onSearch = onSearch,
        onClear = onClearSearch,
        modifier = modifier,
        initialKeyword = keyword,
        placeholder = stringResource(R.string.comic_search_placeholder),
        inlineTrigger = true,
        idleContent = { pick ->
            RecentSearches(
                keywords = recent,
                onPick = pick,
                onClear = onClearSearchHistory,
            )
        },
    ) { searchAction ->
        if (keyword.isBlank()) {
            ComicBrowseContent(
                scrollToTopSignal = scrollToTopSignal,
                source = source,
                feed = feed,
                category = category,
                state = state,
                onSourceChange = onSourceChange,
                onFeedChange = onFeedChange,
                onCategoryChange = onCategoryChange,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                libraryAction = libraryAction,
                searchAction = searchAction,
            )
        } else {
            ComicSearchResults(
                scrollToTopSignal = scrollToTopSignal,
                source = source,
                state = searchState,
                aggregateState = aggregateSearchState,
                scope = searchScope,
                keyword = keyword,
                onSourceChange = onSourceChange,
                onScopeChange = onSearchScopeChange,
                onOpenSourceSearch = onOpenSourceSearch,
                onRetrySourceSearch = onRetrySourceSearch,
                onRetry = onSearch,
                onLoadMore = onSearchLoadMore,
                onOpen = onOpen,
                searchAction = searchAction,
            )
        }
    }
}

@Composable
private fun ComicBrowseContent(
    scrollToTopSignal: Int,
    source: ComicSource,
    feed: ComicFeed,
    category: KalamCategory,
    state: ComicListUiState,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    onCategoryChange: (KalamCategory) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ComicSource, String) -> Unit,
    libraryAction: @Composable () -> Unit,
    searchAction: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ComicListBody(
            state = state,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onOpen = { onOpen(source, it.id) },
            modifier = Modifier.searchGlassSource(),
            contentTopInset = LocalSearchBarInset.current,
            scrollToTopSignal = scrollToTopSignal,
        )
        ComicTopVeil()
        ComicToolbar(
            source = source,
            feed = feed,
            category = category,
            onSourceChange = onSourceChange,
            onFeedChange = onFeedChange,
            onCategoryChange = onCategoryChange,
            libraryAction = libraryAction,
            searchAction = searchAction,
        )
    }
}

@Composable
private fun ComicSearchResults(
    scrollToTopSignal: Int,
    source: ComicSource,
    state: ComicListUiState,
    aggregateState: Map<ComicSource, ComicListUiState>,
    scope: ComicSearchScope,
    keyword: String,
    onSourceChange: (ComicSource) -> Unit,
    onScopeChange: (ComicSearchScope) -> Unit,
    onOpenSourceSearch: (ComicSource) -> Unit,
    onRetrySourceSearch: (ComicSource) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ComicSource, String) -> Unit,
    searchAction: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (scope == ComicSearchScope.ALL) {
            ComicAggregateResults(
                states = aggregateState,
                scrollToTopSignal = scrollToTopSignal,
                onOpen = onOpen,
                onViewAll = onOpenSourceSearch,
                onRetry = onRetrySourceSearch,
            )
        } else {
            ComicListBody(
                state = state,
                onRetry = { onRetry(keyword) },
                onLoadMore = onLoadMore,
                onOpen = { onOpen(source, it.id) },
                modifier = Modifier.searchGlassSource(),
                contentTopInset = LocalSearchBarInset.current,
                scrollToTopSignal = scrollToTopSignal,
            )
        }
        ComicTopVeil()
        Box(Modifier.fillMaxWidth().padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
            LiquidToolbar {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (scope == ComicSearchScope.CURRENT) {
                            VideoOptionMenu(
                                text = source.displayName,
                                options = ComicSource.entries,
                                selected = source,
                                onSelect = onSourceChange,
                                optionLabel = { it.displayName },
                            )
                        }
                        VideoOptionMenu(
                            text = stringResource(scope.labelRes),
                            options = ComicSearchScope.entries,
                            selected = scope,
                            onSelect = onScopeChange,
                            optionLabel = { stringResource(it.labelRes) },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    searchAction()
                }
            }
        }
    }
}

@Composable
private fun ComicAggregateResults(
    states: Map<ComicSource, ComicListUiState>,
    scrollToTopSignal: Int,
    onOpen: (ComicSource, String) -> Unit,
    onViewAll: (ComicSource) -> Unit,
    onRetry: (ComicSource) -> Unit,
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) gridState.animateScrollToItem(0)
    }
    LazyVerticalGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize().searchGlassSource(),
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = LocalSearchBarInset.current + 12.dp,
            bottom = LocalFloatingNavigationBarInset.current + 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ComicSource.entries.forEach { source ->
            val state = states[source] ?: ComicListUiState.Loading
            item(key = "header-${source.id}", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = source.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (state is ComicListUiState.Content) {
                        TextButton(onClick = { onViewAll(source) }) {
                            Text(stringResource(R.string.comic_search_view_all))
                        }
                    }
                }
            }
            when (state) {
                ComicListUiState.Loading -> item(key = "loading-${source.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(36.dp))
                    }
                }
                ComicListUiState.Empty -> item(key = "empty-${source.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.comic_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ComicListUiState.Error -> item(key = "error-${source.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = state.message.text(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRetry(source) }) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
                is ComicListUiState.Content -> items(
                    items = state.items.take(3),
                    key = { "${source.id}:${it.id}" },
                ) { comic ->
                    ComicGridCard(comic, onClick = { onOpen(source, comic.id) })
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ComicTopVeil() {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    SearchTopVeil(
        statusTop = statusTop,
        veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
        triggerHeight = SearchTriggerHeight,
    )
}

@Composable
private fun ComicToolbar(
    source: ComicSource,
    feed: ComicFeed?,
    category: KalamCategory,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    onCategoryChange: (KalamCategory) -> Unit,
    libraryAction: @Composable () -> Unit,
    searchAction: @Composable () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().padding(top = statusTop)) {
        LiquidToolbar {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComicOptions(
                    source = source,
                    feed = feed,
                    category = category,
                    onSourceChange = onSourceChange,
                    onFeedChange = onFeedChange,
                    onCategoryChange = onCategoryChange,
                    libraryAction = libraryAction,
                    searchAction = searchAction,
                )
            }
        }
    }
}

@Composable
private fun RowScope.ComicOptions(
    source: ComicSource,
    feed: ComicFeed?,
    category: KalamCategory,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    onCategoryChange: (KalamCategory) -> Unit,
    libraryAction: @Composable () -> Unit,
    searchAction: @Composable () -> Unit,
) {
    searchAction()
    Row(
        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        VideoOptionMenu(
            text = source.displayName,
            options = ComicSource.entries,
            selected = source,
            onSelect = onSourceChange,
            optionLabel = { it.displayName },
        )
    }
    Spacer(Modifier.width(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (feed != null && (source.supportsLatest || source == ComicSource.KALAM)) {
            ToolbarOptionsButton { dismiss ->
                ToolbarOptionsSheet {
                    if (source.supportsLatest) {
                        ToolbarRadioGroup(
                            title = stringResource(R.string.comic_feed_title),
                            options = ComicFeed.entries,
                            selected = feed,
                            onSelect = { value ->
                                value?.let(onFeedChange)
                                dismiss()
                            },
                            optionLabel = {
                                stringResource(if (it == ComicFeed.POPULAR) R.string.comic_popular else R.string.comic_latest)
                            },
                        )
                    }
                    if (source == ComicSource.KALAM) {
                        ToolbarRadioGroup(
                            title = stringResource(R.string.comic_category_title),
                            options = KalamCategory.entries,
                            selected = category,
                            onSelect = { value ->
                                value?.let(onCategoryChange)
                                dismiss()
                            },
                            optionLabel = { stringResource(it.labelRes) },
                        )
                    }
                }
            }
        }
        libraryAction()
    }
}

private val ComicSearchScope.labelRes: Int
    get() = when (this) {
        ComicSearchScope.CURRENT -> R.string.comic_search_current_source
        ComicSearchScope.ALL -> R.string.comic_search_all_sources
    }

private val KalamCategory.labelRes: Int
    get() = when (this) {
        KalamCategory.ALL -> R.string.comic_category_all
        KalamCategory.KOREAN -> R.string.comic_category_korean
        KalamCategory.JAPANESE -> R.string.comic_category_japanese
        KalamCategory.CHINESE -> R.string.comic_category_chinese
        KalamCategory.WESTERN -> R.string.comic_category_western
        KalamCategory.OTHER -> R.string.comic_category_other
    }
