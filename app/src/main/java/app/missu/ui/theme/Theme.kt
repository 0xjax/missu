package app.missu.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.missu.data.store.ColorSource

/**
 * 按色源解析当前生效的色板：壁纸取色问系统，其余四套用 [staticColorScheme]。
 *
 * NOTE: 壁纸取色只有 Android 12+ 有。低版本上设置页不显示那块色块，但偏好里可能留着
 * [ColorSource.WALLPAPER]（比如从支持的系统换机过来），这里回落到品牌色板。
 */
internal fun resolveColorScheme(
    context: Context,
    darkTheme: Boolean,
    colorSource: ColorSource,
): ColorScheme {
    if (colorSource == ColorSource.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return colorSource.staticColorScheme(darkTheme)
}

/** 页面底色。窗口底色必须跟着它走，见 [WindowBackground]。 */
fun themeBackgroundColor(context: Context, darkTheme: Boolean, colorSource: ColorSource): Color =
    resolveColorScheme(context, darkTheme, colorSource).background

/**
 * 把窗口底色同步成当前主题的页面底色。
 *
 * NOTE: `res/values/themes.xml` 里把 `windowBackground` 写死成 `#000000`（当时只有深色）。
 * 一旦切到浅色（页面底 `#FEFBFC`），**任何透明帧都会露出纯黑**：页面转场的淡入淡出、切换瞬间的
 * 首帧组合、甚至回到前台的重绘。实测把转场拉长到 700ms 抓帧，画面平均亮度从 232 掉到 183，
 * 就是「闪一下、亮度不一致」的来源。窗口底色与页面底色一致后，透明帧看到的仍是同一个颜色。
 */
@Composable
private fun WindowBackground(color: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    SideEffect { window.setBackgroundDrawable(ColorDrawable(color.toArgb())) }
}

/**
 * 全局主题。
 *
 * NOTE: 用 [MaterialExpressiveTheme] 而不是 [androidx.compose.material3.MaterialTheme]——
 * 它多的是把 [MotionScheme.expressive] 装进 CompositionLocal，让官方组件的
 * 展开/按压/过渡走 M3E 的 spatial 动效曲线；颜色仍由色源决定的 colorScheme 决定。
 * NOTE: 五套色板同一套 tone 阶梯、只差色相（见 Palettes.kt），所以换色源不会改变任何对比度，
 * 也不会改变容器台阶；壁纸取色是个例外——整套色板由系统生成，容器色相会跟着壁纸走。
 * WARNING: 换主题会改变所有官方组件的默认形状与动效，改完必须逐页真机复查（浅色方案尤其）。
 */
@Composable
fun MissuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSource: ColorSource = ColorSource.BRAND,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val resolved = resolveColorScheme(context, darkTheme, colorSource)
    val colorScheme = if (darkTheme && pureBlack) resolved.withPureBlack() else resolved

    SystemBarAppearance(darkTheme)
    WindowBackground(colorScheme.background)

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
