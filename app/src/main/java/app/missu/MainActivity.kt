@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
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
import app.missu.ui.discover.HotPeriod
import app.missu.data.api.MissavApi
import app.missu.data.api.SourceConfig
import app.missu.data.api.createMissavApi
import app.missu.data.store.AppLanguage
import app.missu.data.store.contentLanguage
import app.missu.data.store.uiLocale
import app.missu.data.store.CredentialStore
import app.missu.data.store.ColorSource
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
import app.missu.ui.list.VideoListUiState
import app.missu.ui.list.VideoListViewModel
import app.missu.ui.settings.SettingsScreen
import app.missu.ui.components.LocalNavAnimatedVisibilityScope
import app.missu.ui.components.LocalSharedTransitionScope
import app.missu.ui.components.ProvideHeroVisibility
import app.missu.ui.components.heroEnter
import app.missu.ui.components.heroExit
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
import app.missu.ui.preview.LocalVideoPreview
import app.missu.ui.preview.VideoPreviewHost
import app.missu.ui.preview.VideoPreviewState
import app.missu.ui.saved.SavedScreen
import app.missu.ui.saved.SavedViewModel
import app.missu.ui.search.SearchBody
import app.missu.ui.search.SearchHost
import app.missu.ui.search.SearchUiState
import app.missu.ui.search.SearchViewModel
import app.missu.ui.theme.LauncherIcons
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
        // NOTE: 启动时只保证「至少还有一个桌面入口」，不做换色取舍——在前台禁用我们正用着的那个
        // 入口会结束当前 task（见 LauncherIcons 的 WARNING）。真正的换色发生在应用进后台之后。
        val iconSettings = SettingsStore(applicationContext)
        LauncherIcons.ensureOne(applicationContext, iconSettings.desiredIconSource())
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MissuRoot() }
    }

    /** 按持久化的明暗模式（含「跟随系统」）与色源，先给窗口铺上与页面一致的底色。 */
    private fun applyWindowBackground() {
        val settings = SettingsStore(applicationContext)
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkTheme = when (settings.themeMode()) {
            ThemeMode.SYSTEM -> nightMask == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val color = themeBackgroundColor(applicationContext, darkTheme, settings.colorSource())
        window.setBackgroundDrawable(ColorDrawable(color.toArgb()))
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
    val settings = remember { SettingsStore(context.applicationContext) }
    // NOTE: 长按预览的宿主挂在根部：卡片离它十几层，用 CompositionLocal 传下去比逐层加参数省。
    // 它自己是一层 Dialog，所以放在导航/底栏之外也能盖住它们。
    val previewState = remember { VideoPreviewState() }
    var appLanguage by remember { mutableStateOf(settings.appLanguage()) }
    var themeMode by remember { mutableStateOf(settings.themeMode()) }
    var colorSource by remember { mutableStateOf(settings.colorSource()) }
    var followThemeIcon by remember { mutableStateOf(settings.followThemeIcon()) }
    var pureBlack by remember { mutableStateOf(settings.pureBlack()) }
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // NOTE: 语言在组合期就解析好，并**立刻**写进 SourceConfig——首页的第一次请求就在下面
    // 的 LaunchedEffect 里发出，晚一步就会用旧语言拉一轮再换（legacy 踩过）。
    val systemConfiguration = context.resources.configuration
    val contentLanguage = remember(appLanguage) {
        appLanguage.contentLanguage(systemConfiguration).also { SourceConfig.contentLanguage = it }
    }
    // NOTE: 界面语言用 createConfigurationContext 覆写，不重建 Activity：改设置即重组生效。
    // 覆写只作用于 Compose 的 LocalContext/LocalConfiguration，系统栏与窗口不受影响。
    val localizedContext = remember(appLanguage) {
        val locale = appLanguage.uiLocale(systemConfiguration)
        val configuration = Configuration(systemConfiguration).apply { setLocale(locale) }
        context.createConfigurationContext(configuration)
    }

    // NOTE: 桌面图标换色的唯一时机是「应用完全不可见之后」。禁用当前启动入口会结束它所在的 task，
    // 在前台切的表现是被弹回桌面，或者正在跑的人机验证页被一起干掉。ProcessLifecycleOwner 的
    // ON_STOP 在最后一个 Activity 停掉之后才发，正好给出这段余量。
    val appContext = context.applicationContext
    DisposableEffect(appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                LauncherIcons.apply(appContext, settings.desiredIconSource())
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }

    MissuTheme(darkTheme = darkTheme, colorSource = colorSource, pureBlack = pureBlack) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // NOTE: 卡片 → 详情的 container transform 需要共享元素作用域。它必须比 NavHost
            // 更高——两个页面的封面在转场期间同时存在，那层覆盖物由 SharedTransitionLayout 画。
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    LocalVideoPreview provides previewState,
                ) {
                    MissuApp(
                        settings = settings,
                        appLanguage = appLanguage,
                        contentLanguage = contentLanguage,
                        themeMode = themeMode,
                        colorSource = colorSource,
                        followThemeIcon = followThemeIcon,
                        pureBlack = pureBlack,
                        onAppLanguageChange = { value ->
                            settings.setAppLanguage(value)
                            appLanguage = value
                        },
                        onThemeModeChange = { mode ->
                            settings.setThemeMode(mode)
                            themeMode = mode
                        },
                        onColorSourceChange = { source ->
                            settings.setColorSource(source)
                            colorSource = source
                        },
                        onFollowThemeIconChange = { enabled ->
                            settings.setFollowThemeIcon(enabled)
                            followThemeIcon = enabled
                        },                        onPureBlackChange = { enabled ->
                            settings.setPureBlack(enabled)
                            pureBlack = enabled
                        },
                    )
                    VideoPreviewHost(state = previewState)
                }
            }
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
    colorSource: ColorSource,
    followThemeIcon: Boolean,
    pureBlack: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onFollowThemeIconChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    appLanguage: AppLanguage,
    contentLanguage: String,
    onAppLanguageChange: (AppLanguage) -> Unit,
) {
    val navController = rememberNavController()
    val api = rememberApi()
    val context = LocalContext.current

    /**
     * 进详情页，顺带把卡片封面带上。
     *
     * NOTE: 封面必须从卡片传过去：详情数据要等一次请求，而共享容器首帧就得有东西可长——
     * 少了这张图，点卡片时封面会长进一块空灰面。
     */
    fun openDetail(id: String, thumbnail: String?) {
        navController.navigate("detail/$id?img=${Uri.encode(thumbnail.orEmpty())}")
    }
    // NOTE: 账户状态整个 App 共用一份：'我的' 与 '登录' 两个页面必须看到同一份状态。
    val auth: AuthViewModel = viewModel(factory = factory { AuthViewModel(api, ProfileStore(context.applicationContext), CredentialStore(context.applicationContext)) })

    // NOTE: 语言已经在 MissuRoot 里装进 SourceConfig 了，这里只是拿内容语言做重载的 key。
    val historyStore = remember { HistoryStore(context.applicationContext) }
    val searchHistory = remember { SearchHistoryStore(context.applicationContext) }
    // NOTE: 页签级的 ViewModel 必须提到 Activity 作用域。放在 composable("home") 里的话，
    // 切走页签时目的地被销毁、ViewModel 一并清掉，切回来就是「新建 VM → Loading → 重新请求」：
    // 用户看到的是先一帧旧画面、再一个转圈，正是"闪一下/像缓存"的来源。提到这里后切页签是纯粹的视图切换。
    val homeViewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(api) })
    val discoverViewModel: DiscoverViewModel = viewModel(factory = factory { DiscoverViewModel(api) })
    val searchViewModel: SearchViewModel = viewModel(factory = factory { SearchViewModel(api, searchHistory) })

    // NOTE: 语言变了才重拉首页。这个 effect 放在这里（一直处于组合中）而不是 home 目的地里：
    // 放在目的地里的话，每次切回页签都会重新启动 effect → load() → 打回 Loading → 转圈，
    // 也就是「先闪一帧旧画面再加载」的另一半原因。
    LaunchedEffect(contentLanguage) { homeViewModel.load() }
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
                            icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                            label = { Text(stringResource(item.labelRes)) },
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
            // 例外：涉及详情页时不做横滑，改用 container transform —— 卡片封面自己长成播放器，
            // 页面之间是 fade through（fade through 是唯一不会让两页内容叠在一起的做法）。
            enterTransition = {
                if (involvesDetail(initialState, targetState)) {
                    heroEnter()
                } else {
                    val forward = isForward(initialState, targetState, tabOrder)
                    slideInHorizontally(animationSpec = tween(durationMillis = 300)) { if (forward) it else -it }
                }
            },
            exitTransition = {
                if (involvesDetail(initialState, targetState)) {
                    heroExit()
                } else {
                    val forward = isForward(initialState, targetState, tabOrder)
                    slideOutHorizontally(animationSpec = tween(durationMillis = 300)) { if (forward) -it else it }
                }
            },
            popEnterTransition = {
                if (involvesDetail(initialState, targetState)) {
                    heroEnter()
                } else {
                    slideInHorizontally(animationSpec = tween(durationMillis = 300)) { -it }
                }
            },
            popExitTransition = {
                if (involvesDetail(initialState, targetState)) {
                    heroExit()
                } else {
                    slideOutHorizontally(animationSpec = tween(durationMillis = 300)) { it }
                }
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

                ProvideHeroVisibility(this) {
                    SearchHost(
                        state = searchState,
                        recent = recent,
                        onSearch = searchViewModel::search,
                        onLoadMore = searchViewModel::loadMore,
                        onOpen = ::openDetail,
                        onClearHistory = searchViewModel::clearHistory,
                        onClear = searchViewModel::clear,
                    ) {
                        if (searchState is SearchUiState.Idle) {
                            HomeScreen(
                                state = state,
                                refreshing = refreshing,
                                onRefresh = viewModel::refresh,
                                onRetry = viewModel::load,
                                onOpen = ::openDetail,
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
                                onOpen = ::openDetail,
                            )
                        }
                    }
                }
            }

            composable("discover") {
                val viewModel = discoverViewModel
                val states by viewModel.states.collectAsState()
                val sort by viewModel.sort.collectAsState()
                val filters by viewModel.filters.collectAsState()
                val refreshingKinds by viewModel.refreshing.collectAsState()

                // NOTE: 热门三档是同一面内容的三个 path，所以按 path 建 VM：换档 = 换数据源，
                // 切回来那份也已经缓存着（ViewModelStore 里各存一份，不会重新转圈）。
                var hotPeriod by rememberSaveable { mutableStateOf(HotPeriod.TODAY) }
                val hotViewModel: VideoListViewModel = viewModel(
                    key = "hot-${hotPeriod.path}",
                    factory = factory { VideoListViewModel(api, hotPeriod.path) },
                )
                val hotState by hotViewModel.state.collectAsState()
                LaunchedEffect(hotPeriod) {
                    // 只有还没取过的（初始就是 Loading）才发请求，切回已加载过的档位不重拉。
                    if (hotState is VideoListUiState.Loading) hotViewModel.load()
                }

                DiscoverScreen(
                    states = states,
                    sort = sort,
                    filters = filters,
                    refreshingKinds = refreshingKinds,
                    hotPeriod = hotPeriod,
                    hotState = hotState,
                    onLoad = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onRefresh = viewModel::refresh,
                    onHotPeriodChange = { hotPeriod = it },
                    onHotLoadMore = hotViewModel::loadMore,
                    onHotRetry = hotViewModel::load,
                    onOpen = { path, title ->
                        navController.navigate("list?p=${Uri.encode(path)}&t=${Uri.encode(title)}")
                    },
                    onOpenVideo = { id, thumbnail -> openDetail(id, thumbnail) },
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
                // NOTE: 自动播放只有播放器和这一行用到，不必像主题那样提到根部；进入设置页时
                // 从偏好读一次，改完立即写回（播放器下次创建时自然读到新值）。
                var autoplay by remember { mutableStateOf(settings.autoplay()) }
                SettingsScreen(
                    appLanguage = appLanguage,
                    themeMode = themeMode,
                    colorSource = colorSource,
                    followThemeIcon = followThemeIcon,
                    pureBlack = pureBlack,
                    autoplay = autoplay,
                    onAppLanguageChange = onAppLanguageChange,
                    onThemeModeChange = onThemeModeChange,
                    onColorSourceChange = onColorSourceChange,
                    onFollowThemeIconChange = onFollowThemeIconChange,
                    onPureBlackChange = onPureBlackChange,
                    onAutoplayChange = { enabled ->
                        settings.setAutoplay(enabled)
                        autoplay = enabled
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "list?p={p}&t={t}",
                arguments = listOf(
                    navArgument("p") { defaultValue = "/latest" },
                    // NOTE: 默认标题是空的，由页面用资源兜底——导航参数取不到组合作用域。
                    navArgument("t") { defaultValue = "" },
                ),
            ) { entry ->
                val path = Uri.decode(entry.arguments?.getString("p").orEmpty())
                val title = Uri.decode(entry.arguments?.getString("t").orEmpty())
                    .ifEmpty { stringResource(R.string.list_default_title) }
                val viewModel: VideoListViewModel = viewModel(
                    key = "list-$path",
                    factory = factory { VideoListViewModel(api, path) },
                )
                val state by viewModel.state.collectAsState()
                LaunchedEffect(path) { viewModel.load() }

                ProvideHeroVisibility(this) {
                    VideoListScreen(
                        title = title,
                        state = state,
                        onRetry = viewModel::load,
                        onLoadMore = viewModel::loadMore,
                        onOpen = ::openDetail,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("history") {
                val viewModel: HistoryViewModel = viewModel(
                    factory = factory { HistoryViewModel(historyStore) },
                )
                val entries by viewModel.entries.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                ProvideHeroVisibility(this) {
                    HistoryScreen(
                        entries = entries,
                        onOpen = ::openDetail,
                        onClear = viewModel::clear,
                        onBack = { navController.popBackStack() },
                    )
                }
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

                ProvideHeroVisibility(this) {
                    PlaylistDetailScreen(
                        fallbackTitle = fallbackTitle,
                        state = state,
                        message = message,
                        onRetry = viewModel::load,
                        onOpen = ::openDetail,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate("playlist/$id/edit") },
                        onRemove = viewModel::removeAll,
                    )
                }
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

                ProvideHeroVisibility(this) {
                    SavedScreen(
                        state = state,
                        onRetry = viewModel::load,
                        onLoadMore = viewModel::loadMore,
                        onOpen = ::openDetail,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(
                route = "detail/{id}?img={img}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    // NOTE: 卡片封面。导航参数是唯一在详情数据回来之前就能拿到的画面，
                    // 共享容器（封面 → 播放器）首帧要靠它。
                    navArgument("img") { defaultValue = "" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val poster = Uri.decode(entry.arguments?.getString("img").orEmpty())
                // NOTE: key 用 id —— 换一集详情时复用同一个 ViewModel 会显示上一条数据。
                val viewModel: DetailViewModel = viewModel(
                    key = "detail-$id",
                    factory = factory { DetailViewModel(api, id, historyStore) },
                )
                val state by viewModel.state.collectAsState()
                val message by viewModel.message.collectAsState()
                val playlists by viewModel.playlists.collectAsState()
                LaunchedEffect(id) { viewModel.load() }

                ProvideHeroVisibility(this) {
                    DetailScreen(
                        state = state,
                        onRetry = viewModel::load,
                        onBack = { navController.popBackStack() },
                        videoId = id,
                        posterUrl = poster.ifEmpty { null },
                        // NOTE: 返回时这一项会先变 false（popBackStack 是同步的），
                        // 详情页据此在转场第一帧就把容器换回封面、停掉音频。
                        isCurrent = backStackEntry?.id == entry.id,
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
}

/** 底部导航的一级页面。NOTE: 标签存资源 id——它是文件级常量，取不到组合作用域。 */
private data class TopLevel(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

// NOTE: 搜索不再单独占一个页签——「发现」顶部就是搜索入口，结果页仍在（search 路由），
// 底部只留三个一级页面：首页 / 发现 / 我的。
private val topLevels = listOf(
    TopLevel("home", R.string.nav_home, Icons.Filled.Home),
    TopLevel("discover", R.string.nav_discover, Icons.AutoMirrored.Filled.List),
    TopLevel("account", R.string.nav_account, Icons.Filled.Person),
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

/**
 * 这次交接里有没有详情页。
 *
 * NOTE: 详情页与它的来源之间走 container transform（封面长成播放器 + fade through），
 * 其余页面之间仍是整幅横滑。前进与返回都要认这个分支，否则返回时会变成「横滑 + 封面反向飞」
 * 两套动效同时放。
 */
private fun involvesDetail(initial: NavBackStackEntry, target: NavBackStackEntry): Boolean =
    initial.destination.route?.startsWith("detail/") == true ||
        target.destination.route?.startsWith("detail/") == true

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
