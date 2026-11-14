package app.missu.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 品牌色。整套色板都由它推导，见下方「色板怎么来的」。 */
val BrandPink = Color(0xFFD85A76)

/**
 * 深色色板（主体验）。
 *
 * NOTE: `primary` 直接钉品牌色本身，而不是 M3 配方里的 tone 80——品牌粉是这款 App 唯一的高强调色，
 * 换成浅粉（#FEB2BD）整机看起来就不是它了。代价是白字压 `#D85A76` 只有 **3.7:1**：达到 UI 元素
 * 的 3:1 门槛（FAB 图标、选中态、进度条都够），但**不够正文级 4.5:1**，所以这个色只用于图形与
 * 大字号，不要拿它做小字正文的底色。浅色方案用 tone 40 的深一档品牌粉，白字 6.5:1。
 *
 * NOTE: 其余角色（secondary/tertiary/error/surface 五档容器/outline/inverse*）全部由品牌色按
 * M3 的 tone 体系推导：固定色相，按角色取 L*，超色域时降饱和度。推导脚本跑出来的每一对
 * on/容器 对比度都 ≥ 5.4:1（浅色最低 5.49，深色最低 5.49）。
 */
private val MissuDarkColors = darkColorScheme(
    primary = BrandPink,
    // NOTE: 不显式给 onPrimary 时 Material 会从粉色推导出深色文字，和粉色底对比更差；统一白字。
    onPrimary = Color.White,
    primaryContainer = Color(0xFF8D0B3A),
    onPrimaryContainer = Color(0xFFFED9DE),
    secondary = Color(0xFFE6BCC1),
    onSecondary = Color(0xFF47272D),
    secondaryContainer = Color(0xFF5F3D43),
    onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Color(0xFFE9BF96),
    onTertiary = Color(0xFF462A06),
    tertiaryContainer = Color(0xFF5F401D),
    onTertiaryContainer = Color(0xFFFFDCBC),
    error = Color(0xFFFFB4AD),
    onError = Color(0xFF680113),
    errorContainer = Color(0xFF92031E),
    onErrorContainer = Color(0xFFFEDAD7),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEBE0E1),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEBE0E1),
    surfaceVariant = Color(0xFF544245),
    onSurfaceVariant = Color(0xFFD7C1C4),
    outline = Color(0xFFA08C8E),
    outlineVariant = Color(0xFF544245),
    inverseSurface = Color(0xFFEBE0E1),
    inverseOnSurface = Color(0xFF372E2F),
    inversePrimary = Color(0xFFAA2F51),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF403738),
    surfaceDim = Color(0xFF191112),
    // NOTE: 五档容器色必须显式给。把 surface 设成纯黑后，官方从 surface 推导的容器色会塌到黑色，
    // 「分组容器 / 卡片」这类 M3E 布局会整个消失（只剩分隔线）。这里按 M3 的 N 系列音调给出梯度。
    surfaceContainerLowest = Color(0xFF160B0D),
    surfaceContainerLow = Color(0xFF1D1617),
    surfaceContainer = Color(0xFF251E1F),
    surfaceContainerHigh = Color(0xFF302829),
    surfaceContainerHighest = Color(0xFF3B3334),
)

/** 浅色色板：同一色相、按 M3 的 tone 40/90 取，白字压在 primary 上 6.5:1。 */
private val MissuLightColors = lightColorScheme(
    primary = Color(0xFFAA2F51),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFED9DE),
    onPrimaryContainer = Color(0xFF3F0115),
    secondary = Color(0xFF79555A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DE),
    onSecondaryContainer = Color(0xFF301218),
    tertiary = Color(0xFF795833),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBC),
    onTertiaryContainer = Color(0xFF281801),
    error = Color(0xFFB02C34),
    onError = Color.White,
    errorContainer = Color(0xFFFEDAD7),
    onErrorContainer = Color(0xFF410100),
    background = Color(0xFFFEFBFC),
    onBackground = Color(0xFF211A1B),
    surface = Color(0xFFFEFBFC),
    onSurface = Color(0xFF211A1B),
    surfaceVariant = Color(0xFFF3DDE0),
    onSurfaceVariant = Color(0xFF544245),
    outline = Color(0xFF857275),
    outlineVariant = Color(0xFFD7C1C4),
    inverseSurface = Color(0xFF372E2F),
    inverseOnSurface = Color(0xFFF9EEEF),
    inversePrimary = Color(0xFFFEB2BD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F8),
    surfaceDim = Color(0xFFE2D7D9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF1F2),
    surfaceContainer = Color(0xFFF6EBEC),
    surfaceContainerHigh = Color(0xFFF1E6E7),
    surfaceContainerHighest = Color(0xFFEBE0E1),
)

/**
 * 全局主题。
 *
 * NOTE: 用 [MaterialExpressiveTheme] 而不是 [androidx.compose.material3.MaterialTheme]——
 * 它多的是把 [MotionScheme.expressive] 装进 CompositionLocal，让官方组件的
 * 展开/按压/过渡走 M3E 的 spatial 动效曲线；颜色仍由下面的 colorScheme 决定。
 * NOTE: [dynamicColor] 打开时用系统壁纸取色（Android 12+），品牌粉会被系统色板取代——
 * 这是设置项、默认关，因为这款 App 的识别度就在那个粉色上。低版本自动回落品牌色板。
 * WARNING: 换主题会改变所有官方组件的默认形状与动效，改完必须逐页真机复查（浅色方案尤其）。
 */
@Composable
fun MissuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> MissuDarkColors
        else -> MissuLightColors
    }

    SystemBarAppearance(darkTheme)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * 状态栏/导航栏图标明暗。
 *
 * NOTE: `enableEdgeToEdge()` 只在 onCreate 跑一次，跟着系统主题走；一旦用户在设置里强制浅色而系统是
 * 深色，系统就会画出「浅色底 + 白色图标」这种看不见的组合。所以这里按实际生效的主题再设一次。
 */
@Composable
private fun SystemBarAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
