@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.HomeSection
import app.missu.ui.common.UiMessage
import app.missu.ui.common.text
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.TooltipIconButton
import app.missu.ui.components.VideoGridCard
import app.missu.ui.search.LocalSearchBarInset

private const val SECTION_PREVIEW_ROWS = 3

@Composable
fun HomeScreen(
    state: HomeUiState,
    refreshing: Boolean,
    refreshingRandom: Boolean,
    randomRefreshError: UiMessage?,
    onRefresh: () -> Unit,
    onRefreshRandom: () -> Unit,
    onClearRandomRefreshError: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    onOpenList: (path: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageStateTransition(state = state, modifier = modifier.fillMaxSize()) { current ->
        when (current) {
            HomeUiState.Loading -> LoadingHint()

            is HomeUiState.Error -> ErrorHint(current.message.text(), onRetry)

            is HomeUiState.Content -> HomeContent(
                sections = current.sections,
                refreshing = refreshing,
                refreshingRandom = refreshingRandom,
                randomRefreshError = randomRefreshError,
                onRefresh = onRefresh,
                onRefreshRandom = onRefreshRandom,
                onClearRandomRefreshError = onClearRandomRefreshError,
                onOpen = onOpen,
                onOpenList = onOpenList,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    refreshing: Boolean,
    refreshingRandom: Boolean,
    randomRefreshError: UiMessage?,
    onRefresh: () -> Unit,
    onRefreshRandom: () -> Unit,
    onClearRandomRefreshError: () -> Unit,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    onOpenList: (path: String, title: String) -> Unit,
    modifier: Modifier,
) {
    val barInset = LocalSearchBarInset.current
    val columns = LocalVideoListColumns.current
    val pullState = rememberPullToRefreshState()
    val snackbarState = remember { SnackbarHostState() }
    if (randomRefreshError != null) {
        val message = randomRefreshError.text()
        val retry = stringResource(R.string.common_retry)
        LaunchedEffect(randomRefreshError) {
            val result = snackbarState.showSnackbar(
                message = message,
                actionLabel = retry,
                duration = SnackbarDuration.Short,
            )
            onClearRandomRefreshError()
            if (result == SnackbarResult.ActionPerformed) onRefreshRandom()
        }
    }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = barInset),
                )
            },
        ) {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = barInset, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                sections.forEach { section ->
                    homeSection(
                        section = section,
                        columns = columns,
                        onMore = section.morePath?.let { path -> { onOpenList(path, section.title) } },
                        refreshingRandom = refreshingRandom,
                        onRefreshRandom = onRefreshRandom.takeIf { section.id == "random" },
                        onOpen = onOpen,
                    )
                }
            }
        }
        SnackbarHost(snackbarState, Modifier.align(Alignment.BottomCenter))
    }
}

private fun LazyGridScope.homeSection(
    section: HomeSection,
    columns: Int,
    onMore: (() -> Unit)?,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    refreshingRandom: Boolean = false,
    onRefreshRandom: (() -> Unit)? = null,
) {
    item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
        HomeSectionHeader(
            title = section.title,
            onMore = onMore,
            refreshingRandom = refreshingRandom,
            onRefreshRandom = onRefreshRandom,
        )
    }

    items(
        items = section.videos.take(SECTION_PREVIEW_ROWS * columns),
        key = { video -> "${section.id}-${video.id}" },
    ) { video ->
        VideoGridCard(
            video = video,
            heroTag = section.id,
            onClick = { onOpen(video.id, video.thumbnailUrl, section.id) },
        )
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    onMore: (() -> Unit)?,
    refreshingRandom: Boolean = false,
    onRefreshRandom: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (onRefreshRandom != null) {
            if (refreshingRandom) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(24.dp))
                }
            } else {
                TooltipIconButton(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.home_refresh_random),
                    onClick = onRefreshRandom,
                )
            }
        }
        if (onMore != null) {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_more),
                onClick = onMore,
            )
        }
    }
}

