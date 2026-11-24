package app.missu

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import app.missu.ui.components.heroEnter
import app.missu.ui.components.heroExit

internal fun isForward(
    initial: NavBackStackEntry,
    target: NavBackStackEntry,
    tabOrder: List<String>,
): Boolean {
    val from = tabOrder.indexOf(initial.destination.route)
    val to = tabOrder.indexOf(target.destination.route)
    return if (from >= 0 && to >= 0) to > from else true
}

internal fun involvesDetail(initial: NavBackStackEntry, target: NavBackStackEntry): Boolean =
    initial.destination.route?.startsWith("detail/") == true ||
        target.destination.route?.startsWith("detail/") == true

private object PopMotion {
    const val TOTAL_MS = 300
    const val EXIT_SCALE = 0.9f
    const val SLIDE_DELAY_MS = 180
    const val SLIDE_MS = 120
    const val BUTTON_MS = 260
}

/** 详情走共享容器；其余返回使用同一条可预测返回时间线。 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.missuPopEnter(): EnterTransition =
    if (involvesDetail(initialState, targetState)) heroEnter() else EnterTransition.None

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.missuPopExit(button: Boolean): ExitTransition =
    if (involvesDetail(initialState, targetState)) {
        heroExit()
    } else if (button) {
        slideOutHorizontally(animationSpec = tween(durationMillis = PopMotion.BUTTON_MS)) { it }
    } else {
        scaleOut(
            animationSpec = tween(durationMillis = PopMotion.TOTAL_MS),
            targetScale = PopMotion.EXIT_SCALE,
        ) + slideOutHorizontally(
            animationSpec = tween(
                durationMillis = PopMotion.SLIDE_MS,
                delayMillis = PopMotion.SLIDE_DELAY_MS,
            ),
        ) { it }
    }
