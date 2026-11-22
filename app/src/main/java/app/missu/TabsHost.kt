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
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import app.missu.data.literature.LiteratureRepository
import app.missu.data.store.ListQueryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.LiteratureStore
import app.missu.data.store.SearchHistoryStore
import app.missu.ui.account.AccountScreen
import app.missu.ui.account.AuthViewModel
import app.missu.ui.components.PageSurface
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.components.underScrim
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
import app.missu.ui.study.LiteratureViewModel
import app.missu.ui.study.StudyScreen

internal const val TABS_ROUTE = "tabs"

internal data class TopLevel(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

internal val topLevels = listOf(
    TopLevel("home", R.string.nav_home, Icons.Outlined.Home),
    TopLevel("study", R.string.nav_study, Icons.Outlined.AutoStories),
    TopLevel("videos", R.string.nav_videos, Icons.Outlined.VideoLibrary),
    TopLevel("account", R.string.nav_account, Icons.Outlined.Person),
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
    literatureRepository: LiteratureRepository,
    searchHistory: SearchHistoryStore,
    listSortStore: ListSortStore,
    listQueryStore: ListQueryStore,
    literatureStore: LiteratureStore,
    auth: AuthViewModel,
    onOpenDetail: (String, String?, String) -> Unit,
    onOpenList: (String, String) -> Unit,
) {
    val videoNavigationViewModel: VideoNavigationViewModel = viewModel(factory = missuViewModelFactory { VideoNavigationViewModel(api) })
    val searchViewModel: SearchViewModel = viewModel(
        factory = missuViewModelFactory { SearchViewModel(api, searchHistory) },
    )
    val authState by auth.state.collectAsStateWithLifecycle()

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
                    ProvideHeroVisibility(navScope, namespace = "home") {
                        PageSurface(card = false) {
                            HomeScreen(
                                onOpenGuide = { navController.navigate("guide/$it") },
                                onOpenVideos = { onTabChange("videos") },
                            )
                        }
                    }
                }

                "study" -> {
                    val literatureViewModel: LiteratureViewModel = viewModel(
                        factory = missuViewModelFactory {
                            LiteratureViewModel(literatureRepository, literatureStore)
                        },
                    )
                    val literatureState by literatureViewModel.state.collectAsStateWithLifecycle()
                    val query by literatureViewModel.query.collectAsStateWithLifecycle()
                    val filter by literatureViewModel.filter.collectAsStateWithLifecycle()
                    val favorites by literatureStore.favorites.collectAsStateWithLifecycle()
                    PageSurface(card = false) {
                        StudyScreen(
                            literatureState = literatureState,
                            query = query,
                            filter = filter,
                            favorites = favorites,
                            onQueryChange = literatureViewModel::setQuery,
                            onFilterChange = literatureViewModel::setFilter,
                            onToggleFavorite = literatureStore::toggleFavorite,
                            onLoadMore = literatureViewModel::loadMore,
                            onRetry = literatureViewModel::refresh,
                            onOpenLiterature = { navController.navigate("literature/$it") },
                            onOpenEnglish = { navController.navigate("english/$it") },
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
                                        states = states,
                                        searchAction = searchAction,
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
