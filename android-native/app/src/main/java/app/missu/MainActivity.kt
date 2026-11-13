package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.missu.data.api.MissavApi
import app.missu.data.api.createMissavApi
import app.missu.data.store.CredentialStore
import app.missu.data.store.ProfileStore
import app.missu.ui.account.AccountScreen
import app.missu.ui.account.AuthViewModel
import app.missu.ui.account.LoginScreen
import app.missu.ui.detail.DetailScreen
import app.missu.ui.detail.DetailViewModel
import app.missu.ui.home.HomeScreen
import app.missu.ui.home.HomeViewModel
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
    val authState by auth.state.collectAsState()
    val loginState by auth.loginState.collectAsState()

    // NOTE: 系统栏、刘海、输入法的内边距只在这里处理一次，各页面只负责内容布局。
    // 需要贴边显示的页面（将来的全屏播放）自己在 destination 里 opt-out，不要在页面里重复加 padding。
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                val viewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(api) })
                val state by viewModel.state.collectAsState()
                LaunchedEffect(Unit) { viewModel.load() }

                HomeScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onOpen = { id -> navController.navigate("detail/$id") },
                    onSearch = { navController.navigate("search") },
                    onAccount = { navController.navigate("account") },
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
                    onLogout = auth::logout,
                    onBack = { navController.popBackStack() },
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
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                // NOTE: key 用 id —— 换一集详情时复用同一个 ViewModel 会显示上一条数据。
                val viewModel: DetailViewModel = viewModel(
                    key = "detail-$id",
                    factory = factory { DetailViewModel(api, id) },
                )
                val state by viewModel.state.collectAsState()
                LaunchedEffect(id) { viewModel.load() }

                DetailScreen(
                    state = state,
                    onRetry = viewModel::load,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
