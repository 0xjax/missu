package app.missu.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 应用主色：与 Expo 版一致的 accent，仅用于选中与强调。 */
val Accent = Color(0xFFD85A76)
val Background = Color(0xFF000000)
val SurfaceVariant = Color(0xFF212225)

private val MissuDarkColors = darkColorScheme(
    primary = Accent,
    // NOTE: 不显式给 onPrimary 时 Material 会从粉色推导出深色文字，压在 accent 上对比不足；按钮统一白字。
    onPrimary = Color.White,
    background = Background,
    surface = Background,
    surfaceVariant = SurfaceVariant,
)

/**
 * 全局主题：深色是事实上的主体验（夜间使用为主）。
 *
 * NOTE: 用 [MaterialExpressiveTheme] 而不是 [androidx.compose.material3.MaterialTheme]——
 * 它多的是把 [MotionScheme.expressive] 装进 CompositionLocal，让官方组件的
 * 展开/按压/过渡走 M3E 的 spatial 动效曲线；颜色仍由下面的 colorScheme 决定。
 * WARNING: 换主题会改变所有官方组件的默认形状与动效，改完必须逐页真机复查。
 */
@Composable
fun MissuTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = MissuDarkColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
