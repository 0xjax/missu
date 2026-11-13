package app.missu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** 应用主色：与 Expo 版一致的 accent，仅用于选中与强调。 */
private val Accent = Color(0xFFD85A76)

private val MissuDarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF212225),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = MissuDarkColors) {
                Scaffold { innerPadding ->
                    Placeholder(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * 骨架阶段的占位屏：只验证工程能构建、Compose 能上屏。
 * TODO: 换成首页（getHome → 板块列表），见 android-native/README.md 的迁移顺序。
 */
@Composable
private fun Placeholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Missu · Kotlin 骨架", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "下一步：网络层 + Jsoup 解析 getHome",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
