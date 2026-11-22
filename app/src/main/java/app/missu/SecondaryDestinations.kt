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
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.comic.ComicRepository
import app.missu.data.comic.ComicSource
import app.missu.data.literature.LiteratureRepository
import app.missu.data.store.AppLanguage
import app.missu.data.store.ColorSource
import app.missu.data.store.ComicPreferencesStore
import app.missu.data.store.HistoryStore
import app.missu.data.store.ListHistoryEntry
import app.missu.data.store.ListHistoryStore
import app.missu.data.store.ListQueryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.LiteratureStore
import app.missu.data.store.SearchHistoryStore
import app.missu.data.store.SettingsStore
import app.missu.data.store.ThemeMode
import app.missu.ui.account.AuthViewModel
import app.missu.ui.account.LoginScreen
import app.missu.ui.common.text
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageSurface
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.comics.ComicDetailScreen
import app.missu.ui.comics.ComicDetailViewModel
import app.missu.ui.comics.ComicReaderScreen
import app.missu.ui.comics.ComicReaderViewModel
import app.missu.ui.detail.DetailScreen
import app.missu.ui.detail.DetailViewModel
import app.missu.ui.history.HistoryScreen
import app.missu.ui.history.HistoryViewModel
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
import app.missu.ui.playlists.PlaylistsScreen
import app.missu.ui.playlists.PlaylistsViewModel
import app.missu.ui.saved.SavedScreen
import app.missu.ui.saved.SavedViewModel
import app.missu.ui.search.SearchBody
import app.missu.ui.search.SearchHost
import app.missu.ui.search.SearchViewModel
import app.missu.ui.settings.SettingsScreen
import app.missu.ui.study.EnglishArticleScreen
import app.missu.ui.study.LiteratureDetailUiState
import app.missu.ui.study.LiteratureDetailViewModel
import app.missu.ui.study.LiteratureScreen
import app.missu.ui.study.englishArticleById

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
    comicRepository: ComicRepository,
    literatureRepository: LiteratureRepository,
    settings: SettingsStore,
    settingsState: AppSettingsState,
    settingsActions: AppSettingsActions,
    historyStore: HistoryStore,
    listHistoryStore: ListHistoryStore,
    searchHistory: SearchHistoryStore,
    listSortStore: ListSortStore,
    listQueryStore: ListQueryStore,
    literatureStore: LiteratureStore,
    auth: AuthViewModel,
    onGoBack: () -> Unit,
    onOpenDetail: (String, String?, String) -> Unit,
    onOpenList: (String, String) -> Unit,
    onOpenListQuery: (VideoListQuery, String) -> Unit,
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
                is LiteratureDetailUiState.Content -> LiteratureScreen(
                    work = current.work,
                    favorite = current.work.id in favorites,
                    onToggleFavorite = { literatureStore.toggleFavorite(current.work.id) },
                    onBack = onGoBack,
                )
            }
        }
    }

    composable("english/{id}") { entry ->
        englishArticleById(entry.arguments?.getString("id").orEmpty())?.let { article ->
            PageSurface {
                EnglishArticleScreen(article = article, onBack = onGoBack)
            }
        }
    }

    composable("guide/{id}") { entry ->
        guideById(entry.arguments?.getString("id").orEmpty())?.let { guide ->
            PageSurface {
                GuideScreen(guide = guide, onBack = onGoBack)
            }
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
        LaunchedEffect(source, id) { viewModel.loadIfNeeded() }
        PageSurface {
            ComicDetailScreen(
                sourceName = source.displayName,
                state = state,
                chapterDescending = chapterDescending,
                onToggleChapterOrder = viewModel::toggleChapterOrder,
                onRetry = viewModel::load,
                onBack = onGoBack,
                onOpenChapter = { chapter ->
                    navController.navigate(
                        "comic/reader?s=${Uri.encode(source.id)}&m=${Uri.encode(id)}" +
                            "&c=${Uri.encode(chapter.id)}&t=${Uri.encode(chapter.title)}",
                    )
                },
            )
        }
    }

    composable(
        route = "comic/reader?s={s}&m={m}&c={c}&t={t}",
        arguments = listOf(
            navArgument("s") { defaultValue = ComicSource.COPY.id },
            navArgument("m") { defaultValue = "" },
            navArgument("c") { defaultValue = "" },
            navArgument("t") { defaultValue = "" },
        ),
    ) { entry ->
        val source = ComicSource.fromId(entry.arguments?.getString("s").orEmpty())
        val mangaId = Uri.decode(entry.arguments?.getString("m").orEmpty())
        val chapterId = Uri.decode(entry.arguments?.getString("c").orEmpty())
        val title = Uri.decode(entry.arguments?.getString("t").orEmpty())
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

    composable("history") {
        val viewModel: HistoryViewModel = viewModel(
            factory = missuViewModelFactory { HistoryViewModel(historyStore, listHistoryStore) },
        )
        val entries by viewModel.entries.collectAsStateWithLifecycle()
        val listEntries by viewModel.listEntries.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { viewModel.load() }

        ProvideHeroVisibility(this, namespace = "history") {
            PageSurface {
                HistoryScreen(
                    entries = entries,
                    listEntries = listEntries,
                    onOpen = { id, thumbnail -> onOpenDetail(id, thumbnail, "") },
                    onOpenList = { onOpenListQuery(it.query, it.title) },
                    onToggleListPin = viewModel::toggleListPin,
                    onClearVideos = viewModel::clearVideos,
                    onClearLists = viewModel::clearLists,
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

    composable("search/{keyword}") { entry ->
        val keyword = Uri.decode(entry.arguments?.getString("keyword").orEmpty())
        val viewModel: SearchViewModel = viewModel(
            key = "search-$keyword",
            factory = missuViewModelFactory { SearchViewModel(api, searchHistory) },
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
