package app.missu

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.store.AppLanguage
import app.missu.data.store.ColorSource
import app.missu.data.store.HistoryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.SettingsStore
import app.missu.data.store.ThemeMode
import app.missu.ui.account.AuthViewModel
import app.missu.ui.account.LoginScreen
import app.missu.ui.components.PageSurface
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.detail.DetailScreen
import app.missu.ui.detail.DetailViewModel
import app.missu.ui.history.HistoryScreen
import app.missu.ui.history.HistoryViewModel
import app.missu.ui.home.HomeViewModel
import app.missu.ui.home.effectivePinnedQuery
import app.missu.data.model.PinnedHomeList
import app.missu.data.model.VideoListQuery
import app.missu.ui.list.VideoListScreen
import app.missu.ui.list.VideoListUiState
import app.missu.ui.list.VideoListViewModel
import app.missu.ui.playlists.PlaylistDetailScreen
import app.missu.ui.playlists.PlaylistDetailViewModel
import app.missu.ui.playlists.PlaylistEditScreen
import app.missu.ui.playlists.PlaylistEditViewModel
import app.missu.ui.playlists.PlaylistsScreen
import app.missu.ui.playlists.PlaylistsViewModel
import app.missu.ui.saved.SavedScreen
import app.missu.ui.saved.SavedViewModel
import app.missu.ui.settings.SettingsScreen

internal data class AppSettingsState(
    val appLanguage: AppLanguage,
    val themeMode: ThemeMode,
    val colorSource: ColorSource,
    val followThemeIcon: Boolean,
    val pureBlack: Boolean,
    val singleColumnVideos: Boolean,
)

internal data class AppSettingsActions(
    val onAppLanguageChange: (AppLanguage) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onColorSourceChange: (ColorSource) -> Unit,
    val onFollowThemeIconChange: (Boolean) -> Unit,
    val onPureBlackChange: (Boolean) -> Unit,
    val onSingleColumnVideosChange: (Boolean) -> Unit,
)

