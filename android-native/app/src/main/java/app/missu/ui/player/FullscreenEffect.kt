package app.missu.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 全屏效果：横屏 + 隐藏系统栏。退出时（或组件销毁时）恢复竖屏与系统栏。
 *
 * NOTE: 用 `SENSOR_LANDSCAPE` 而不是固定 `LANDSCAPE`，这样用户握住手机的另一侧也能看。
 * WARNING: 必须在 onDispose 里恢复，否则用户返回列表后整个 App 会继续横屏。
 */
@Composable
fun FullscreenEffect(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(enabled, activity) {
        if (activity == null) return@DisposableEffect onDispose { }

        val controller = WindowCompat.getInsetsController(activity.window, view)
        applyFullscreen(activity, controller, enabled)

        onDispose {
            applyFullscreen(activity, controller, false)
        }
    }
}

private fun applyFullscreen(
    activity: Activity,
    controller: WindowInsetsControllerCompat,
    enabled: Boolean,
) {
    if (enabled) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
