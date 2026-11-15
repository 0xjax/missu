@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
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
import app.missu.ui.components.underScrim
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
    // NOTE: key 里要带上系统 configuration。播放器全屏会让 Activity 转到横屏（`configChanges` 挡住
    // 重建，组合不重启），只按语言缓存的话，横屏里 `LocalConfiguration`/`LocalResources` 还是竖屏
    // 那一份——按屏宽/屏高算尺寸的组件（倍速面板这类）会拿错尺寸。
    val localizedContext = remember(appLanguage, systemConfiguration) {
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
    // NOTE: 纯按钮返回（顶栏 up 箭头）没有手势深度，不该走「缩到 90% + 下层压暗」那套，
    // 它只播一记「向右滑出」；手势松手才走缩放那条线。
    // 记的是**被弹出的那个 entry 的 id**，不是布尔量：布尔量要在某处复位，而复位时机
    // 会和转场读它的时机赛跑（亚毫秒级的先后差别就够让缩放那一套生效）。
    var buttonPopId by remember { mutableStateOf<String?>(null) }
    // NOTE: 当前页签提到这里：三个页签合成了一个目的地（TABS_ROUTE），而详情页的共享元素
    // 命名空间来自「上一个目的地的路由」——那只会是 tabs，所以还得知道是哪个页签。
    var tabRoute by rememberSaveable { mutableStateOf("home") }

    val api = rememberApi(challengeState)
    val context = LocalContext.current

    /**
     * 进详情页，顺带把卡片封面带上。
     *
     * NOTE: 封面必须从卡片传过去：详情数据要等一次请求，而共享容器首帧就得有东西可长——
     * 少了这张图，点卡片时封面会长进一块空灰面。
     * NOTE: 共享元素只认 key。首页是多个板块拼成的一页，站点自己就会把同一部影片放进两个板块，
     * 两张卡片算出同一个 key 时配对会落到另一张上（封面朝屏外飞）。所以首页那张卡片把**来源标签**
     * 一路带到详情页，两侧用同一个值配对（见 heroKeyFor）；其它页面一页里 id 不会重复。
     */
    fun openDetail(id: String, thumbnail: String?, heroTag: String) {
        navController.navigate(
            "detail/$id?img=${Uri.encode(thumbnail.orEmpty())}&h=${Uri.encode(heroTag)}",
        )
    }

    /** 同 [openDetail]，来源一页里不会有重复卡片时用这个（列表 / 收藏 / 历史 / 片单 / 搜索结果）。 */
    fun openDetail(id: String, thumbnail: String?) {
        openDetail(id, thumbnail, "")
    }

    fun goBack() {
        buttonPopId = navController.currentBackStackEntry?.id
        navController.popBackStack()
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
    // NOTE: 不在这里复位 buttonPopId——它只在「按钮返回」那一帧被比对，过期值不会再命中任何 entry。
    // NOTE: 顶栏/底栏的归属见 TABS_ROUTE；这里只保留「这个 entry 是否已离开返回栈」。

    // NOTE: 系统栏、刘海、输入法的内边距只在这里处理一次，各页面只负责内容布局。
    // 底部由 NavigationBar 自己处理导航栏 inset，所以内容区只吃顶部与左右。
    Scaffold(
        // NOTE: 不给顶部内边距——顶部让给页面自己（PageSurface 铺满整屏、内容让出状态栏），
        // 否则返回手势把页面缩成卡片时，状态栏那条会留在卡片外面。
        // NOTE: 左右只取**系统栏**，不取 `safeDrawing`：横屏时刘海会变成左右一条 104px 的
        // 挖孔内边距，播放器全屏就少一条、露页面底色（实机可见）。竖屏下两者左右都是 0，
        // 所以这个取舍只影响横屏播放——而那正是画面必须铺到物理边缘的场合。
        contentWindowInsets = WindowInsets.systemBars.only(
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
            popExitTransition = { missuPopExit(button = initialState.id == buttonPopId) },
            // NOTE: 预测式返回（侧滑还没松手）走的是这两个，签名是 `Scope.(swipeEdge) -> …`。
            // 默认值是 fadeIn(spring) + scaleOut(0.7)：两页不透明地叠在一起，实测文字重影。
            // 两段各管一段：手势期间只缩放，松手后才平移离开（见下面 missuPopEnter/Exit）
            // 接着放完的那段，中间没有"预览一个、落地另一个"的换挡。
            predictivePopEnterTransition = { missuPopEnter() },
            predictivePopExitTransition = { missuPopExit(button = false) },
        ) {
            composable(TABS_ROUTE) { entry ->
                // NOTE: 三个页签是**一个页面**的三种形态，不是三个可返回的目的地：
                // 底栏属于它们本身，所以返回预览里进入的页面自带底栏，切页签时底栏也不动。
                // 页签状态在 MissuApp（详情页要用它决定共享元素命名空间）。
                val navScope = this
                // NOTE: 页签是这个目的地**内部**的状态，返回栈上没有它们——不接管返回键的话，
                // 在「发现」「我的」按返回会直接结束 Activity。按 Android 的约定退一层：非起始
                // 页签先回到起始页签，到了起始页签再按才是退出应用。
                // NOTE: 只在「不在起始页签」时启用，否则它会抢走首页那层的返回键（搜索态的
                // BackHandler 在 SearchHost 里，见那儿）。
                val startTab = topLevels.first().route
                BackHandler(enabled = tabRoute != startTab) { tabRoute = startTab }
                Scaffold(
                    // NOTE: 预测返回时把这一页压暗（详情/列表等二级页都从这一页被揭开），
                    // 见 `underScrim`。挂在 Scaffold 自己的绘制上，底栏一起盖。
                    modifier = Modifier.underScrim(
                        scope = navScope,
                        covered = backStackEntry?.id != entry.id,
                        // NOTE: 详情页的返回是 container transform，下层是淡入的；那种揭示不压暗
                        // （见 `underScrim` 的 fadingIn）。这里用「栈顶是不是详情页」来判断——
                        // 预测手势还没提交时它仍然在栈顶。
                        fadingIn = backStackEntry?.destination?.route?.startsWith("detail/") == true,
                    ),
                    // NOTE: 这里不能再吃状态栏内边距——每个页签页自己已经让过一次
                    // （`PageSurface` / `SearchHost`）。重复让就会在顶部多出一条状态栏高度的空白。
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        ShortNavigationBar {
                            topLevels.forEach { item ->
                                ShortNavigationBarItem(
                                    selected = tabRoute == item.route,
                                    onClick = { if (tabRoute != item.route) tabRoute = item.route },
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
                            // NOTE: `contentInset = false` 是「沉浸状态栏」的一半：首页内容从 y=0 开始，
                            // 状态栏那一条由 SearchHost 的纯色半透明盖住；其它页面保持默认（让开状态栏），
                            // 所以它们的顶部一个像素都不变。
                            PageSurface(card = false, contentInset = false) {
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
                                ProvideHeroVisibility(navScope, namespace = "discover") {
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
                            auth.login(email, password) { goBack() }
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
                        onBack = { goBack() },
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
                            onBack = { goBack() },
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
                            onBack = { goBack() },
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
                        onBack = { goBack() },
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
                            onBack = { goBack() },
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
                        onBack = { goBack() },
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
                            onBack = { goBack() },
                        )
                    }
                }
            }
            composable(
                route = "detail/{id}?img={img}&h={h}",
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType },
                    // NOTE: 卡片封面。导航参数是唯一在详情数据回来之前就能拿到的画面，
                    // 共享容器（封面 → 播放器）首帧要靠它。
                    navArgument("img") { defaultValue = "" },
                    // NOTE: 卡片的来源标签（只有首页会带）。首页多板块会把同一部影片放两处，
                    // 光靠 id 详情页会和另一张卡片配对，见 heroKeyFor。
                    navArgument("h") { defaultValue = "" },
                ),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                val poster = Uri.decode(entry.arguments?.getString("img").orEmpty())
                val heroTag = Uri.decode(entry.arguments?.getString("h").orEmpty())
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
                // NOTE: 共享元素的命名空间必须和卡片的提供方一致（见 ProvideHeroVisibility）。
                // 三个页签合并成了一个目的地，上一站只会是 `tabs`，所以那种情况要用当前页签；
                // 其它来源（列表 / 收藏 / 历史 / 片单）直接用它们的路由名。
                val previousRoute = navController.previousBackStackEntry?.destination?.route
                    ?.substringBefore('?')
                    ?.substringBefore('/')
                val heroSource = if (previousRoute == TABS_ROUTE || previousRoute.isNullOrEmpty()) {
                    tabRoute
                } else {
                    previousRoute
                }

                ProvideHeroVisibility(this, namespace = heroSource) {
                    // NOTE: 详情页自己吃状态栏：顶部要画一条**黑**的（和播放器/全屏的黑底接上，
                    // 转屏时不闪白条），所以不让 PageSurface 统一让出状态栏内边距。
                    PageSurface(contentInset = false) {
                        DetailScreen(
                            state = state,
                            onRetry = viewModel::load,
                            videoId = id,
                            heroTag = heroTag,
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

// NOTE: 底部三个一级页面：首页 / 发现 / 我的。搜索不占页签，也**不是**独立目的地——它是首页
// 目的地内部的一个状态（见 SearchHost），所以它的返回键得由它自己接管。
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
 * 返回转场的数值。
 *
 * 两段：手势阶段（`predictivePop*`）只有缩放，松手后（`pop*`）由页面自己播固定时长的平移。
 * 缩放取官方「预测性返回 · 全屏 Surface」的 90%；时间线 500ms 同时决定了
 * 「拉满 = 缩到底」和「松手后转场还剩多少时间可以走完平移动画」。
 * 下层页面本身**不动也不淡**——它的压暗由 `underScrim` 单独画（那才是能看出来的暗）。
 *
 * NOTE: 预览区间内退场页保持不透明，缩多少由手指给多少。官方规范把缩放与淡出压在前 35%，
 * 但那是假设退场页底下另有一个窗口（系统动画底下是桌面 / 上一个 Activity）；本工程是单
 * Activity、页面下方就是窗口底色，照搬会拖到 1/3 处整屏空底色（实机抓到过帧）。
 */
private object PopMotion {
    const val TOTAL_MS = 500

    /** 退场页缩到 90%。 */
    const val EXIT_SCALE = 0.9f

    /** 平移在 60% 进度后才起手：手势期间只缩放，松手后它才把页面推出去。 */
    const val SLIDE_DELAY_MS = 300

    /** 平移自身的时长。 */
    const val SLIDE_MS = 200

    /** 按钮返回（up 箭头）整段时长：只滑出，不缩放。 */
    const val BUTTON_MS = 260
}

/**
 * 详情页走 container transform（规范：用 Navigation 组件时不要缩放整屏）；
 * 其余返回只由退场页自己演（缩放 + 平移），下层页面保持原样，压暗交给 `underScrim`。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.missuPopEnter(): EnterTransition =
    if (involvesDetail(initialState, targetState)) heroEnter() else EnterTransition.None

private fun AnimatedContentTransitionScope<NavBackStackEntry>.missuPopExit(button: Boolean): ExitTransition =
    if (involvesDetail(initialState, targetState)) {
        heroExit()
    } else if (button) {
        // 按钮返回：不缩放，直接向右滑出。
        slideOutHorizontally(
            animationSpec = tween(durationMillis = PopMotion.BUTTON_MS),
        ) { it }
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
