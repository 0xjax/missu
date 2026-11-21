@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package app.missu

import android.app.Activity
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.missu.data.api.AppDependencies
import app.missu.data.api.LocalAppDependencies
import app.missu.data.api.SourceConfig
import app.missu.data.model.VideoListQuery
import app.missu.data.store.AppLanguage
import app.missu.data.store.ColorSource
import app.missu.data.store.CredentialStore
import app.missu.data.store.HistoryStore
import app.missu.data.store.ListHistoryStore
import app.missu.data.store.ListQueryStore
import app.missu.data.store.ListSortStore
import app.missu.data.store.LiteratureStore
import app.missu.data.store.ProfileStore
import app.missu.data.store.SearchHistoryStore
import app.missu.data.store.SettingsStore
import app.missu.data.store.ThemeMode
import app.missu.data.store.contentLanguage
import app.missu.data.store.uiLocale
import app.missu.ui.account.AuthViewModel
import app.missu.ui.challenge.ChallengeDiagnostics
import app.missu.ui.challenge.ChallengeOverlayHost
import app.missu.ui.challenge.ChallengeState
import app.missu.ui.components.LocalSharedTransitionScope
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.preview.LocalVideoPreview
import app.missu.ui.preview.VideoPreviewHost
import app.missu.ui.preview.VideoPreviewState
import app.missu.ui.theme.LauncherIcons
import app.missu.ui.theme.MissuTheme
import app.missu.ui.theme.themeBackgroundColor
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 窗口底色必须在第一帧之前就跟上主题，之后由 MissuTheme 持续同步。
        applyWindowBackground()
        // 启动时只保证至少还有一个桌面入口；换色发生在应用进后台之后。
        val iconSettings = SettingsStore(applicationContext)
        LauncherIcons.ensureOne(applicationContext, iconSettings.desiredIconSource())
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { MissuRoot() }
    }

    private fun applyWindowBackground() {
        val settings = SettingsStore(applicationContext)
        val nightMask = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val darkTheme = when (settings.themeMode()) {
            ThemeMode.SYSTEM -> nightMask == Configuration.UI_MODE_NIGHT_YES
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val color = themeBackgroundColor(applicationContext, darkTheme, settings.colorSource())
        window.setBackgroundDrawable(color.toArgb().toDrawable())
    }
}

/** 主题、语言和全局浮层的唯一持有者。 */
@Composable
private fun MissuRoot() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context.applicationContext) }
    val previewState = remember { VideoPreviewState() }
    val challengeState = remember { ChallengeState() }
    val dependencies = remember { AppDependencies(context.applicationContext, challengeState) }
    LaunchedEffect(dependencies) {
        try {
            dependencies.literatureRepository.warmUp()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("Literature", "Warm-up failed", error)
        }
    }
    setSingletonImageLoaderFactory { imageContext ->
        ImageLoader.Builder(imageContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { dependencies.httpClient }))
            }
            .crossfade(true)
            .build()
    }
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
    var singleColumnVideos by remember { mutableStateOf(settings.singleColumnVideos()) }
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 在第一次请求前同步内容语言；localizedContext 同时跟踪系统配置与全屏旋转。
    val systemConfiguration = LocalConfiguration.current
    remember(appLanguage) {
        appLanguage.contentLanguage(systemConfiguration).also { SourceConfig.contentLanguage = it }
    }
    val localizedContext = remember(appLanguage, systemConfiguration) {
        val locale = appLanguage.uiLocale(systemConfiguration)
        val configuration = Configuration(systemConfiguration).apply { setLocale(locale) }
        context.createConfigurationContext(configuration)
    }

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
            SharedTransitionLayout {
                CompositionLocalProvider(
                    LocalSharedTransitionScope provides this,
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedContext.resources.configuration,
                    LocalResources provides localizedContext.resources,
                    LocalVideoPreview provides previewState,
                    LocalAppDependencies provides dependencies,
                    LocalVideoListColumns provides if (singleColumnVideos) 1 else 2,
                ) {
                    MissuApp(
                        settings = settings,
                        challengeState = challengeState,
                        appLanguage = appLanguage,
                        themeMode = themeMode,
                        colorSource = colorSource,
                        followThemeIcon = followThemeIcon,
                        pureBlack = pureBlack,
                        singleColumnVideos = singleColumnVideos,
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
                        },
                        onPureBlackChange = { enabled ->
                            settings.setPureBlack(enabled)
                            pureBlack = enabled
                        },
                        onSingleColumnVideosChange = { enabled ->
                            settings.setSingleColumnVideos(enabled)
                            singleColumnVideos = enabled
                        },
                    )
                    VideoPreviewHost(state = previewState)
                    ChallengeOverlayHost(state = challengeState)
                }
            }
        }
    }
}

