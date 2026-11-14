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
 * NOTE: 色板由品牌粉按 M3 的 tone（`L*`）体系推导（`scripts/gen-theme.py`）：固定色相，按角色取音调，
 * 超 sRGB 色域时降饱和。中性表面用**色度 2**——留一丝来源色相但基本是灰，观感与官方基线
 * （深色 surface `#141218`，通道差 6）同一量级；色度取 4 时会明显偏粉，是试过才知道的坑。
 *
 * NOTE: `primary` 直接钉品牌色本身，而不是配方里的 tone 80——品牌粉是这款 App 唯一的高强调色。
 * 代价是白字压 `#D85A76` 只有 **3.7:1**：达到 UI 元素的 3:1 门槛（FAB 图标、选中态、进度条都够），
 * 但**不够正文级 4.5:1**，所以这个色只用于图形与大字号。浅色方案用 tone 40 的深一档品牌粉，白字 6.5:1。
 *
 * NOTE: 深色底取 neutral **tone 6**（`#171213`），不是纯黑：OLED 上黑底与亮像素相邻会拖影，纯黑也
 * 留不出「更亮的容器」这一档。想省电的人可在设置里切「纯黑背景」。
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
    background = Color(0xFF171213),
    onBackground = Color(0xFFE7E1E2),
    surface = Color(0xFF171213),
    onSurface = Color(0xFFE7E1E2),
    surfaceVariant = Color(0xFF4F4445),
    onSurfaceVariant = Color(0xFFD1C3C5),
    outline = Color(0xFF9A8E8F),
    outlineVariant = Color(0xFF4F4445),
    inverseSurface = Color(0xFFE7E1E2),
    inverseOnSurface = Color(0xFF342F30),
    inversePrimary = Color(0xFFAA2F51),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3C3839),
    surfaceDim = Color(0xFF171213),
    // NOTE: 五档容器色必须显式给。只给 surface 时官方从它推导的容器色会和底色几乎重合，
    // 「分组容器 / 卡片」这类 M3E 布局会把层级丢掉（只剩分隔线）。
    surfaceContainerLowest = Color(0xFF120D0D),
    surfaceContainerLow = Color(0xFF1E1B1B),
    surfaceContainer = Color(0xFF221F1F),
    surfaceContainerHigh = Color(0xFF2D2929),
    surfaceContainerHighest = Color(0xFF383434),
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
    onBackground = Color(0xFF1E1B1B),
    surface = Color(0xFFFEFBFC),
    onSurface = Color(0xFF1E1B1B),
    surfaceVariant = Color(0xFFEDDFE1),
    onSurfaceVariant = Color(0xFF4F4445),
    outline = Color(0xFF807476),
    outlineVariant = Color(0xFFD1C3C5),
    inverseSurface = Color(0xFF342F30),
    inverseOnSurface = Color(0xFFF5EFF0),
    inversePrimary = Color(0xFFFEB2BD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFEF8F9),
    surfaceDim = Color(0xFFDED9D9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F2F3),
    surfaceContainer = Color(0xFFF2ECED),
    surfaceContainerHigh = Color(0xFFECE7E7),
    surfaceContainerHighest = Color(0xFFE7E1E2),
)

/**
 * 深色 +「纯黑背景」。
 *
 * NOTE: 只换表面那几档，强调色不动。纯黑上容器梯度整体下移两档，否则容器和底色差太小。
 * 这是设置里的显式选择；默认走 [MissuDarkColors]（tone 6 底），符合 M3 的深色规范。
 */
private val MissuDarkColorsPureBlack = MissuDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceDim = Color(0xFF0D0606),
    surfaceContainerLowest = Color(0xFF0D0606),
    surfaceContainerLow = Color(0xFF171213),
    surfaceContainer = Color(0xFF1E1B1B),
    surfaceContainerHigh = Color(0xFF292525),
    surfaceContainerHighest = Color(0xFF342F30),
)

/**
 * 全局主题。
 *
 * NOTE: 用 [MaterialExpressiveTheme] 而不是 [androidx.compose.material3.MaterialTheme]——
 * 它多的是把 [MotionScheme.expressive] 装进 CompositionLocal，让官方组件的
 * 展开/按压/过渡走 M3E 的 spatial 动效曲线；颜色仍由上面的 colorScheme 决定。
 * NOTE: [dynamicColor] 打开时用系统壁纸取色（Android 12+），品牌粉会被系统色板取代——
 * 这是设置项、默认关，因为这款 App 的识别度就在那个粉色上。低版本自动回落品牌色板。
 * WARNING: 换主题会改变所有官方组件的默认形状与动效，改完必须逐页真机复查（浅色方案尤其）。
 */
@Composable
fun MissuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme && pureBlack -> MissuDarkColorsPureBlack
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
