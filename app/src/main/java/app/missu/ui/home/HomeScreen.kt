@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.HomeSection
import app.missu.data.model.PinnedHomeList
import app.missu.data.model.VideoListQuery
import app.missu.data.model.VideoSummary
import app.missu.ui.common.UiMessage
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.TooltipIconButton
import app.missu.ui.components.VideoGridCard
import app.missu.ui.components.heroKeyFor
import app.missu.ui.components.videoHero
import app.missu.ui.list.VideoListUiState
import app.missu.ui.search.LocalSearchBarInset
import coil3.compose.AsyncImage

private const val SECTION_PREVIEW_ROWS = 3

@Composable
fun HomeScreen(
    state: HomeUiState,
    pinned: PinnedHomeList?,
    pinnedState: VideoListUiState?,
    refreshing: Boolean,
    refreshingRandom: Boolean,
    randomRefreshError: UiMessage?,
    onRefresh: () -> Unit,
    onRefreshRandom: () -> Unit,
    onClearRandomRefreshError: () -> Unit,
    onRetryPinned: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    onOpenList: (path: String, title: String) -> Unit,
    onOpenPinnedList: (PinnedHomeList) -> Unit,
    onPin: (PinnedHomeList) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageStateTransition(state = state, modifier = modifier.fillMaxSize()) { current ->
        when (current) {
            HomeUiState.Loading -> LoadingHint()

            is HomeUiState.Error -> ErrorHint(current.message.text(), onRetry)

            is HomeUiState.Content -> HomeContent(
                sections = current.sections,
                pinned = pinned,
                pinnedState = pinnedState,
                refreshing = refreshing,
                refreshingRandom = refreshingRandom,
                randomRefreshError = randomRefreshError,
                onRefresh = onRefresh,
                onRefreshRandom = onRefreshRandom,
                onClearRandomRefreshError = onClearRandomRefreshError,
                onRetryPinned = onRetryPinned,
                onOpen = onOpen,
                onOpenList = onOpenList,
                onOpenPinnedList = onOpenPinnedList,
                onPin = onPin,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

internal fun orderHomeSections(sections: List<HomeSection>, pinned: PinnedHomeList?): List<HomeSection> {
    val native = pinned?.let { nativePinnedSection(it, HomeUiState.Content(sections)) } ?: return sections
    return listOf(native) + sections.filterNot { it.id == native.id }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    pinned: PinnedHomeList?,
    pinnedState: VideoListUiState?,
    refreshing: Boolean,
    refreshingRandom: Boolean,
    randomRefreshError: UiMessage?,
    onRefresh: () -> Unit,
    onRefreshRandom: () -> Unit,
    onClearRandomRefreshError: () -> Unit,
    onRetryPinned: () -> Unit,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    onOpenList: (path: String, title: String) -> Unit,
    onOpenPinnedList: (PinnedHomeList) -> Unit,
    onPin: (PinnedHomeList) -> Unit,
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
                val genericPinned = pinned?.takeIf { nativePinnedSection(it, HomeUiState.Content(sections)) == null }
                if (genericPinned != null) {
                    when (val current = pinnedState) {
                        is VideoListUiState.Content -> homeSection(
                            section = HomeSection(
                                id = "pinned-${genericPinned.query.cacheKey}",
                                title = genericPinned.title,
                                videos = current.videos,
                            ),
                            columns = columns,
                            carousel = true,
                            onMore = { onOpenPinnedList(genericPinned) },
                            onPin = null,
                            onOpen = onOpen,
                        )

                        VideoListUiState.Loading, null -> {
                            homeSectionHeader(
                                genericPinned,
                                onMore = { onOpenPinnedList(genericPinned) },
                            )
                            item(key = "pinned-loading", span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    LoadingIndicator()
                                }
                            }
                        }

                        is VideoListUiState.Error -> {
                            homeSectionHeader(
                                genericPinned,
                                onMore = { onOpenPinnedList(genericPinned) },
                            )
                            item(key = "pinned-error", span = { GridItemSpan(maxLineSpan) }) {
                                ErrorHint(current.message.text(), onRetryPinned, Modifier.height(220.dp))
                            }
                        }

                        VideoListUiState.Empty -> {
                            homeSectionHeader(
                                genericPinned,
                                onMore = { onOpenPinnedList(genericPinned) },
                            )
                            item(key = "pinned-empty", span = { GridItemSpan(maxLineSpan) }) {
                                EmptyHint(
                                    icon = Icons.AutoMirrored.Outlined.List,
                                    title = stringResource(R.string.list_empty_title),
                                    modifier = Modifier.height(200.dp),
                                )
                            }
                        }
                    }
                }

                val effectivePinned = effectivePinnedQuery(pinned, HomeUiState.Content(sections))
                orderHomeSections(sections, pinned).forEachIndexed { index, section ->
                    val listPath = homeSectionListPath(section)
                    homeSection(
                        section = section,
                        columns = columns,
                        carousel = genericPinned == null && index == 0,
                        onMore = section.morePath?.let { path -> { onOpenList(path, section.title) } },
                        onPin = listPath?.let { path ->
                            val query = VideoListQuery(path)
                            if (effectivePinned?.sameSiteQuery(query) == true) {
                                null
                            } else {
                                { onPin(PinnedHomeList(section.title, query)) }
                            }
                        },
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

private fun LazyGridScope.homeSectionHeader(
    pinned: PinnedHomeList,
    onMore: () -> Unit,
) {
    item(key = "header-pinned-${pinned.query.cacheKey}", span = { GridItemSpan(maxLineSpan) }) {
        HomeSectionHeader(title = pinned.title, onMore = onMore, onPin = null)
    }
}

private fun LazyGridScope.homeSection(
    section: HomeSection,
    columns: Int,
    carousel: Boolean,
    onMore: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    refreshingRandom: Boolean = false,
    onRefreshRandom: (() -> Unit)? = null,
) {
    item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
        HomeSectionHeader(
            title = section.title,
            onMore = onMore,
            onPin = onPin,
            refreshingRandom = refreshingRandom,
            onRefreshRandom = onRefreshRandom,
        )
    }

    if (carousel) {
        item(key = "carousel-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
            RecommendedCarousel(videos = section.videos, heroTag = section.id, onOpen = onOpen)
        }
    } else {
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
}

@Composable
private fun HomeSectionHeader(
    title: String,
    onMore: (() -> Unit)?,
    onPin: (() -> Unit)?,
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
        if (onPin != null) {
            TooltipIconButton(
                icon = Icons.Outlined.PushPin,
                contentDescription = stringResource(R.string.list_pin_home),
                onClick = onPin,
            )
        }
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

@Composable
private fun RecommendedCarousel(
    videos: List<VideoSummary>,
    heroTag: String,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
) {
    if (videos.isEmpty()) return
    val state = rememberCarouselState(itemCount = { videos.size })

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 264.dp,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(state),
    ) { index ->
        val video = videos[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoHero(heroKeyFor(heroTag, video.id), MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onOpen(video.id, video.thumbnailUrl, heroTag) },
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x00000000), Color(0xD9000000)),
                        ),
                    ),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                video.duration?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