internal fun NavGraphBuilder.secondaryDestinations(
    navController: NavHostController,
    backStackEntry: State<NavBackStackEntry?>,
    tabRoute: State<String>,
    api: MissavApi,
    homeViewModel: HomeViewModel,
    settings: SettingsStore,
    settingsState: AppSettingsState,
    settingsActions: AppSettingsActions,
    historyStore: HistoryStore,
    listSortStore: ListSortStore,
    auth: AuthViewModel,
    onGoBack: () -> Unit,
    onOpenDetail: (String, String?, String) -> Unit,
    onOpenList: (String, String) -> Unit,
) {
    composable("login") {
        val loginState by auth.loginState.collectAsStateWithLifecycle()
        PageSurface {
            LoginScreen(
                state = loginState,
                onSubmit = { email, password -> auth.login(email, password, onGoBack) },
                onBack = {
                    auth.clearLoginError()
                    navController.popBackStack()
                },
            )
        }
    }

    composable("settings") {
        var autoplay by remember { mutableStateOf(settings.autoplay()) }
        val currentSettings = settingsState
        PageSurface {
            SettingsScreen(
                appLanguage = currentSettings.appLanguage,
                themeMode = currentSettings.themeMode,
                colorSource = currentSettings.colorSource,
                followThemeIcon = currentSettings.followThemeIcon,
                pureBlack = currentSettings.pureBlack,
                singleColumnVideos = currentSettings.singleColumnVideos,
                autoplay = autoplay,
                onAppLanguageChange = settingsActions.onAppLanguageChange,
                onThemeModeChange = settingsActions.onThemeModeChange,
                onColorSourceChange = settingsActions.onColorSourceChange,
                onFollowThemeIconChange = settingsActions.onFollowThemeIconChange,
                onPureBlackChange = settingsActions.onPureBlackChange,
                onSingleColumnVideosChange = settingsActions.onSingleColumnVideosChange,
                onAutoplayChange = { enabled ->
                    settings.setAutoplay(enabled)
                    autoplay = enabled
                },
                onBack = onGoBack,
            )
        }
    }

    composable(
        route = "list?p={p}&t={t}&s={s}&f={f}",
        arguments = listOf(
            navArgument("p") { defaultValue = "/latest" },
            navArgument("t") { defaultValue = "" },
            navArgument("s") { defaultValue = "" },
            navArgument("f") { defaultValue = "" },
        ),
    ) { entry ->
        val path = Uri.decode(entry.arguments?.getString("p").orEmpty())
        val title = Uri.decode(entry.arguments?.getString("t").orEmpty())
            .ifEmpty { stringResource(R.string.list_default_title) }
        var sort by rememberSaveable {
            mutableStateOf(VideoListOptions.Sort.fromKey(entry.arguments?.getString("s")))
        }
        var filter by rememberSaveable {
            mutableStateOf(VideoListOptions.Filter.fromKey(entry.arguments?.getString("f")))
        }
        val query = remember(path, sort, filter) { VideoListQuery(path, sort = sort, filter = filter) }
        val pinnedHome by homeViewModel.pinned.collectAsStateWithLifecycle()
        val homeState by homeViewModel.state.collectAsStateWithLifecycle()
        val effectivePinned = effectivePinnedQuery(pinnedHome, homeState)
        val viewModel: VideoListViewModel = viewModel(
            key = "list-${query.cacheKey}",
            factory = missuViewModelFactory { VideoListViewModel(api, query, listSortStore) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val detectedSort by viewModel.detectedSort.collectAsStateWithLifecycle()
        LaunchedEffect(query) {
            if (state is VideoListUiState.Loading) viewModel.load()
        }

        ProvideHeroVisibility(this, namespace = "list") {
            PageSurface {
                VideoListScreen(
                    title = title,
                    state = state,
                    sort = sort,
                    filter = filter,
                    detectedSort = detectedSort,
                    sorts = VideoListOptions.Sort.entries,
                    filters = VideoListOptions.Filter.entries,
                    onSortChange = { sort = it },
                    onFilterChange = { filter = it },
                    onRetry = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onBack = onGoBack,
                    showPin = effectivePinned?.sameSiteQuery(query) != true,
                    onPin = {
                        homeViewModel.pin(
                            pinned = PinnedHomeList(title = title, query = query),
                            currentVideos = (state as? VideoListUiState.Content)?.videos.orEmpty(),
                        )
                    },
                )
            }
        }
    }

    composable("history") {
        val viewModel: HistoryViewModel = viewModel(
            factory = missuViewModelFactory { HistoryViewModel(historyStore) },
        )
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.load() }

        ProvideHeroVisibility(this, namespace = "history") {
            PageSurface {
                HistoryScreen(
                    entries = entries,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onClear = viewModel::clear,
                    onBack = onGoBack,
                )
            }
        }
    }

    composable("playlists") {
        val viewModel: PlaylistsViewModel = viewModel(
            factory = missuViewModelFactory { PlaylistsViewModel(api) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val createMessage by viewModel.createMessage.collectAsStateWithLifecycle()
        val creating by viewModel.creating.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.load() }

        PageSurface {
            PlaylistsScreen(
                state = state,
                onRetry = viewModel::load,
                onOpen = { playlist ->
                    navController.navigate("playlist/${playlist.id}?n=${Uri.encode(playlist.name)}")
                },
                onBack = onGoBack,
                createMessage = createMessage,
                creating = creating,
                onCreate = viewModel::create,
            )
        }
    }

    composable(
        route = "playlist/{id}?n={n}",
        arguments = listOf(
            navArgument("id") { type = NavType.StringType },
            navArgument("n") { defaultValue = "" },
        ),
    ) { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        val fallbackTitle = Uri.decode(entry.arguments?.getString("n").orEmpty())
        val viewModel: PlaylistDetailViewModel = viewModel(
            key = "playlist-$id",
            factory = missuViewModelFactory { PlaylistDetailViewModel(api, id) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        LaunchedEffect(id) { viewModel.load() }

        ProvideHeroVisibility(this, namespace = "playlist") {
            PageSurface {
                PlaylistDetailScreen(
                    fallbackTitle = fallbackTitle,
                    state = state,
                    message = message,
                    onRetry = viewModel::load,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onBack = onGoBack,
                    onEdit = { navController.navigate("playlist/$id/edit") },
                    onRemove = viewModel::removeAll,
                )
            }
        }
    }

    composable("playlist/{id}/edit") { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        val viewModel: PlaylistEditViewModel = viewModel(
            key = "playlist-edit-$id",
            factory = missuViewModelFactory { PlaylistEditViewModel(api, id) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        val busy by viewModel.busy.collectAsStateWithLifecycle()
        LaunchedEffect(id) { viewModel.load() }

        fun backToList() { navController.popBackStack("playlists", inclusive = false) }

        PageSurface {
            PlaylistEditScreen(
                state = state,
                message = message,
                busy = busy,
                onSave = { name, description, isPublic ->
                    viewModel.save(name, description, isPublic) { backToList() }
                },
                onDelete = { viewModel.delete { backToList() } },
                onRetry = viewModel::load,
                onBack = onGoBack,
            )
        }
    }

    composable("saved") {
        val viewModel: SavedViewModel = viewModel(factory = missuViewModelFactory { SavedViewModel(api) })
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.load() }

        ProvideHeroVisibility(this, namespace = "saved") {
            PageSurface {
                SavedScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onBack = onGoBack,
                )
            }
        }
    }

    composable(
        route = "detail/{id}?img={img}&h={h}",
        arguments = listOf(
            navArgument("id") { type = NavType.StringType },
            navArgument("img") { defaultValue = "" },
            navArgument("h") { defaultValue = "" },
        ),
    ) { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        val poster = Uri.decode(entry.arguments?.getString("img").orEmpty())
        val heroTag = Uri.decode(entry.arguments?.getString("h").orEmpty())
        val viewModel: DetailViewModel = viewModel(
            key = "detail-$id",
            factory = missuViewModelFactory { DetailViewModel(api, id, historyStore) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val message by viewModel.message.collectAsStateWithLifecycle()
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        LaunchedEffect(id) { viewModel.load() }

        val previousRoute = navController.previousBackStackEntry?.destination?.route
            ?.substringBefore('?')
            ?.substringBefore('/')
        val heroSource = if (previousRoute == TABS_ROUTE || previousRoute.isNullOrEmpty()) {
            tabRoute.value
        } else {
            previousRoute
        }

        ProvideHeroVisibility(this, namespace = heroSource) {
            PageSurface(contentInset = false) {
                DetailScreen(
                    state = state,
                    onRetry = viewModel::load,
                    videoId = id,
                    heroTag = heroTag,
                    posterUrl = poster.ifEmpty { null },
                    isCurrent = backStackEntry.value?.id == entry.id,
                    message = message,
                    onToggleSaved = viewModel::toggleSaved,
                    startPositionMs = viewModel.startPositionMs,
                    onPositionReport = viewModel::reportProgress,
                    playlists = playlists,
                    onOpenPlaylists = viewModel::openPlaylists,
                    onClosePlaylists = viewModel::closePlaylists,
                    onTogglePlaylist = viewModel::togglePlaylist,
                    onOpenPath = onOpenList,
                )
            }
        }
    }
}
