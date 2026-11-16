package app.missu


import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.store.ListSortStore
import app.missu.data.store.SearchHistoryStore
import app.missu.ui.account.AccountScreen
import app.missu.ui.account.AuthViewModel
import app.missu.ui.components.PageSurface
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.components.underScrim
import app.missu.ui.discover.DiscoverScreen
import app.missu.ui.discover.DiscoverViewModel
import app.missu.ui.discover.HOT_PATH
import app.missu.ui.home.HomeScreen
import app.missu.ui.home.HomeViewModel
import app.missu.ui.home.effectivePinnedQuery
import app.missu.data.model.PinnedHomeList
import app.missu.data.model.VideoListQuery
import app.missu.ui.list.AutoLoadVideoList
import app.missu.ui.list.VideoListUiState
import app.missu.ui.list.VideoListViewModel
import app.missu.ui.search.SearchBody
import app.missu.ui.search.SearchHost
import app.missu.ui.search.SearchUiState
import app.missu.ui.search.SearchViewModel

internal const val TABS_ROUTE = "tabs"

internal data class TopLevel(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

internal val topLevels = listOf(
    TopLevel("home", R.string.nav_home, Icons.Filled.Home),
    TopLevel("discover", R.string.nav_discover, Icons.AutoMirrored.Filled.List),
    TopLevel("account", R.string.nav_account, Icons.Filled.Person),
)

@Composable
internal fun TabsHost(
    entry: NavBackStackEntry,
    navScope: AnimatedContentScope,
    backStackEntry: NavBackStackEntry?,
    navController: NavHostController,
    tabRoute: String,
    onTabChange: (String) -> Unit,
    contentLanguage: String,
    api: MissavApi,
    homeViewModel: HomeViewModel,
    searchHistory: SearchHistoryStore,
    listSortStore: ListSortStore,
    auth: AuthViewModel,
    onOpenDetail: (String, String?, String) -> Unit,
    onOpenList: (String, String) -> Unit,
    onOpenPinnedList: (PinnedHomeList) -> Unit,
) {
    val discoverViewModel: DiscoverViewModel = viewModel(factory = missuViewModelFactory { DiscoverViewModel(api) })
    val searchViewModel: SearchViewModel = viewModel(
        factory = missuViewModelFactory { SearchViewModel(api, searchHistory) },
    )
    val authState by auth.state.collectAsStateWithLifecycle()

    LaunchedEffect(contentLanguage) { homeViewModel.loadIfNeeded(contentLanguage) }

    val startTab = topLevels.first().route
    BackHandler(enabled = tabRoute != startTab) { onTabChange(startTab) }

    Scaffold(
        modifier = Modifier.underScrim(
            scope = navScope,
            covered = backStackEntry?.id != entry.id,
            fadingIn = backStackEntry?.destination?.route?.startsWith("detail/") == true,
        ),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            ShortNavigationBar {
                topLevels.forEach { item ->
                    ShortNavigationBarItem(
                        selected = tabRoute == item.route,
                        onClick = { if (tabRoute != item.route) onTabChange(item.route) },
                        icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                        label = { Text(stringResource(item.labelRes)) },
                    )
                }
            }
        },
    ) { tabPadding ->
        AnimatedContent(
            targetState = tabRoute,
            modifier = Modifier.padding(tabPadding),
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
            when (current) {
                "home" -> {
                    val state by homeViewModel.state.collectAsStateWithLifecycle()
                    val refreshing by homeViewModel.refreshing.collectAsStateWithLifecycle()
                    val pinned by homeViewModel.pinned.collectAsStateWithLifecycle()
                    val pinnedState by homeViewModel.pinnedState.collectAsStateWithLifecycle()
                    val refreshingRandom by homeViewModel.refreshingRandom.collectAsStateWithLifecycle()
                    val randomRefreshError by homeViewModel.randomRefreshError.collectAsStateWithLifecycle()
                    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
                    val recent by searchViewModel.recent.collectAsStateWithLifecycle()
                    ProvideHeroVisibility(navScope, namespace = "home") {
                        SearchHost(
                            state = searchState,
                            recent = recent,
                            onSearch = searchViewModel::search,
                            onLoadMore = searchViewModel::loadMore,
                            onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                            onClearHistory = searchViewModel::clearHistory,
                            onClear = searchViewModel::clear,
                        ) {
                            if (searchState is SearchUiState.Idle) {
                                PageSurface(card = false, contentInset = false) {
                                    HomeScreen(
                                        state = state,
                                        pinned = pinned,
                                        pinnedState = pinnedState,
                                        refreshing = refreshing,
                                        refreshingRandom = refreshingRandom,
                                        randomRefreshError = randomRefreshError,
                                        onRefresh = homeViewModel::refresh,
                                        onRefreshRandom = homeViewModel::refreshRandom,
                                        onClearRandomRefreshError = homeViewModel::clearRandomRefreshError,
                                        onRetryPinned = homeViewModel::retryPinned,
                                        onRetry = homeViewModel::load,
                                        onOpen = onOpenDetail,
                                        onOpenList = onOpenList,
                                        onOpenPinnedList = onOpenPinnedList,
                                        onPin = { homeViewModel.pin(it, emptyList()) },
                                    )
                                }
                            } else {
                                SearchBody(
                                    state = searchState,
                                    keyword = (searchState as? SearchUiState.Content)?.keyword.orEmpty(),
                                    onRetry = searchViewModel::search,
                                    onLoadMore = searchViewModel::loadMore,
                                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                                )
                            }
                        }
                    }
                }

                "discover" -> {
                    ProvideHeroVisibility(navScope, namespace = "discover") {
                        val states by discoverViewModel.states.collectAsStateWithLifecycle()
                        val sort by discoverViewModel.sort.collectAsStateWithLifecycle()
                        val filters by discoverViewModel.filters.collectAsStateWithLifecycle()
                        val refreshingKinds by discoverViewModel.refreshing.collectAsStateWithLifecycle()
                        val channels by discoverViewModel.channels.collectAsStateWithLifecycle()
                        var hotSort by rememberSaveable { mutableStateOf<VideoListOptions.Sort?>(null) }
                        var hotFilter by rememberSaveable { mutableStateOf<VideoListOptions.Filter?>(null) }
                        val hotQuery = remember(hotSort, hotFilter) {
                            VideoListQuery(HOT_PATH, sort = hotSort, filter = hotFilter)
                        }
                        val hotViewModel: VideoListViewModel = viewModel(
                            key = "hot-${hotQuery.cacheKey}",
                            factory = missuViewModelFactory { VideoListViewModel(api, hotQuery, listSortStore) },
                        )
                        val hotState by hotViewModel.state.collectAsStateWithLifecycle()
                        val hotDetectedSort by hotViewModel.detectedSort.collectAsStateWithLifecycle()
                        val pinnedHome by homeViewModel.pinned.collectAsStateWithLifecycle()
                        val homeState by homeViewModel.state.collectAsStateWithLifecycle()
                        val effectivePinned = effectivePinnedQuery(pinnedHome, homeState)
                        val hotTitle = stringResource(R.string.discover_section_hot)
                        AutoLoadVideoList(hotViewModel, hotViewModel.state, hotViewModel::load)

                        PageSurface(card = false) {
                            DiscoverScreen(
                                states = states,
                                sort = sort,
                                filters = filters,
                                refreshingKinds = refreshingKinds,
                                hotSort = hotSort,
                                hotFilter = hotFilter,
                                hotDetectedSort = hotDetectedSort,
                                hotState = hotState,
                                showHotPin = effectivePinned?.sameSiteQuery(hotQuery) != true,
                                channels = channels,
                                onLoad = discoverViewModel::load,
                                onLoadMore = discoverViewModel::loadMore,
                                onRefresh = discoverViewModel::refresh,
                                onChannelsLoad = { discoverViewModel.loadChannels() },
                                onChannelsRetry = { discoverViewModel.loadChannels(force = true) },
                                onHotSortChange = { hotSort = it },
                                onHotFilterChange = { hotFilter = it },
                                onHotLoadMore = hotViewModel::loadMore,
                                onHotRetry = hotViewModel::load,
                                onHotPin = {
                                    homeViewModel.pin(
                                        pinned = PinnedHomeList(
                                            title = hotTitle,
                                            query = hotQuery,
                                        ),
                                        currentVideos = (hotState as? VideoListUiState.Content)?.videos.orEmpty(),
                                        currentNextPage = (hotState as? VideoListUiState.Content)?.nextPage,
                                    )
                                },
                                onOpen = onOpenList,
                                onOpenVideo = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                                onSortChange = discoverViewModel::setSort,
                                onFilterChange = discoverViewModel::setFilter,
                                onResetFilters = discoverViewModel::resetFilters,
                            )
                        }
                    }
                }

                "account" -> {
                    PageSurface(card = false) {
                        AccountScreen(
                            state = authState,
                            onLogin = { navController.navigate("login") },
                            onSaved = { navController.navigate("saved") },
                            onHistory = { navController.navigate("history") },
                            onPlaylists = { navController.navigate("playlists") },
                            onSettings = { navController.navigate("settings") },
                            onLogout = auth::logout,
                        )
                    }
                }
            }
        }
    }
}
