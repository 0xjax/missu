package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.missu.ui.discover.DiscoverScreen
import app.missu.ui.discover.DiscoverViewModel
import app.missu.data.api.MissavApi
import app.missu.data.api.createMissavApi
import app.missu.data.store.CredentialStore
import app.missu.data.store.ProfileStore
import app.missu.data.store.HistoryStore
import app.missu.data.store.SearchHistoryStore
import app.missu.data.store.SettingsStore
import app.missu.data.store.ThemeMode
import app.missu.ui.history.HistoryScreen
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import app.missu.ui.history.HistoryViewModel
import app.missu.ui.list.VideoListScreen
import app.missu.ui.list.VideoListViewModel
import app.missu.ui.settings.SettingsScreen
import app.missu.ui.account.AccountScreen
import app.missu.ui.account.AuthViewModel
import app.missu.ui.account.LoginScreen
import app.missu.ui.detail.DetailScreen
import app.missu.ui.detail.DetailViewModel
import app.missu.ui.home.HomeScreen
import app.missu.ui.home.HomeViewModel
import app.missu.ui.playlists.PlaylistDetailScreen
import app.missu.ui.playlists.PlaylistEditScreen
import app.missu.ui.playlists.PlaylistEditViewModel
import app.missu.ui.playlists.PlaylistDetailViewModel
import app.missu.ui.playlists.PlaylistsScreen
import app.missu.ui.playlists.PlaylistsViewModel
import app.missu.ui.saved.SavedScreen
import app.missu.ui.saved.SavedViewModel
import app.missu.ui.search.SearchBody
import app.missu.ui.search.SearchHost
import app.missu.ui.search.SearchUiState
import app.missu.ui.search.SearchViewModel
import app.missu.ui.theme.MissuTheme
import app.missu.ui.theme.themeBackgroundColor
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // NOTE: 窗口底色必须在第一帧之前就跟上主题。XML 里写死的是 #000000（当年只有深色），
        // 用户切到浅色后，任何透明帧都会露出纯黑 = 转场时"闪一下"。之后由 MissuTheme 持续同步。
        applyWindowBackground()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MissuRoot() }
    }

    /** 按持久化的明暗模式（含「跟随系统」）先给窗口铺上与页面一致的底色。 */
    private fun applyWindowBackground() {
        val settings = SettingsStore(applicationContext)
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkTheme = when (settings.themeMode()) {
            ThemeMode.SYSTEM -> nightMask == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        window.setBackgroundDrawable(ColorDrawable(themeBackgroundColor(darkTheme).toArgb()))
    }
}

/**
 * 主题状态的唯一持有者：明暗模式与壁纸取色都要能实时改变整棵树，所以放在最外层。
 *
 * NOTE: 设置本身存在 SharedPreferences 里（[SettingsStore]），这里只是把它读成 Compose 状态；
 * 改设置 → 立刻重组主题，不需要重启 Activity。
 */
@Composable
private fun MissuRoot() {
    // NOTE: 缩略图淡入。不设的话 Coil 的默认行为是「解码完成瞬间替换」——卡片会从一层纯色
    // 忽然跳成图片；开启 crossfade 后是渐显，卡片底色（surfaceContainerHigh）始终垫在下面。
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).crossfade(true).build()
    }

    val context = LocalContext.current
    val settings = remember { SettingsStore(context.applicationContext).also { it.applyStoredLanguage() } }
    var themeMode by remember { mutableStateOf(settings.themeMode()) }
    var dynamicColor by remember { mutableStateOf(settings.dynamicColor()) }
    var pureBlack by remember { mutableStateOf(settings.pureBlack()) }
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MissuTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, pureBlack = pureBlack) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MissuApp(
                settings = settings,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                pureBlack = pureBlack,
                onThemeModeChange = { mode ->
                    settings.setThemeMode(mode)
                    themeMode = mode
                },
                onDynamicColorChange = { enabled ->
                    settings.setDynamicColor(enabled)
                    dynamicColor = enabled
                },
                onPureBlackChange = { enabled ->
                    settings.setPureBlack(enabled)
                    pureBlack = enabled
                },
            )
        }
    }
}

