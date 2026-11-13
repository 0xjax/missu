package app.missu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 应用主色：与 Expo 版一致的 accent，仅用于选中与强调。 */
val Accent = Color(0xFFD85A76)
val Background = Color(0xFF000000)
val SurfaceVariant = Color(0xFF212225)

private val MissuDarkColors = darkColorScheme(
    primary = Accent,
    background = Background,
    surface = Background,
    surfaceVariant = SurfaceVariant,
)

/** 全局主题：深色是事实上的主体验（夜间使用为主）。 */
@Composable
fun MissuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MissuDarkColors, content = content)
}
