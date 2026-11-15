package app.missu.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 全屏效果：横屏 + 隐藏系统栏。退出时（或组件销毁时）恢复竖屏与系统栏。
 *
 * NOTE: 用 `SENSOR_LANDSCAPE` 而不是固定 `LANDSCAPE`，这样用户握住手机的另一侧也能看。
 * WARNING: 必须在 onDispose 里恢复，否则用户返回列表后整个 App 会继续横屏。
 * CRITICAL: Activity 只能从 `LocalView.context` 找，**不能**用 `LocalContext`：界面语言覆写把
 * `LocalContext` 换成了 `createConfigurationContext` 的产物（见 MainActivity 的
 * `localizedContext`），它既不是 Activity，也没有一条 ContextWrapper 链能穿回 Activity。
 * 走 `LocalContext` 时 `findActivity()` 返回 null，整段全屏就成了空操作——画面靠
 * `fillMaxSize` 铺满竖屏，`requestedOrientation` 和 `hide(systemBars)` 一次都没发出去
 * （表现就是「全屏了，但还是竖屏」）。
 * NOTE: 退出用 `UNSPECIFIED` 而不是写死横竖：MainActivity 在清单里锁了 portrait，回到
 * `UNSPECIFIED` 就是回到清单声明，全屏用完不会把横屏留在 App 里。
 */
@Composable
fun FullscreenEffect(enabled: Boolean) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() }

    DisposableEffect(enabled, activity) {
        if (activity == null) return@DisposableEffect onDispose { }

        val controller = WindowCompat.getInsetsController(activity.window, view)
        applyFullscreen(activity, controller, enabled)

        onDispose {
            // 离开详情页：把方向**还给系统**（清单里没有锁方向，这一步就恢复了用户自己的旋转设置）。
            applyFullscreen(activity, controller, enabled = false, restoreSystem = true)
        }
    }
}

private fun applyFullscreen(
    activity: Activity,
    controller: WindowInsetsControllerCompat,
    enabled: Boolean,
    restoreSystem: Boolean = false,
) {
    if (enabled) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        // NOTE: 退出全屏先回**竖屏**，不是 `UNSPECIFIED`：详情页是按竖屏排的，而退出那一刻手机往往
        // 还横着（用户开着自动旋转），交回系统的话整页会摊在横屏里、正文直接被挤到屏幕外（实机可见）。
        // NOTE: 这**不是**清单里的方向锁——真正把方向还给系统的是离开详情页时的 [restoreSystem]。
        activity.requestedOrientation = if (restoreSystem) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
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
