package app.missu

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.missu.data.api.LocalAppDependencies
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.comic.ComicRepository
import app.missu.data.comic.ComicSource
import app.missu.data.literature.LiteratureFilter
import app.missu.data.literature.LiteraturePackManager
import app.missu.data.literature.LiteratureRepository
import app.missu.data.store.AppLanguage
import app.missu.data.store.ColorSource
import app.missu.data.store.ComicFavoriteStore
import app.missu.data.store.ComicHistoryEntry
import app.missu.data.store.ComicHistoryStore
import app.missu.data.store.ComicPreferencesStore
import app.missu.data.store.HistoryStore
import app.missu.data.store.ListHistoryEntry
import app.missu.data.store.ListHistoryStore
import app.missu.data.store.ListQueryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.LiteratureHistoryStore
import app.missu.data.store.LiteratureStore
import app.missu.data.store.SearchHistoryStore
import app.missu.data.store.SettingsStore
import app.missu.data.store.ThemeMode
import app.missu.data.store.UsageStatsStore
import app.missu.ui.account.AccountScreen
import app.missu.ui.account.AuthUiState
import app.missu.ui.account.AuthViewModel
import app.missu.ui.account.LoginScreen
import app.missu.ui.common.text
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageSurface
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.comics.ComicDetailScreen
import app.missu.ui.comics.ComicDetailUiState
import app.missu.ui.comics.ComicLibraryScreen
import app.missu.ui.comics.ComicDetailViewModel
import app.missu.ui.comics.ComicReaderScreen
import app.missu.ui.comics.ComicReaderViewModel
import app.missu.ui.detail.DetailScreen
import app.missu.ui.detail.DetailViewModel
import app.missu.ui.history.VideoLibraryScreen
import app.missu.ui.history.HistoryViewModel
import app.missu.ui.home.ArticleScreen
import app.missu.ui.home.GuideScreen
import app.missu.ui.home.guideById
import app.missu.data.model.VideoListQuery
import app.missu.ui.list.AutoLoadVideoList
import app.missu.ui.list.VideoListScreen
import app.missu.ui.list.VideoListUiState
import app.missu.ui.list.VideoListViewModel
import app.missu.ui.playlists.PlaylistDetailScreen
import app.missu.ui.playlists.PlaylistDetailViewModel
import app.missu.ui.playlists.PlaylistEditScreen
import app.missu.ui.playlists.PlaylistEditViewModel
import app.missu.ui.playlists.PlaylistsViewModel
import app.missu.ui.playlists.PlaylistsUiState
import app.missu.ui.saved.SavedUiState
import app.missu.ui.saved.SavedViewModel
import app.missu.ui.search.SearchBody
import app.missu.ui.search.SearchHost
import app.missu.ui.search.SearchViewModel
import app.missu.ui.settings.LiteraturePackViewModel
import app.missu.ui.settings.SettingsScreen
import app.missu.ui.usage.UsageStatsScreen
import app.missu.ui.literature.LiteratureDetailUiState
import app.missu.ui.literature.LiteratureDetailViewModel
import app.missu.ui.literature.LiteratureScreen
import app.missu.ui.literature.LiteratureViewModel
import app.missu.ui.literature.LiteratureLibraryScreen

internal data class AppSettingsState(
    val appLanguage: AppLanguage,
    val themeMode: ThemeMode,
    val colorSource: ColorSource,
    val followThemeIcon: Boolean,
    val pureBlack: Boolean,
    val singleColumnVideos: Boolean,
    val comicColumns: Int,
)

internal data class AppSettingsActions(
    val onAppLanguageChange: (AppLanguage) -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onColorSourceChange: (ColorSource) -> Unit,
    val onFollowThemeIconChange: (Boolean) -> Unit,
    val onPureBlackChange: (Boolean) -> Unit,
    val onSingleColumnVideosChange: (Boolean) -> Unit,
    val onComicColumnsChange: (Int) -> Unit,
)