@Composable
private fun MissuApp(
    settings: SettingsStore,
    challengeState: ChallengeState,
    themeMode: ThemeMode,
    colorSource: ColorSource,
    followThemeIcon: Boolean,
    pureBlack: Boolean,
    singleColumnVideos: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onFollowThemeIconChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onSingleColumnVideosChange: (Boolean) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
) {
    val navController = rememberNavController()
    var buttonPopId by remember { mutableStateOf<String?>(null) }
    var tabRoute by rememberSaveable { mutableStateOf("home") }
    val dependencies = LocalAppDependencies.current
    val api = dependencies.api
    val literatureRepository = dependencies.literatureRepository
    val context = LocalContext.current
    val listQueryStore = remember { ListQueryStore(context.applicationContext) }

    fun openDetail(id: String, thumbnail: String?, heroTag: String) {
        navController.navigate(
            "detail/$id?img=${Uri.encode(thumbnail.orEmpty())}&h=${Uri.encode(heroTag)}",
        )
    }

    fun openListQuery(query: VideoListQuery, title: String) {
        navController.navigate(
            "list?p=${Uri.encode(query.path)}&t=${Uri.encode(title)}" +
                "&s=${query.sort?.name.orEmpty()}&f=${query.filter?.name.orEmpty()}",
        )
    }

    fun openList(path: String, title: String) {
        openListQuery(listQueryStore.get(path), title)
    }

    fun goBack() {
        buttonPopId = navController.currentBackStackEntry?.id
        navController.popBackStack()
    }

    val auth: AuthViewModel = viewModel(
        factory = missuViewModelFactory {
            AuthViewModel(
                api,
                ProfileStore(context.applicationContext),
                CredentialStore(context.applicationContext),
            )
        },
    )
    val historyStore = remember { HistoryStore(context.applicationContext) }
    val listHistoryStore = remember { ListHistoryStore(context.applicationContext) }
    val searchHistory = remember { SearchHistoryStore(context.applicationContext) }
    val listSortStore = remember { ListSortStore(context.applicationContext) }
    val literatureStore = remember { LiteratureStore(context.applicationContext) }
    val backStackEntry = navController.currentBackStackEntryAsState()
    val tabRouteState = remember { derivedStateOf { tabRoute } }
    val settingsState = AppSettingsState(
        appLanguage = appLanguage,
        themeMode = themeMode,
        colorSource = colorSource,
        followThemeIcon = followThemeIcon,
        pureBlack = pureBlack,
        singleColumnVideos = singleColumnVideos,
    )
    val settingsActions = AppSettingsActions(
        onAppLanguageChange = onAppLanguageChange,
        onThemeModeChange = onThemeModeChange,
        onColorSourceChange = onColorSourceChange,
        onFollowThemeIconChange = onFollowThemeIconChange,
        onPureBlackChange = onPureBlackChange,
        onSingleColumnVideosChange = onSingleColumnVideosChange,
    )

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val tabOrder = topLevels.map { it.route }
        NavHost(
            navController = navController,
            startDestination = TABS_ROUTE,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                if (involvesDetail(initialState, targetState)) {
                    app.missu.ui.components.heroEnter()
                } else {
                    val forward = isForward(initialState, targetState, tabOrder)
                    slideInHorizontally(animationSpec = tween(durationMillis = 300)) {
                        if (forward) it else -it
                    }
                }
            },
            exitTransition = {
                if (involvesDetail(initialState, targetState)) {
                    app.missu.ui.components.heroExit()
                } else {
                    val forward = isForward(initialState, targetState, tabOrder)
                    slideOutHorizontally(animationSpec = tween(durationMillis = 300)) {
                        if (forward) -it else it
                    }
                }
            },
            popEnterTransition = { missuPopEnter() },
            popExitTransition = { missuPopExit(button = initialState.id == buttonPopId) },
            predictivePopEnterTransition = { missuPopEnter() },
            predictivePopExitTransition = { missuPopExit(button = false) },
        ) {
            composable(TABS_ROUTE) { entry ->
                TabsHost(
                    entry = entry,
                    navScope = this,
                    backStackEntry = backStackEntry.value,
                    navController = navController,
                    tabRoute = tabRoute,
                    onTabChange = { tabRoute = it },
                    api = api,
                    literatureRepository = literatureRepository,
                    searchHistory = searchHistory,
                    listSortStore = listSortStore,
                    listQueryStore = listQueryStore,
                    literatureStore = literatureStore,
                    auth = auth,
                    onOpenDetail = ::openDetail,
                    onOpenList = ::openList,
                )
            }
            secondaryDestinations(
                navController = navController,
                backStackEntry = backStackEntry,
                tabRoute = tabRouteState,
                api = api,
                literatureRepository = literatureRepository,
                settings = settings,
                settingsState = settingsState,
                settingsActions = settingsActions,
                historyStore = historyStore,
                listHistoryStore = listHistoryStore,
                searchHistory = searchHistory,
                listSortStore = listSortStore,
                listQueryStore = listQueryStore,
                literatureStore = literatureStore,
                auth = auth,
                onGoBack = ::goBack,
                onOpenDetail = ::openDetail,
                onOpenList = ::openList,
                onOpenListQuery = ::openListQuery,
            )
        }
    }
}
