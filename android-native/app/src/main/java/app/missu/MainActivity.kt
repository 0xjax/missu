package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
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
import app.missu.data.store.SettingsStore
import app.missu.ui.history.HistoryScreen
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
import app.missu.ui.saved.SavedScreen
import app.missu.ui.saved.SavedViewModel
import app.missu.ui.search.SearchScreen
import app.missu.ui.search.SearchViewModel
import app.missu.ui.theme.MissuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MissuTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MissuApp()
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
private fun MissuApp() {
    val navController = rememberNavController()
    val api = rememberApi()
    val context = LocalContext.current
    // NOTE: 账户状态整个 App 共用一份：'我的' 与 '登录' 两个页面必须看到同一份状态。
    val auth: AuthViewModel = viewModel(factory = factory { AuthViewModel(api, ProfileStore(context.applicationContext), CredentialStore(context.applicationContext)) })

    // NOTE: 语言必须在第一次请求之前装进 SourceConfig，否则首屏会按默认语言拉一轮再换。
    val settings = remember { SettingsStore(context.applicationContext).also { it.applyStoredLanguage() } }
    val historyStore = remember { HistoryStore(context.applicationContext) }
    var language by remember { mutableStateOf(settings.language()) }
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
                NavigationBar {
                    topLevels.forEach { item ->
                        NavigationBarItem(
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
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                val viewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(api) })
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                // 语言变了就重新拉首页（请求 URL 跟着语言变），其余页面下次进入时自然用新语言。
                LaunchedEffect(language) { viewModel.load() }

                HomeScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onSearch = { navController.navigate("search") },
                )
            }

            composable("discover") {
                val viewModel: DiscoverViewModel = viewModel(factory = factory { DiscoverViewModel(api) })
                val states by viewModel.states.collectAsState()
                val sort by viewModel.sort.collectAsState()
                val filters by viewModel.filters.collectAsState()

                DiscoverScreen(
                    states = states,
                    sort = sort,
                    filters = filters,
                    onLoad = viewModel::load,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { path, title ->
                        navController.navigate("list?p=${Uri.encode(path)}&t=${Uri.encode(title)}")
                    },
                    onSortChange = viewModel::setSort,
                    onFilterChange = viewModel::setFilter,
                    onResetFilters = viewModel::resetFilters,
                )
            }
            composable("search") {
                val viewModel: SearchViewModel = viewModel(factory = factory { SearchViewModel(api) })
                val state by viewModel.state.collectAsState()

                SearchScreen(
                    state = state,
                    onSearch = viewModel::search,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onBack = { navController.popBackStack() },
                )
            }

            composable("account") {
                AccountScreen(
                    state = authState,
                    onLogin = { navController.navigate("login") },
                    onSaved = { navController.navigate("saved") },
                    onHistory = { navController.navigate("history") },
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
                    onLanguageChange = { value ->
                        settings.setLanguage(value)
                        language = value
                    },
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

/** 底部导航的四个一级页面。 */
private data class TopLevel(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevels = listOf(
    TopLevel("home", "首页", Icons.Filled.Home),
    TopLevel("discover", "发现", Icons.Filled.List),
    TopLevel("search", "搜索", Icons.Filled.Search),
    TopLevel("account", "我的", Icons.Filled.Person),
)

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
