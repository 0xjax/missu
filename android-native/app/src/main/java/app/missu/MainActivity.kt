package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import app.missu.ui.detail.DetailScreen
import app.missu.ui.detail.DetailViewModel
import app.missu.ui.home.HomeScreen
import app.missu.ui.home.HomeViewModel
import app.missu.ui.theme.MissuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MissuTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MissuNavHost()
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
private fun MissuNavHost() {
    val navController = rememberNavController()
    val api = rememberApi()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = viewModel(factory = factory { HomeViewModel(api) })
            val state by viewModel.state.collectAsState()
            LaunchedEffect(Unit) { viewModel.load() }

            HomeScreen(
                state = state,
                onRetry = viewModel::load,
                onOpen = { id -> navController.navigate("detail/$id") },
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

private inline fun <reified T : ViewModel> factory(crossinline create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