/** 取数入口：用 applicationContext（ChallengeActivity 自己带 NEW_TASK，不依赖 Activity 引用）。 */
@Composable
private fun rememberApi(): MissavApi {
    val context = LocalContext.current
    return remember { createMissavApi(context.applicationContext) }
}

@Composable
private fun MissuApp(
    settings: SettingsStore,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val api = rememberApi()
    val context = LocalContext.current
    // NOTE: 账户状态整个 App 共用一份：'我的' 与 '登录' 两个页面必须看到同一份状态。
    val auth: AuthViewModel = viewModel(factory = factory { AuthViewModel(api, ProfileStore(context.applicationContext), CredentialStore(context.applicationContext)) })

    // NOTE: 语言必须在第一次请求之前装进 SourceConfig，否则首屏会按默认语言拉一轮再换。
    val historyStore = remember { HistoryStore(context.applicationContext) }
    val searchHistory = remember { SearchHistoryStore(context.applicationContext) }
    var language by remember { mutableStateOf(settings.language()) }
    // NOTE: 页签级的 ViewModel 必须提到 Activity 作用域。放在 composable("home") 里的话，
    // 切走页签时目的地被销毁、ViewModel 一并清掉，切回来就是「新建 VM → Loading → 重新请求」：
    // 用户看到的是先一帧旧画面、再一个转圈，正是"闪一下/像缓存"的来源。提到这里后切页签是纯粹的视图切换。
    val homeViewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(api) })
    val discoverViewModel: DiscoverViewModel = viewModel(factory = factory { DiscoverViewModel(api) })
    val searchViewModel: SearchViewModel = viewModel(factory = factory { SearchViewModel(api, searchHistory) })

    // NOTE: 语言变了才重拉首页。这个 effect 放在这里（一直处于组合中）而不是 home 目的地里：
    // 放在目的地里的话，每次切回页签都会重新启动 effect → load() → 打回 Loading → 转圈，
    // 也就是「先闪一帧旧画面再加载」的另一半原因。
    LaunchedEffect(language) { homeViewModel.load() }
    val authState by auth.state.collectAsState()
    val loginState by auth.loginState.collectAsState()
    // 底部导航只在四个一级页面显示；详情/列表/收藏等二级页面全屏展示。
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevels.any { it.route == currentRoute }

    // NOTE: 系统栏、刘海、输入法的内边距只在这里处理一次，各页面只负责内容布局。
    // 底部由 NavigationBar 自己处理导航栏 inset，所以内容区只吃顶部与左右。
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        bottomBar = {
            if (showBottomBar) {
                // NOTE: M3E 的紧凑底栏（ShortNavigationBar）替代基线 NavigationBar——3 个目的地正合适，
                // 高度更矮、指示器是药丸形。大屏场景的 WideNavigationRail 暂不需要（只交付手机）。
                ShortNavigationBar {
                    topLevels.forEach { item ->
                        ShortNavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        // 切一级页签时保留各自的滚动位置与已加载内容。
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val tabOrder = topLevels.map { it.route }
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            // NOTE: 整幅横滑（1:1 位移），像 ViewPager2 那样「两页接成一张长纸」前后推。
            // 为什么是它：alpha 过渡在整屏内容上必然出问题——交叉淡入 = 两页文字叠在一起，
            // 顺序淡出再淡入 = 中间整屏是空底色（"闪一下"）；而横滑**两页全程不透明**，
            // 每一帧都是两块实心画面在拼，不存在叠加，也不暴露窗口底色。
            // 方向：页签按底栏顺序比较（首页→发现→我的 是前进），其它路由（详情/列表）前进即右进左出。
            enterTransition = {
                val forward = isForward(initialState, targetState, tabOrder)
                slideInHorizontally(animationSpec = tween(durationMillis = 300)) { if (forward) it else -it }
            },
            exitTransition = {
                val forward = isForward(initialState, targetState, tabOrder)
                slideOutHorizontally(animationSpec = tween(durationMillis = 300)) { if (forward) -it else it }
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(durationMillis = 300)) { -it }
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(durationMillis = 300)) { it }
            },
        ) {
            composable("home") {
                val viewModel = homeViewModel
                val state by viewModel.state.collectAsState()
                val refreshing by viewModel.refreshing.collectAsState()
                // NOTE: 搜索不再是独立页面——它和首页同属一个目的地，栏在顶部就地展开。
                // 页面切换会让输入框换一份样式、键盘重启一次，那是"割裂感"的来源。
                val searchState by searchViewModel.state.collectAsState()
                val recent by searchViewModel.recent.collectAsState()
                // NOTE: 用 loadIfNeeded 而不是 load：切回页签时不要把已有内容打回 Loading。
                LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

                SearchHost(
                    state = searchState,
                    recent = recent,
                    onSearch = searchViewModel::search,
                    onLoadMore = searchViewModel::loadMore,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onClearHistory = searchViewModel::clearHistory,
                    onClear = searchViewModel::clear,
                ) {
                    if (searchState is SearchUiState.Idle) {
                        HomeScreen(
                            state = state,
                            refreshing = refreshing,
                            onRefresh = viewModel::refresh,
                            onRetry = viewModel::load,
                            onOpen = { id -> navController.navigate("detail/$id") },
                            onOpenList = { path, title ->
                                navController.navigate("list?p=${Uri.encode(path)}&t=${Uri.encode(title)}")
                            },
                        )
                    } else {
                        SearchBody(
                            state = searchState,
                            keyword = (searchState as? SearchUiState.Content)?.keyword.orEmpty(),
                            onRetry = searchViewModel::search,
                            onLoadMore = searchViewModel::loadMore,
                            onOpen = { id -> navController.navigate("detail/$id") },
                        )
                    }
                }
            }

            composable("discover") {
                val viewModel = discoverViewModel
                val states by viewModel.states.collectAsState()
                val sort by viewModel.sort.collectAsState()
                val filters by viewModel.filters.collectAsState()
                val refreshingKinds by viewModel.refreshing.collectAsState()

                DiscoverScreen(
                    states = states,
                    sort = sort,
                    filters = filters,
                    refreshingKinds = refreshingKinds,
                    onLoad = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onRefresh = viewModel::refresh,
                    onOpen = { path, title ->
                        navController.navigate("list?p=${Uri.encode(path)}&t=${Uri.encode(title)}")
                    },
                    onSortChange = viewModel::setSort,
                    onFilterChange = viewModel::setFilter,
                    onResetFilters = viewModel::resetFilters,
                )
            }
            composable("account") {
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

            composable("login") {
                LoginScreen(
                    state = loginState,
                    onSubmit = { email, password ->
                        auth.login(email, password) { navController.popBackStack() }
                    },
                    onBack = {
                        auth.clearLoginError()
                        navController.popBackStack()
                    },

                )
            }
            composable("settings") {
                SettingsScreen(
                    language = language,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    pureBlack = pureBlack,
                    onLanguageChange = { value ->
                        settings.setLanguage(value)
                        language = value
                    },
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onPureBlackChange = onPureBlackChange,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "list?p={p}&t={t}",
                arguments = listOf(
                    navArgument("p") { defaultValue = "/latest" },
                    navArgument("t") { defaultValue = "列表" },
                ),
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString("p").orEmpty())
                val title = Uri.decode(entry.arguments?.getString("t").orEmpty())
                val viewModel: VideoListViewModel = viewModel(
                    key = "list-$path",
                    factory = factory { VideoListViewModel(api, path) },
                )
                val state by viewModel.state.collectAsState()
                LaunchedEffect(path) { viewModel.load() }

                VideoListScreen(
                    title = title,
                    state = state,
                    onRetry = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("history") {
                val viewModel: HistoryViewModel = viewModel(
                    factory = factory { HistoryViewModel(historyStore) },
                )
                val entries by viewModel.entries.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                HistoryScreen(
                    entries = entries,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onClear = viewModel::clear,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("playlists") {
                val viewModel: PlaylistsViewModel = viewModel(factory = factory { PlaylistsViewModel(api) })
                val state by viewModel.state.collectAsState()
                val createMessage by viewModel.createMessage.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                PlaylistsScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onOpen = { playlist ->
                        navController.navigate("playlist/${playlist.id}?n=${Uri.encode(playlist.name)}")
                    },
                    onBack = { navController.popBackStack() },
                    createMessage = createMessage,
                    onCreate = viewModel::create,
                )
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
                    factory = factory { PlaylistDetailViewModel(api, id) },
                )
                val state by viewModel.state.collectAsState()
                val message by viewModel.message.collectAsState()
                LaunchedEffect(id) { viewModel.load() }

                PlaylistDetailScreen(
                    fallbackTitle = fallbackTitle,
                    state = state,
                    message = message,
                    onRetry = viewModel::load,
                    onOpen = { videoId -> navController.navigate("detail/$videoId") },
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate("playlist/$id/edit") },
                    onRemove = viewModel::removeAll,
                )
            }

            composable("playlist/{id}/edit") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val viewModel: PlaylistEditViewModel = viewModel(
                    key = "playlist-edit-$id",
                    factory = factory { PlaylistEditViewModel(api, id) },
                )
                val state by viewModel.state.collectAsState()
                val message by viewModel.message.collectAsState()
                val busy by viewModel.busy.collectAsState()
                LaunchedEffect(id) { viewModel.load() }

                // 保存或删除成功后回到列表：列表会重新加载，详情页的旧名字也不会残留。
                fun backToList() { navController.popBackStack("playlists", inclusive = false) }

                PlaylistEditScreen(
                    state = state,
                    message = message,
                    busy = busy,
                    onSave = { name, description, isPublic ->
                        viewModel.save(name, description, isPublic) { backToList() }
                    },
                    onDelete = { viewModel.delete { backToList() } },
                    onRetry = viewModel::load,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("saved") {
                val viewModel: SavedViewModel = viewModel(factory = factory { SavedViewModel(api) })
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                SavedScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                // NOTE: key 用 id —— 换一集详情时复用同一个 ViewModel 会显示上一条数据。
                val viewModel: DetailViewModel = viewModel(
                    key = "detail-$id",
                    factory = factory { DetailViewModel(api, id, historyStore) },
                )
                val state by viewModel.state.collectAsState()
                val message by viewModel.message.collectAsState()
                val playlists by viewModel.playlists.collectAsState()
                LaunchedEffect(id) { viewModel.load() }

                DetailScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onBack = { navController.popBackStack() },
                    message = message,
                    onToggleSaved = viewModel::toggleSaved,
                    startPositionMs = viewModel.startPositionMs,
                    onPositionReport = viewModel::reportProgress,
                    playlists = playlists,
                    onOpenPlaylists = viewModel::openPlaylists,
                    onClosePlaylists = viewModel::closePlaylists,
                    onTogglePlaylist = viewModel::togglePlaylist,
                    onOpenPath = { path, title ->
                        navController.navigate("list?p=${Uri.encode(path)}&t=${Uri.encode(title)}")
                    },
                )
            }
        }
    }
}

/** 底部导航的一级页面。 */
private data class TopLevel(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

// NOTE: 搜索不再单独占一个页签——「发现」顶部就是搜索入口，结果页仍在（search 路由），
// 底部只留三个一级页面：首页 / 发现 / 我的。
private val topLevels = listOf(
    TopLevel("home", "首页", Icons.Filled.Home),
    TopLevel("discover", "发现", Icons.AutoMirrored.Filled.List),
    TopLevel("account", "我的", Icons.Filled.Person),
)

/**
 * 这次导航是不是「前进」。
 *
 * NOTE: 两个目的地都是底栏一级页时按底栏顺序比较（首页 → 发现 → 我的 为前进）；
 * 其它跳转（进详情 / 列表 / 设置）一律算前进，返回由 popEnter/popExit 单独给反向。
 */
private fun isForward(
    initial: NavBackStackEntry,
    target: NavBackStackEntry,
    tabOrder: List<String>,
): Boolean {
    val from = tabOrder.indexOf(initial.destination.route)
    val to = tabOrder.indexOf(target.destination.route)
    return if (from >= 0 && to >= 0) to > from else true
}

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
