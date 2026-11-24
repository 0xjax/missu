package app.missu


import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.comic.ComicRepository
import app.missu.data.literature.LiteratureRepository
import app.missu.data.store.ComicSourceStore
import app.missu.data.store.ListQueryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.LiteratureStore
import app.missu.data.store.SearchHistoryStore
import app.missu.data.store.UsageStatsStore
import app.missu.ui.components.ContentLibraryButton
import app.missu.ui.components.LocalFloatingNavigationBarInset
import app.missu.ui.components.LocalTabAnimatedVisibilityScope
import app.missu.ui.components.PageSurface
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.components.rememberLiquidGlassStyle
import app.missu.ui.components.underScrim
import app.missu.ui.comics.ComicNavigationScreen
import app.missu.ui.comics.ComicNavigationViewModel
import app.missu.ui.videos.VideoNavigationScreen
import app.missu.ui.videos.VideoNavigationViewModel
import app.missu.ui.videos.HOT_PATH
import app.missu.ui.home.HomeScreen
import app.missu.data.model.VideoListQuery
import app.missu.ui.list.AutoLoadVideoList
import app.missu.ui.list.VideoListUiState
import app.missu.ui.list.VideoListViewModel
import app.missu.ui.search.SearchBody
import app.missu.ui.search.SearchHost
import app.missu.ui.search.SearchUiState
import app.missu.ui.search.SearchViewModel
import app.missu.ui.literature.LiteratureViewModel
import app.missu.ui.literature.LiteratureNavigationScreen
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val TABS_ROUTE = "tabs"