internal fun NavGraphBuilder.secondaryDestinations(
    navController: NavHostController,
    backStackEntry: State<NavBackStackEntry?>,
    tabRoute: State<String>,
    api: MissavApi,
    comicRepository: ComicRepository,
    literatureRepository: LiteratureRepository,
    literaturePackManager: LiteraturePackManager,
    settings: SettingsStore,
    settingsState: AppSettingsState,
    settingsActions: AppSettingsActions,
    historyStore: HistoryStore,
    listHistoryStore: ListHistoryStore,
    searchHistory: SearchHistoryStore,
    comicHistoryStore: ComicHistoryStore,
    comicFavoriteStore: ComicFavoriteStore,
    literatureHistoryStore: LiteratureHistoryStore,
    listSortStore: ListSortStore,
    listQueryStore: ListQueryStore,
    literatureStore: LiteratureStore,
    usageStats: UsageStatsStore,
    auth: AuthViewModel,
    onGoBack: () -> Unit,
    onOpenDetail: (String, String?, String) -> Unit,
    onOpenList: (String, String) -> Unit,
    onOpenListQuery: (VideoListQuery, String) -> Unit,
) {
    composable("video-account") {
        val state by auth.state.collectAsStateWithLifecycle()
        PageSurface {
            AccountScreen(
                state = state,
                onLogin = { navController.navigate("login") },
                onLogout = auth::logout,
                onBack = onGoBack,
            )
        }
    }

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

    composable("literature/{id}") { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        val viewModel: LiteratureDetailViewModel = viewModel(
            key = "literature-$id",
            factory = missuViewModelFactory { LiteratureDetailViewModel(literatureRepository, id) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val favorites by literatureStore.favorites.collectAsStateWithLifecycle()
        LaunchedEffect(id) { viewModel.loadIfNeeded() }
        PageSurface {
            when (val current = state) {
                LiteratureDetailUiState.Loading -> LoadingHint()
                is LiteratureDetailUiState.Error -> ErrorHint(current.message.text(), viewModel::load)
                is LiteratureDetailUiState.Content -> {
                    LaunchedEffect(current.work.id) { literatureHistoryStore.record(current.work) }
                    LiteratureScreen(
                        work = current.work,
                        favorite = current.work.id in favorites,
                        onToggleFavorite = { literatureStore.toggleFavorite(current.work.id) },
                        onBack = onGoBack,
                    )
                }
            }
        }
    }

    composable("literature-library") {
        val favoritesViewModel: LiteratureViewModel = viewModel(
            factory = missuViewModelFactory {
                LiteratureViewModel(literatureRepository, literatureStore, LiteratureFilter.FAVORITES)
            },
        )
        val packViewModel: LiteraturePackViewModel = viewModel(
            factory = missuViewModelFactory { LiteraturePackViewModel(literaturePackManager) },
        )
        val entries by literatureHistoryStore.entries.collectAsStateWithLifecycle()
        val favoritesState by favoritesViewModel.state.collectAsStateWithLifecycle()
        val favorites by literatureStore.favorites.collectAsStateWithLifecycle()
        val packState by packViewModel.state.collectAsStateWithLifecycle()
        PageSurface {
            LiteratureLibraryScreen(
                entries = entries,
                favoritesState = favoritesState,
                favorites = favorites,
                literaturePackState = packState,
                onToggleFavorite = literatureStore::toggleFavorite,
                onLoadMoreFavorites = favoritesViewModel::loadMore,
                onRetryFavorites = favoritesViewModel::refresh,
                onOpen = { navController.navigate("literature/$it") },
                onClearHistory = literatureHistoryStore::clear,
                onLiteraturePackDownload = packViewModel::download,
                onLiteraturePackCancel = packViewModel::cancelDownload,
                onLiteraturePackRemove = packViewModel::useCore,
                onBack = onGoBack,
            )
        }
    }

    composable("guide/{id}") { entry ->
        guideById(entry.arguments?.getString("id").orEmpty())?.let { guide ->
            PageSurface {
                GuideScreen(guide = guide, onBack = onGoBack)
            }
        }
    }

    composable("article/{id}") { entry ->
        val id = entry.arguments?.getString("id").orEmpty()
        LocalAppDependencies.current.articleRepository.findById(id)?.let { article ->
            PageSurface {
                ArticleScreen(article = article, onBack = onGoBack)
            }
        }
    }

    composable("comic-library") {
        val entries by comicHistoryStore.entries.collectAsStateWithLifecycle()
        val favorites by comicFavoriteStore.entries.collectAsStateWithLifecycle()
        PageSurface {
            ComicLibraryScreen(
                entries = entries,
                favorites = favorites,
                comicColumns = settingsState.comicColumns,
                onOpen = { entry ->
                    navController.navigate(
                        "comic/reader?s=${Uri.encode(entry.source.id)}&m=${Uri.encode(entry.mangaId)}" +
                            "&c=${Uri.encode(entry.chapterId)}&t=${Uri.encode(entry.chapterTitle)}&p=${entry.page}",
                    )
                },
                onOpenFavorite = { entry ->
                    navController.navigate(
                        "comic/detail?s=${Uri.encode(entry.source.id)}&id=${Uri.encode(entry.mangaId)}",
                    )
                },
                onClear = comicHistoryStore::clear,
                onComicColumnsChange = settingsActions.onComicColumnsChange,
                onBack = onGoBack,
            )
        }
    }

    composable(
        route = "comic/detail?s={s}&id={id}",
        arguments = listOf(
            navArgument("s") { defaultValue = ComicSource.COPY.id },
            navArgument("id") { defaultValue = "" },
        ),
    ) { entry ->
        val source = ComicSource.fromId(entry.arguments?.getString("s").orEmpty())
        val id = Uri.decode(entry.arguments?.getString("id").orEmpty())
        val comicContext = LocalContext.current
        val comicPreferences = remember { ComicPreferencesStore(comicContext.applicationContext) }
        val viewModel: ComicDetailViewModel = viewModel(
            key = "comic-detail-${source.id}-$id",
            factory = missuViewModelFactory { ComicDetailViewModel(comicRepository, source, id, comicPreferences) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val chapterDescending by viewModel.chapterDescending.collectAsStateWithLifecycle()
        val comicFavorites by comicFavoriteStore.entries.collectAsStateWithLifecycle()
        LaunchedEffect(source, id) { viewModel.loadIfNeeded() }
        PageSurface {
            ComicDetailScreen(
                sourceName = source.displayName,
                state = state,
                chapterDescending = chapterDescending,
                favorite = comicFavorites.any { it.source == source && it.mangaId == id },
                onToggleFavorite = {
                    (state as? ComicDetailUiState.Content)?.detail?.let { comicFavoriteStore.toggle(source, it) }
                },
                onToggleChapterOrder = viewModel::toggleChapterOrder,
                onRetry = viewModel::load,
                onBack = onGoBack,
                onOpenChapter = { chapter ->
                    (state as? ComicDetailUiState.Content)?.detail?.let { detail ->
                        comicHistoryStore.record(
                            ComicHistoryEntry(
                                source = source,
                                mangaId = id,
                                title = detail.title,
                                cover = detail.cover,
                                chapterId = chapter.id,
                                chapterTitle = chapter.title,
                                page = 0,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    navController.navigate(
                        "comic/reader?s=${Uri.encode(source.id)}&m=${Uri.encode(id)}" +
                            "&c=${Uri.encode(chapter.id)}&t=${Uri.encode(chapter.title)}&p=0",
                    )
                },
            )
        }
    }

    composable(
        route = "comic/reader?s={s}&m={m}&c={c}&t={t}&p={p}",
        arguments = listOf(
            navArgument("s") { defaultValue = ComicSource.COPY.id },
            navArgument("m") { defaultValue = "" },
            navArgument("c") { defaultValue = "" },
            navArgument("t") { defaultValue = "" },
            navArgument("p") { type = NavType.IntType; defaultValue = 0 },
        ),
    ) { entry ->
        val source = ComicSource.fromId(entry.arguments?.getString("s").orEmpty())
        val mangaId = Uri.decode(entry.arguments?.getString("m").orEmpty())
        val chapterId = Uri.decode(entry.arguments?.getString("c").orEmpty())
        val title = Uri.decode(entry.arguments?.getString("t").orEmpty())
        val initialPage = entry.arguments?.getInt("p") ?: 0
        val viewModel: ComicReaderViewModel = viewModel(
            key = "comic-reader-${source.id}-$chapterId",
            factory = missuViewModelFactory { ComicReaderViewModel(comicRepository, source, mangaId, chapterId) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        LaunchedEffect(source, chapterId) { viewModel.loadIfNeeded() }
        PageSurface {
            ComicReaderScreen(
                title = title,
                state = state,
                onRetry = viewModel::load,
                onBack = onGoBack,
                initialPage = initialPage,
                onPageChange = { page ->
                    comicHistoryStore.updatePosition(source, mangaId, chapterId, page)
                },
            )
        }
    }

    composable("usage") {
        val days by usageStats.days.collectAsStateWithLifecycle()
        PageSurface {
            UsageStatsScreen(
                days = days,
                onClear = usageStats::clear,
                onBack = onGoBack,
            )
        }
    }

    composable("settings") {
        val currentSettings = settingsState
        PageSurface {
            SettingsScreen(
                appLanguage = currentSettings.appLanguage,
                themeMode = currentSettings.themeMode,
                colorSource = currentSettings.colorSource,
                followThemeIcon = currentSettings.followThemeIcon,
                pureBlack = currentSettings.pureBlack,
                onAppLanguageChange = settingsActions.onAppLanguageChange,
                onThemeModeChange = settingsActions.onThemeModeChange,
                onColorSourceChange = settingsActions.onColorSourceChange,
                onFollowThemeIconChange = settingsActions.onFollowThemeIconChange,
                onPureBlackChange = settingsActions.onPureBlackChange,
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
        LaunchedEffect(query, title) {
            listHistoryStore.record(ListHistoryEntry(title, query, System.currentTimeMillis()))
        }
        val viewModel: VideoListViewModel = viewModel(
            key = "list-${query.cacheKey}",
            factory = missuViewModelFactory { VideoListViewModel(api, query, listSortStore) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val detectedSort by viewModel.detectedSort.collectAsStateWithLifecycle()
        AutoLoadVideoList(viewModel, viewModel.state, viewModel::load)

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
                    onSortChange = {
                        listQueryStore.put(query.copy(sort = it))
                        sort = it
                    },
                    onFilterChange = {
                        listQueryStore.put(query.copy(filter = it))
                        filter = it
                    },
                    onRetry = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onBack = onGoBack,
                )
            }
        }
    }

    composable("video-library") {
        val historyViewModel: HistoryViewModel = viewModel(
            factory = missuViewModelFactory { HistoryViewModel(historyStore, listHistoryStore) },
        )
        val savedViewModel: SavedViewModel = viewModel(
            factory = missuViewModelFactory { SavedViewModel(api) },
        )
        val playlistsViewModel: PlaylistsViewModel = viewModel(
            factory = missuViewModelFactory { PlaylistsViewModel(api) },
        )
        val entries by historyViewModel.entries.collectAsStateWithLifecycle()
        val listEntries by historyViewModel.listEntries.collectAsStateWithLifecycle()
        val savedState by savedViewModel.state.collectAsStateWithLifecycle()
        val playlistsState by playlistsViewModel.state.collectAsStateWithLifecycle()
        val createMessage by playlistsViewModel.createMessage.collectAsStateWithLifecycle()
        val creating by playlistsViewModel.creating.collectAsStateWithLifecycle()
        val authState by auth.state.collectAsStateWithLifecycle()
        var autoplay by remember { mutableStateOf(settings.autoplay()) }
        LaunchedEffect(Unit) { historyViewModel.load() }

        ProvideHeroVisibility(this, namespace = "video-library") {
            PageSurface {
                VideoLibraryScreen(
                    entries = entries,
                    listEntries = listEntries,
                    savedState = savedState,
                    playlistsState = playlistsState,
                    createMessage = createMessage,
                    creating = creating,
                    singleColumnVideos = settingsState.singleColumnVideos,
                    autoplay = autoplay,
                    signedIn = authState is AuthUiState.LoggedIn,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onOpenList = { onOpenListQuery(it.query, it.title) },
                    onToggleListPin = historyViewModel::toggleListPin,
                    onClearVideos = historyViewModel::clearVideos,
                    onClearLists = historyViewModel::clearLists,
                    onSavedRetry = savedViewModel::load,
                    onSavedLoadMore = savedViewModel::loadMore,
                    onPlaylistsRetry = playlistsViewModel::load,
                    onOpenPlaylist = { playlist ->
                        navController.navigate("playlist/${playlist.id}?n=${Uri.encode(playlist.name)}")
                    },
                    onCreatePlaylist = playlistsViewModel::create,
                    onTabChange = { tab ->
                        when (tab) {
                            0 -> if (savedState is SavedUiState.Loading) savedViewModel.load()
                            3 -> if (playlistsState is PlaylistsUiState.Loading) playlistsViewModel.load()
                        }
                    },
                    onOpenAccount = { navController.navigate("video-account") },
                    onSingleColumnVideosChange = settingsActions.onSingleColumnVideosChange,
                    onAutoplayChange = { enabled ->
                        settings.setAutoplay(enabled)
                        autoplay = enabled
                    },
                    onBack = onGoBack,
                )
            }
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

        fun backToList() { navController.popBackStack("video-library", inclusive = false) }

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

    composable("search/{keyword}") { entry ->
        val keyword = Uri.decode(entry.arguments?.getString("keyword").orEmpty())
        val viewModel: SearchViewModel = viewModel(
            key = "search-$keyword",
            factory = missuViewModelFactory { SearchViewModel(api, searchHistory, listQueryStore) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val sort by viewModel.sort.collectAsStateWithLifecycle()
        val filter by viewModel.filter.collectAsStateWithLifecycle()
        val detectedSort by viewModel.detectedSort.collectAsStateWithLifecycle()
        val recent by viewModel.recent.collectAsStateWithLifecycle()
        LaunchedEffect(keyword) { viewModel.search(keyword) }

        ProvideHeroVisibility(this, namespace = "search") {
            PageSurface(contentInset = false) {
                SearchHost(
                    state = state,
                    initialKeyword = keyword,
                    recent = recent,
                    onSearch = viewModel::search,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onClearHistory = viewModel::clearHistory,
                    onClear = viewModel::clear,
                    onExit = onGoBack,
                ) {
                    SearchBody(
                        state = state,
                        keyword = keyword,
                        sort = sort,
                        filter = filter,
                        detectedSort = detectedSort,
                        onSortChange = viewModel::setSort,
                        onFilterChange = viewModel::setFilter,
                        onRetry = viewModel::search,
                        onLoadMore = viewModel::loadMore,
                        onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    )
                }
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
        LaunchedEffect(id) { viewModel.loadIfNeeded() }

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
                    onSearch = { keyword ->
                        navController.navigate("search/${Uri.encode(keyword)}")
                    },
                )
            }
        }
    }
}
