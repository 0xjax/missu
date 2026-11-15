@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.LocalResources
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
import app.missu.ui.discover.HOT_PATH
import app.missu.data.api.ChallengeHandler
import app.missu.data.api.MissavApi
import app.missu.data.api.SourceConfig
import app.missu.data.api.VideoListOptions
import app.missu.data.api.createMissavApi
import app.missu.data.store.AppLanguage
import app.missu.data.store.contentLanguage
import app.missu.data.store.uiLocale
import app.missu.data.store.CredentialStore
import app.missu.data.store.ColorSource
import app.missu.data.store.ProfileStore
import app.missu.data.store.HistoryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.SearchHistoryStore
import app.missu.data.store.SettingsStore
import app.missu.data.store.ThemeMode
import android.app.Activity
import app.missu.ui.challenge.ChallengeDiagnostics
import app.missu.ui.challenge.ChallengeOverlayHost
import app.missu.ui.challenge.ChallengeState
import app.missu.ui.history.HistoryScreen
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import app.missu.ui.history.HistoryViewModel
import app.missu.ui.components.PageSurface
import app.missu.ui.list.VideoListQuery
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
    // NOTE: 人机验证的宿主同样挂在根部（理由同上）。它也是 Dialog，所以验证期间能盖住底栏与系统栏；
    // 而且它就在主题与语言覆写之内，不需要自己再解析一遍明暗/色板/界面语言。
    val challengeState = remember { ChallengeState() }
    // NOTE: 仅 debuggable 构建的预览入口——不触发真实请求就能把防护层抬起来，用来在明暗/色板组合下看效果：
    // `adb shell am force-stop app.missu && adb shell am start -n app.missu/.MainActivity \
    //      --es challenge_preview file:///android_asset/challenge-preview.html\#widget`
    // 真实挑战何时出现由站点决定、造不出来（见 docs/android-native.md），所以留这条可控的预览通道。
    LaunchedEffect(challengeState) {
        if (!ChallengeDiagnostics.enabled(context)) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        val url = activity.intent?.getStringExtra("challenge_preview") ?: return@LaunchedEffect
        challengeState.preview(url)
    }
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
                    // NOTE: `stringResource` 走的是 `LocalResources`（compose-ui 的计算型 local）。
                    // 弹层（DropdownMenu / ModalBottomSheet）会以窗口自身的 context 重新起一段组合，
                    // 只覆写 LocalContext/LocalConfiguration 时它们的文案会掉回系统语言；这里把
                    // Resources 也显式给出去，弹层里才能跟界面语言一致。
                    LocalResources provides localizedContext.resources,
                    LocalVideoPreview provides previewState,
                ) {
                    MissuApp(
                        settings = settings,
                        challengeState = challengeState,
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
                    ChallengeOverlayHost(state = challengeState)
                }
            }
        }
    }
}

/** 取数入口：用 applicationContext（数据层不持有 Activity 引用）。验证面是组合里的一层 Dialog。 */
@Composable
private fun rememberApi(challenge: ChallengeHandler): MissavApi {
    val context = LocalContext.current
    return remember { createMissavApi(context.applicationContext, challenge) }
}

