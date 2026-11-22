package app.missu.ui.comics

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.comic.ComicFeed
import app.missu.data.comic.ComicSource
import app.missu.ui.components.EmptyHint
import app.missu.ui.list.VideoOptionMenu
import app.missu.ui.list.VideoToolbarContainer
import app.missu.ui.search.LocalSearchBarInset
import app.missu.ui.search.SearchHostShell
import app.missu.ui.search.SearchTopVeil
import app.missu.ui.search.SearchTriggerHeight

@Composable
fun ComicNavigationScreen(
    source: ComicSource,
    feed: ComicFeed,
    state: ComicListUiState,
    searchState: ComicListUiState,
    keyword: String,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    onLoad: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSearchLoadMore: () -> Unit,
    onOpen: (ComicSource, String) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
) {
    LaunchedEffect(source, feed) { onLoad() }
    SearchHostShell(
        searching = keyword.isNotBlank(),
        onSearch = onSearch,
        onClear = onClearSearch,
        modifier = modifier,
        initialKeyword = keyword,
        placeholder = stringResource(R.string.comic_search_placeholder),
        inlineTrigger = true,
        idleContent = {
            EmptyHint(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.comic_search_hint),
                subtitle = source.displayName,
            )
        },
    ) { searchAction ->
        if (keyword.isBlank()) {
            ComicBrowseContent(
                scrollToTopSignal = scrollToTopSignal,
                source = source,
                feed = feed,
                state = state,
                onSourceChange = onSourceChange,
                onFeedChange = onFeedChange,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                searchAction = searchAction,
            )
        } else {
            ComicSearchResults(
                scrollToTopSignal = scrollToTopSignal,
                source = source,
                state = searchState,
                keyword = keyword,
                onSourceChange = onSourceChange,
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
    state: ComicListUiState,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ComicSource, String) -> Unit,
    searchAction: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ComicListBody(
            state = state,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onOpen = { onOpen(source, it.id) },
            contentTopInset = LocalSearchBarInset.current,
            scrollToTopSignal = scrollToTopSignal,
        )
        ComicTopVeil()
        ComicToolbar(
            source = source,
            feed = feed,
            onSourceChange = onSourceChange,
            onFeedChange = onFeedChange,
            searchAction = searchAction,
        )
    }
}

@Composable
private fun ComicSearchResults(
    scrollToTopSignal: Int,
    source: ComicSource,
    state: ComicListUiState,
    keyword: String,
    onSourceChange: (ComicSource) -> Unit,
    onRetry: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ComicSource, String) -> Unit,
    searchAction: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ComicListBody(
            state = state,
            onRetry = { onRetry(keyword) },
            onLoadMore = onLoadMore,
            onOpen = { onOpen(source, it.id) },
            contentTopInset = LocalSearchBarInset.current,
            scrollToTopSignal = scrollToTopSignal,
        )
        ComicTopVeil()
        ComicToolbar(
            source = source,
            feed = null,
            onSourceChange = onSourceChange,
            onFeedChange = {},
            searchAction = searchAction,
        )
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
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
    searchAction: @Composable () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().padding(top = statusTop)) {
        VideoToolbarContainer {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ComicOptions(source, feed, onSourceChange, onFeedChange)
                Spacer(Modifier.width(12.dp))
                searchAction()
            }
        }
    }
}

@Composable
private fun RowScope.ComicOptions(
    source: ComicSource,
    feed: ComicFeed?,
    onSourceChange: (ComicSource) -> Unit,
    onFeedChange: (ComicFeed) -> Unit,
) {
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
        if (feed != null && source.supportsLatest) {
            VideoOptionMenu(
                text = stringResource(if (feed == ComicFeed.POPULAR) R.string.comic_popular else R.string.comic_latest),
                options = ComicFeed.entries,
                selected = feed,
                onSelect = onFeedChange,
                optionLabel = {
                    stringResource(if (it == ComicFeed.POPULAR) R.string.comic_popular else R.string.comic_latest)
                },
            )
        }
    }
}
