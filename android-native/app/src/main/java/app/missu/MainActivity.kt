package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.missu.data.api.createMissavApi
import app.missu.ui.theme.MissuTheme
import kotlinx.coroutines.launch

/**
 * 骨架阶段的验证屏：把「取首页 → 解析」这条链路接到真机上。
 * TODO: 换成真首页（两列封面网格），这个按钮届时删除。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MissuTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SmokeScreen()
                }
            }
        }
    }
}

private sealed interface Smoke {
    data object Idle : Smoke
    data object Loading : Smoke
    data class Ok(val lines: List<String>) : Smoke
    data class Fail(val message: String) : Smoke
}

@Composable
private fun SmokeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<Smoke>(Smoke.Idle) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Missu · Kotlin", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "数据层已就绪：点下面按钮抓一次首页",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))

        when (val current = state) {
            Smoke.Idle -> Unit
            Smoke.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("请求中…（若弹出验证页，请在里面完成）", style = MaterialTheme.typography.bodySmall)
            }
            is Smoke.Ok -> current.lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            is Smoke.Fail -> Text(
                current.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            state = Smoke.Loading
            scope.launch {
                state = try {
                    val sections = createMissavApi(context).getHome()
                    val total = sections.sumOf { it.videos.size }
                    Smoke.Ok(
                        listOf("板块 ${sections.size} 个 / 视频 $total 条") +
                            sections.map { "· ${it.title}：${it.videos.size} 条" },
                    )
                } catch (error: Exception) {
                    Smoke.Fail("${error::class.simpleName}: ${error.message}")
                }
            }
        }) {
            Text("取首页数据")
        }
    }
}