internal data class TopLevel(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

internal val topLevels = listOf(
    TopLevel("home", R.string.nav_home, Icons.Outlined.Home),
    TopLevel("literature", R.string.nav_literature, Icons.Outlined.AutoStories),
    TopLevel("videos", R.string.nav_videos, Icons.Outlined.VideoLibrary),
    TopLevel("comics", R.string.nav_comics, Icons.Outlined.MenuBook),
)

@Composable
internal fun TabsHost(
    entry: NavBackStackEntry,
    navScope: AnimatedContentScope,
    backStackEntry: NavBackStackEntry?,
    navController: NavHostController,
    tabRoute: String,
    onTabChange: (String) -> Unit,
    api: MissavApi,
    comicRepository: ComicRepository,
    literatureRepository: LiteratureRepository,
    searchHistory: SearchHistoryStore,
    comicSearchHistory: SearchHistoryStore,
    listSortStore: ListSortStore,
    listQueryStore: ListQueryStore,
    literatureStore: LiteratureStore,
    usageStats: UsageStatsStore,
    onOpenDetail: (String, String?, String) -> Unit,
    onOpenList: (String, String) -> Unit,
    onOpenComic: (app.missu.data.comic.ComicSource, String) -> Unit,
) {
    val videoNavigationViewModel: VideoNavigationViewModel = viewModel(factory = missuViewModelFactory { VideoNavigationViewModel(api) })
    val searchViewModel: SearchViewModel = viewModel(
        factory = missuViewModelFactory { SearchViewModel(api, searchHistory, listQueryStore) },
    )
    val context = LocalContext.current
    val comicSourceStore = remember { ComicSourceStore(context.applicationContext) }
    val comicNavigationViewModel: ComicNavigationViewModel = viewModel(
        factory = missuViewModelFactory {
            ComicNavigationViewModel(comicRepository, comicSourceStore, comicSearchHistory)
        },
    )
    var tabReselectVersion by remember { mutableIntStateOf(0) }

    val startTab = topLevels.first().route
    BackHandler(enabled = tabRoute != startTab) { onTabChange(startTab) }

    val glassState = rememberHazeState()
    val navigationShape = RoundedCornerShape(32.dp)
    val navigationStyle = rememberLiquidGlassStyle(navigationShape)
    val navigationInset = 76.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalFloatingNavigationBarInset provides navigationInset) {
        Box(
            Modifier.fillMaxSize().underScrim(
                scope = navScope,
                covered = backStackEntry?.id != entry.id,
                fadingIn = backStackEntry?.destination?.route?.startsWith("detail/") == true,
            ),
        ) {
            AnimatedContent(
                targetState = tabRoute,
                modifier = Modifier.fillMaxSize().hazeSource(glassState),
                transitionSpec = {
                    val forward = topLevels.indexOfFirst { it.route == targetState } >
                        topLevels.indexOfFirst { it.route == initialState }
                    slideInHorizontally(animationSpec = tween(durationMillis = 300)) {
                        if (forward) it else -it
                    } togetherWith slideOutHorizontally(animationSpec = tween(durationMillis = 300)) {
                        if (forward) -it else it
                    }
                },
                label = "tab",
            ) { current ->
                CompositionLocalProvider(LocalTabAnimatedVisibilityScope provides this) {
                when (current) {
                "home" -> {
                    ProvideHeroVisibility(navScope, namespace = "home") {
                        PageSurface(card = false) {
                            val usageDays by usageStats.days.collectAsStateWithLifecycle()
                            HomeScreen(
                                usageDays = usageDays,
                                scrollToTopSignal = tabReselectVersion,
                                onOpenGuide = { navController.navigate("guide/$it") },
                                onOpenUsage = { navController.navigate("usage") },
                                onOpenSettings = { navController.navigate("settings") },
                            )
                        }
                    }
                }

                "literature" -> {
                    val literatureViewModel: LiteratureViewModel = viewModel(
                        factory = missuViewModelFactory {
                            LiteratureViewModel(literatureRepository, literatureStore)
                        },
                    )
                    val literatureState by literatureViewModel.state.collectAsStateWithLifecycle()
                    val query by literatureViewModel.query.collectAsStateWithLifecycle()
                    val filter by literatureViewModel.filter.collectAsStateWithLifecycle()
                    val sort by literatureViewModel.sort.collectAsStateWithLifecycle()
                    val favorites by literatureStore.favorites.collectAsStateWithLifecycle()
                    PageSurface(card = false, contentInset = false) {
                        LiteratureNavigationScreen(
                            scrollToTopSignal = tabReselectVersion,
                            literatureState = literatureState,
                            query = query,
                            filter = filter,
                            sort = sort,
                            favorites = favorites,
                            onQueryChange = literatureViewModel::setQuery,
                            onFilterChange = literatureViewModel::setFilter,
                            onSortChange = literatureViewModel::setSort,
                            onToggleFavorite = literatureStore::toggleFavorite,
                            onLoadMore = literatureViewModel::loadMore,
                            onRetry = literatureViewModel::refresh,
                            onOpenLiterature = { navController.navigate("literature/$it") },
                            libraryAction = {
                                ContentLibraryButton(
                                    contentDescription = stringResource(R.string.literature_library_title),
                                    onClick = { navController.navigate("literature-library") },
                                )
                            },
                        )
                    }
                }

                "videos" -> {
                    ProvideHeroVisibility(navScope, namespace = "videos") {
                        val states by videoNavigationViewModel.states.collectAsStateWithLifecycle()
                        val sort by videoNavigationViewModel.sort.collectAsStateWithLifecycle()
                        val filters by videoNavigationViewModel.filters.collectAsStateWithLifecycle()
                        val refreshingKinds by videoNavigationViewModel.refreshing.collectAsStateWithLifecycle()
                        val channels by videoNavigationViewModel.channels.collectAsStateWithLifecycle()
                        var hotSort by rememberSaveable { mutableStateOf(listQueryStore.get(HOT_PATH).sort) }
                        var hotFilter by rememberSaveable { mutableStateOf(listQueryStore.get(HOT_PATH).filter) }
                        val hotQuery = remember(hotSort, hotFilter) {
                            VideoListQuery(HOT_PATH, sort = hotSort, filter = hotFilter)
                        }
                        val hotViewModel: VideoListViewModel = viewModel(
                            key = "hot-${hotQuery.cacheKey}",
                            factory = missuViewModelFactory { VideoListViewModel(api, hotQuery, listSortStore) },
                        )
                        val hotState by hotViewModel.state.collectAsStateWithLifecycle()
                        val hotDetectedSort by hotViewModel.detectedSort.collectAsStateWithLifecycle()
                        AutoLoadVideoList(hotViewModel, hotViewModel.state, hotViewModel::load)

                        val searchState by searchViewModel.state.collectAsStateWithLifecycle()
                        val searchSort by searchViewModel.sort.collectAsStateWithLifecycle()
                        val searchFilter by searchViewModel.filter.collectAsStateWithLifecycle()
                        val searchDetectedSort by searchViewModel.detectedSort.collectAsStateWithLifecycle()
                        val recent by searchViewModel.recent.collectAsStateWithLifecycle()
                        PageSurface(card = false, contentInset = false) {
                            SearchHost(
                                state = searchState,
                                recent = recent,
                                onSearch = searchViewModel::search,
                                onLoadMore = searchViewModel::loadMore,
                                onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                                onClearHistory = searchViewModel::clearHistory,
                                onClear = searchViewModel::clear,
                                inlineTrigger = true,
                            ) { searchAction ->
                                if (searchState is SearchUiState.Idle) {
                                    VideoNavigationScreen(
                                        scrollToTopSignal = tabReselectVersion,
                                        states = states,
                                        searchAction = searchAction,
                                        libraryAction = {
                                            ContentLibraryButton(
                                                contentDescription = stringResource(R.string.video_library_title),
                                                onClick = { navController.navigate("video-library") },
                                            )
                                        },
                                        sort = sort,
                                        filters = filters,
                                        refreshingKinds = refreshingKinds,
                                        hotSort = hotSort,
                                        hotFilter = hotFilter,
                                        hotDetectedSort = hotDetectedSort,
                                        hotState = hotState,
                                        channels = channels,
                                        onLoad = videoNavigationViewModel::load,
                                        onLoadMore = videoNavigationViewModel::loadMore,
                                        onRefresh = videoNavigationViewModel::refresh,
                                        onChannelsLoad = { videoNavigationViewModel.loadChannels() },
                                        onChannelsRetry = { videoNavigationViewModel.loadChannels(force = true) },
                                        onHotSortChange = {
                                            listQueryStore.put(hotQuery.copy(sort = it))
                                            hotSort = it
                                        },
                                        onHotFilterChange = {
                                            listQueryStore.put(hotQuery.copy(filter = it))
                                            hotFilter = it
                                        },
                                        onHotLoadMore = hotViewModel::loadMore,
                                        onHotRetry = hotViewModel::load,
                                        onOpen = onOpenList,
                                        onOpenVideo = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                                        onSortChange = videoNavigationViewModel::setSort,
                                        onFilterChange = videoNavigationViewModel::setFilter,
                                        onResetFilters = videoNavigationViewModel::resetFilters,
                                    )
                                } else {
                                    SearchBody(
                                        scrollToTopSignal = tabReselectVersion,
                                        state = searchState,
                                        keyword = (searchState as? SearchUiState.Content)?.keyword.orEmpty(),
                                        sort = searchSort,
                                        filter = searchFilter,
                                        detectedSort = searchDetectedSort,
                                        onSortChange = searchViewModel::setSort,
                                        onFilterChange = searchViewModel::setFilter,
                                        onRetry = searchViewModel::search,
                                        onLoadMore = searchViewModel::loadMore,
                                        onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                                        searchAction = searchAction,
                                    )
                                }
                            }
                        }
                    }
                }

                "comics" -> {
                    val source by comicNavigationViewModel.source.collectAsStateWithLifecycle()
                    val feed by comicNavigationViewModel.feed.collectAsStateWithLifecycle()
                    val category by comicNavigationViewModel.category.collectAsStateWithLifecycle()
                    val comicState by comicNavigationViewModel.listState.collectAsStateWithLifecycle()
                    val comicSearchState by comicNavigationViewModel.searchState.collectAsStateWithLifecycle()
                    val comicAggregateState by comicNavigationViewModel.aggregateSearchState.collectAsStateWithLifecycle()
                    val comicSearchScope by comicNavigationViewModel.searchScope.collectAsStateWithLifecycle()
                    val comicKeyword by comicNavigationViewModel.keyword.collectAsStateWithLifecycle()
                    val comicRecent by comicNavigationViewModel.recent.collectAsStateWithLifecycle()
                    PageSurface(card = false, contentInset = false) {
                        ComicNavigationScreen(
                            scrollToTopSignal = tabReselectVersion,
                            source = source,
                            feed = feed,
                            category = category,
                            state = comicState,
                            searchState = comicSearchState,
                            aggregateSearchState = comicAggregateState,
                            searchScope = comicSearchScope,
                            keyword = comicKeyword,
                            recent = comicRecent,
                            onSourceChange = comicNavigationViewModel::setSource,
                            onFeedChange = comicNavigationViewModel::setFeed,
                            onCategoryChange = comicNavigationViewModel::setCategory,
                            onLoad = comicNavigationViewModel::load,
                            onLoadMore = { comicNavigationViewModel.loadMore() },
                            onRetry = comicNavigationViewModel::refresh,
                            onSearch = comicNavigationViewModel::search,
                            onClearSearch = comicNavigationViewModel::clearSearch,
                            onClearSearchHistory = comicNavigationViewModel::clearSearchHistory,
                            onSearchScopeChange = comicNavigationViewModel::setSearchScope,
                            onOpenSourceSearch = comicNavigationViewModel::openSourceSearch,
                            onRetrySourceSearch = comicNavigationViewModel::retrySourceSearch,
                            onSearchLoadMore = { comicNavigationViewModel.loadMore(search = true) },
                            onOpen = onOpenComic,
                            libraryAction = {
                                ContentLibraryButton(
                                    contentDescription = stringResource(R.string.comic_history_title),
                                    onClick = { navController.navigate("comic-library") },
                                )
                            },
                        )
                    }
                }
            }
            }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .hazeGlass(
                        input = HazeInput.Sources(glassState),
                        style = navigationStyle,
                    )
                    .clip(navigationShape),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().height(64.dp).selectableGroup()) {
                    val selectedIndex = topLevels.indexOfFirst { it.route == tabRoute }.coerceAtLeast(0)
                    val itemWidth = maxWidth / topLevels.size
                    val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
                    val indicatorIndex = remember { Animatable(selectedIndex.toFloat()) }
                    val coroutineScope = rememberCoroutineScope()
                    var dragging by remember { mutableStateOf(false) }
                    var pressedRoute by remember { mutableStateOf<String?>(null) }
                    val pressed = pressedRoute == tabRoute
                    LaunchedEffect(selectedIndex) {
                        indicatorIndex.animateTo(
                            targetValue = selectedIndex.toFloat(),
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                        )
                    }
                    val travel = abs(selectedIndex.toFloat() - indicatorIndex.value)
                    val stretch = if (dragging) 0.12f else min(0.16f, travel * 0.16f)
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed && !dragging) 1.06f else 1f,
                        animationSpec = spring(dampingRatio = 0.68f, stiffness = 600f),
                        label = "navigation-press",
                    )
                    val indicatorColor by animateColorAsState(
                        targetValue = MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 0.24f else 0.16f),
                        animationSpec = tween(durationMillis = 120),
                        label = "navigation-press-color",
                    )
                    Box(
                        Modifier
                            .offset {
                                IntOffset((itemWidthPx * indicatorIndex.value).roundToInt(), 0)
                            }
                            .width(itemWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                            .graphicsLayer {
                                scaleX = pressScale * (1f + stretch)
                                scaleY = pressScale * (1f - stretch * 0.35f)
                            }
                            .background(indicatorColor, CircleShape),
                    )
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        topLevels.forEach { item ->
                            LiquidNavigationItem(
                                item = item,
                                selected = tabRoute == item.route,
                                onClick = {
                                    if (tabRoute == item.route) tabReselectVersion++ else onTabChange(item.route)
                                },
                                onPressChange = { isPressed ->
                                    if (isPressed) {
                                        pressedRoute = item.route
                                    } else if (pressedRoute == item.route) {
                                        pressedRoute = null
                                    }
                                },
                                onDragStart = {
                                    dragging = true
                                    coroutineScope.launch { indicatorIndex.stop() }
                                },
                                onDrag = { amount ->
                                    coroutineScope.launch {
                                        indicatorIndex.snapTo(
                                            (indicatorIndex.value + amount / itemWidthPx)
                                                .coerceIn(0f, (topLevels.size - 1).toFloat()),
                                        )
                                    }
                                },
                                onDragEnd = {
                                    dragging = false
                                    val target = indicatorIndex.value.roundToInt().coerceIn(topLevels.indices)
                                    if (target == selectedIndex) {
                                        coroutineScope.launch {
                                            indicatorIndex.animateTo(
                                                targetValue = target.toFloat(),
                                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                                            )
                                        }
                                    } else {
                                        onTabChange(topLevels[target].route)
                                    }
                                },
                                onDragCancel = {
                                    dragging = false
                                    coroutineScope.launch {
                                        indicatorIndex.animateTo(
                                            targetValue = selectedIndex.toFloat(),
                                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun LiquidNavigationItem(
    item: TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
    onPressChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 150),
        label = "navigation-content",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val interactionPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(selected, interactionPressed) {
        onPressChange(selected && interactionPressed)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .pointerInput(selected) {
                if (selected) {
                    detectHorizontalDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onHorizontalDrag = { _, amount -> onDrag(amount) },
                    )
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(item.labelRes),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