@Composable
private fun MissuApp(
    settings: SettingsStore,
    challengeState: ChallengeState,
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

    val api = rememberApi(challengeState)
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
    // NOTE: 列表的「站点默认排序」按路径学一次，之后每次进同一列表的首帧就能直接写成实际项——
    // 不学的话排序芯片每进一次都先显示「默认」再跳（见 ListSortStore）。
    val listSortStore = remember { ListSortStore(context.applicationContext) }
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
    // NOTE: 顶栏/底栏的归属见 TABS_ROUTE；这里只保留「这个 entry 是否已离开返回栈」。

    // NOTE: 系统栏、刘海、输入法的内边距只在这里处理一次，各页面只负责内容布局。
    // 底部由 NavigationBar 自己处理导航栏 inset，所以内容区只吃顶部与左右。
    Scaffold(
        // NOTE: 不给顶部内边距——顶部让给页面自己（PageSurface 铺满整屏、内容让出状态栏），
        // 否则返回手势把页面缩成卡片时，状态栏那条会留在卡片外面。
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal,
        ),
        // NOTE: 底栏不在这里——它在 TABS_ROUTE 那个页面内部（三个页签是一个页面的三种形态）。
    ) { innerPadding ->
        val tabOrder = topLevels.map { it.route }
        NavHost(
            navController = navController,
            startDestination = TABS_ROUTE,
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
            popEnterTransition = { missuPopEnter() },
            popExitTransition = { missuPopExit() },
            // NOTE: 预测式返回（侧滑还没松手）走的是这两个，签名是 `Scope.(swipeEdge) -> …`。
            // 默认值是 fadeIn(spring) + scaleOut(0.7)：两页不透明地叠在一起，实测文字重影。
            // 两段各管一段：手势期间只缩放，松手后才平移离开（见下面 missuPopEnter/Exit）
            // 接着放完的那段，中间没有"预览一个、落地另一个"的换挡。
            predictivePopEnterTransition = { missuPopEnter() },
            predictivePopExitTransition = { missuPopExit() },
        ) {
            composable(TABS_ROUTE) { entry ->
                // NOTE: 三个页签是**一个页面**的三种形态，不是三个可返回的目的地：
                // 底栏属于它们本身，所以返回预览里进入的页面自带底栏，切页签时底栏也不动。
                var tab by rememberSaveable { mutableStateOf("home") }
                val navScope = this
                Scaffold(
                    // NOTE: 这里不能再吃状态栏内边距——每个页签页自己已经让过一次
                    // （`PageSurface` / `SearchHost`）。重复让就会在顶部多出一条状态栏高度的空白。
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        ShortNavigationBar {
                            topLevels.forEach { item ->
                                ShortNavigationBarItem(
                                    selected = tab == item.route,
                                    onClick = { if (tab != item.route) tab = item.route },
                                    icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                                    label = { Text(stringResource(item.labelRes)) },
                                )
                            }
                        }
                    },
                ) { tabPadding ->
                    AnimatedContent(
                        targetState = tab,
                        modifier = Modifier.padding(tabPadding),
                        transitionSpec = {
                            val forward = topLevels.indexOfFirst { it.route == targetState } >
                                topLevels.indexOfFirst { it.route == initialState }
                            slideInHorizontally(animationSpec = tween(durationMillis = 300)) { if (forward) it else -it } togetherWith
                                slideOutHorizontally(animationSpec = tween(durationMillis = 300)) { if (forward) -it else it }
                        },
                        label = "tab",
                    ) { current ->
                        when (current) {
                            "home" -> {
                val viewModel = homeViewModel
                val state by viewModel.state.collectAsState()
                val refreshing by viewModel.refreshing.collectAsState()
                // NOTE: 搜索不再是独立页面——它和首页同属一个目的地，栏在顶部就地展开。
                // 页面切换会让输入框换一份样式、键盘重启一次，那是"割裂感"的来源。
                val searchState by searchViewModel.state.collectAsState()
                val recent by searchViewModel.recent.collectAsState()
                // NOTE: 用 loadIfNeeded 而不是 load：切回页签时不要把已有内容打回 Loading。
                LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

                ProvideHeroVisibility(navScope, namespace = "home") {
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
                            PageSurface(card = false) {
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
                            }
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
                            "discover" -> {
                val viewModel = discoverViewModel
                val states by viewModel.states.collectAsState()
                val sort by viewModel.sort.collectAsState()
                val filters by viewModel.filters.collectAsState()
                val refreshingKinds by viewModel.refreshing.collectAsState()
                val channels by viewModel.channels.collectAsState()

                // NOTE: 热门就是 `/today-hot` 一面列表：今日 / 本周 / 本月只是它的三个排序取值（实测与三个
                // vanity path 等价），过滤也同享。所以它和列表页用**同一套口径**（同一个 `VideoListQuery`
                // 与 `VideoListContent`），不另造一套「周期」结构；按 cacheKey 建 VM，切回来那份还在缓存里。
                var hotSort by rememberSaveable { mutableStateOf<VideoListOptions.Sort?>(null) }
                var hotFilter by rememberSaveable { mutableStateOf<VideoListOptions.Filter?>(null) }
                val hotQuery = remember(hotSort, hotFilter) {
                    VideoListQuery(HOT_PATH, sort = hotSort, filter = hotFilter)
                }
                val hotViewModel: VideoListViewModel = viewModel(
                    key = "hot-${hotQuery.cacheKey}",
                    factory = factory { VideoListViewModel(api, hotQuery, listSortStore) },
                )
                val hotState by hotViewModel.state.collectAsState()
                val hotDetectedSort by hotViewModel.detectedSort.collectAsState()
                LaunchedEffect(hotQuery) {
                    // 只有还没取过的（初始就是 Loading）才发请求，切回已取过的口径不重拉。
                    if (hotState is VideoListUiState.Loading) hotViewModel.load()
                }

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
                        channels = channels,
                        onLoad = viewModel::load,
                        onLoadMore = viewModel::loadMore,
                        onRefresh = viewModel::refresh,
                        onChannelsLoad = { viewModel.loadChannels() },
                        onChannelsRetry = { viewModel.loadChannels(force = true) },
                        onHotSortChange = { hotSort = it },
                        onHotFilterChange = { hotFilter = it },
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

            composable("login") { entry ->
                PageSurface {
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
            }
            composable("settings") { entry ->
                // NOTE: 自动播放只有播放器和这一行用到，不必像主题那样提到根部；进入设置页时
                // 从偏好读一次，改完立即写回（播放器下次创建时自然读到新值）。
                var autoplay by remember { mutableStateOf(settings.autoplay()) }
                PageSurface {
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

                // NOTE: 从首页「更多」进这一页时**不做**卡片→列表项的逐点共享元素：两边条数不同、
                // 进入后要按真实滚动位置落位、而且可能带着筛选，逐项动画必然参差。那个方向本身是
                // 成立的（数据同源、位置可对齐时），先记在这里；当前统一走「页面级转场 + 口径切换的
                // fade through」这两条既有分支，一致性优先。
                //
                // NOTE: 口径（排序/过滤）是这一页的**状态**，与 path 一起构成 `VideoListQuery`。
                // 换口径 = 换一个 ViewModel 实例（按 cacheKey），页面结构一点不动；切回已看过的
                // 口径还能复用那份数据，不重新转圈。
                var sort by rememberSaveable { mutableStateOf<VideoListOptions.Sort?>(null) }
                var filter by rememberSaveable { mutableStateOf<VideoListOptions.Filter?>(null) }
                val query = remember(path, sort, filter) { VideoListQuery(path, sort = sort, filter = filter) }
                val viewModel: VideoListViewModel = viewModel(
                    key = "list-${query.cacheKey}",
                    factory = factory { VideoListViewModel(api, query, listSortStore) },
                )
                val state by viewModel.state.collectAsState()
                val detectedSort by viewModel.detectedSort.collectAsState()
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
                            onOpen = ::openDetail,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
            composable("history") { entry ->
                val viewModel: HistoryViewModel = viewModel(
                    factory = factory { HistoryViewModel(historyStore) },
                )
                val entries by viewModel.entries.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                ProvideHeroVisibility(this, namespace = "history") {
                    PageSurface {
                        HistoryScreen(
                            entries = entries,
                            onOpen = ::openDetail,
                            onClear = viewModel::clear,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
            composable("playlists") { entry ->
                val viewModel: PlaylistsViewModel = viewModel(factory = factory { PlaylistsViewModel(api) })
                val state by viewModel.state.collectAsState()
                val createMessage by viewModel.createMessage.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                PageSurface {
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

                ProvideHeroVisibility(this, namespace = "playlist") {
                    PageSurface {
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
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable("saved") { entry ->
                val viewModel: SavedViewModel = viewModel(factory = factory { SavedViewModel(api) })
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                ProvideHeroVisibility(this, namespace = "saved") {
                    PageSurface {
                        SavedScreen(
                            state = state,
                            onRetry = viewModel::load,
                            onLoadMore = viewModel::loadMore,
                            onOpen = ::openDetail,
                            onBack = { navController.popBackStack() },
                        )
                    }
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

                // NOTE: 共享元素的命名空间取「来源目的地的路由名」——详情页只和真正被点的那张卡片
                // 配对。首页预览与完整列表里都有同一批影片，不分区的话它们之间也会互相配对，
                // 转场时只有两边都有的那几张封面在飞、其余跟着页面横滑（实测）。
                val heroSource = navController.previousBackStackEntry?.destination?.route
                    ?.substringBefore('?')
                    ?.substringBefore('/')
                    ?.ifEmpty { "home" }
                    ?: "home"

                ProvideHeroVisibility(this, namespace = heroSource) {
                    PageSurface {
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
}

/** 底部导航的一级页面。NOTE: 标签存资源 id——它是文件级常量，取不到组合作用域。 */
private data class TopLevel(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

// NOTE: 搜索不再单独占一个页签——「发现」顶部就是搜索入口，结果页仍在（search 路由），
// 底部只留三个一级页面：首页 / 发现 / 我的。
private const val TABS_ROUTE = "tabs"

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

/**
 * 返回转场的数值，取自官方「预测性返回 · 全屏 Surface」Motion 规范：退出缩到 **90%**、
 * 进入从 **110%** 落回 100%。
 *
 * 唯一偏离规范的是**时间分布**：规范把缩放和淡出都压在前 35% 进度里，我们把缩放铺满整段、
 * 淡出放到最后 20%。原因：规范那 35% 的交接假设「退场页底下另有一个窗口」（系统动画底下是
 * 桌面或上一个 Activity），两页同时不可见时看到的是那个窗口；本工程是单 Activity、页面下方
 * 就是窗口底色。照搬只有两种结果——按规范淡完再淡入，拖到 1/3 处整屏空底色；让退场页盖着
 * 不淡的上一页淡出，两页文字叠在一起。两种都在实机上抓到了帧，所以改成：预览区间内退场页
 * 不透明，缩多少由手指给多少。
 */
/**
 * 返回转场的数值。
 *
 * 两段：手势阶段（`predictivePop*`）只有缩放，松手后（`pop*`）由页面自己播固定时长的平移。
 * 缩放取官方「预测性返回 · 全屏 Surface」的 90%；**时间线放到 600ms**——它同时决定了
 * 「拉满 = 缩到底」和「松手后转场还剩多少时间可以走完平移动画」。
 */
/**
 * 返回转场的数值（官方「预测性返回 · 全屏 Surface」的 90%）。
 *
 * 手势与松手共用同一条 500ms 时间线：前 60% 只缩放（跟手指），后 40% 向右平移并提亮下层。
 */
private object PopMotion {
    const val TOTAL_MS = 500

    /** 退场页缩到 90%。 */
    const val EXIT_SCALE = 0.9f

    /** 下层在预览期间压到的亮度；松手后提亮回 1。 */
    const val UNDER_ALPHA = 0.88f

    /** 平移在 60% 进度后才起手：手势期间只缩放，松手后它才把页面推出去。 */
    const val SLIDE_DELAY_MS = 300

    /** 平移自身的时长。 */
    const val SLIDE_MS = 200
}

/** 详情页走 container transform（规范：用 Navigation 组件时不要缩放整屏）。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.missuPopEnter(): EnterTransition =
    if (involvesDetail(initialState, targetState)) {
        heroEnter()
    } else {
        fadeIn(
            animationSpec = keyframes {
                durationMillis = PopMotion.TOTAL_MS
                PopMotion.UNDER_ALPHA at 0
                PopMotion.UNDER_ALPHA at PopMotion.SLIDE_DELAY_MS
                1f at PopMotion.TOTAL_MS
            },
            initialAlpha = PopMotion.UNDER_ALPHA,
        )
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.missuPopExit(): ExitTransition =
    if (involvesDetail(initialState, targetState)) {
        heroExit()
    } else {
        scaleOut(
            animationSpec = tween(durationMillis = PopMotion.TOTAL_MS),
            targetScale = PopMotion.EXIT_SCALE,
        ) + slideOutHorizontally(
            // 60% 之后才起手：拖动期间不会看到它挪动，松手后它就是「推出去」那一下。
            animationSpec = tween(
                durationMillis = PopMotion.SLIDE_MS,
                delayMillis = PopMotion.SLIDE_DELAY_MS,
            ),
        ) { it }
    }

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
