package app.missu.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/** 手势返回时下层页面压暗到的深度（纯黑 32%）。 */
private const val UNDER_DIM = 0.32f

private const val UNDER_DIM_MS = 300
private const val UNDER_DIM_HOLD_MS = 180

/**
 * 返回揭示：把**下层页面**压暗一层（真的黑，不是页面透明度）。缩放阶段保持深度，
 * 只在卡片侧向退出阶段跟随页面转场进度同步提亮。
 *
 * NOTE: 为什么不能靠「下层页面淡入」那招：那是改整页的 alpha，页面会朝背后的窗口底色
 * （同一个 `background`）混合——浅色主题下等于洗白，读不出「暗」；而且下层被退场卡片挡住九成，
 * 只剩四周一条边，12% 也好 30% 也好都看不出来。要的是**盖在上面的一层黑**。
 * NOTE: 这层必须画在下层页面里（退场页在它上面、由 NavHost 后画），所以它挂在页面自己的绘制上：
 * `drawWithContent { drawContent(); drawRect(black) }` 连底栏一起盖，不用把整页再包一层 Box。
 * NOTE: 只在「下层正被揭开」时存在。判断分两步：
 * 1. 转场方向——只有被揭开时目标状态才是 [EnterExitState.Visible]（被盖住时目标是 `PostExit`）。
 *    不能再用导航栈顶判定：快速返回会立即提交栈，但此时揭示动画才刚开始，压暗必须保留到落地。
 * 2. [button]——按钮返回只做横滑，不压暗；[fadingIn] 则排除详情页的 container transform。
 *    详情下层跟着 alpha 走，压暗会读成「先亮后暗」地抖一下；手势缩放揭示是实心露出来的，压暗才成立。
 */
@Composable
fun Modifier.underScrim(
    scope: AnimatedVisibilityScope,
    button: Boolean,
    fadingIn: Boolean,
): Modifier {
    val transition = scope.transition
    val buttonAtStart = remember(scope) { button }
    val fadingInAtStart = remember(scope) { fadingIn }
    val revealing = !buttonAtStart && !fadingInAtStart &&
        transition.targetState == EnterExitState.Visible &&
        transition.currentState != EnterExitState.Visible
    val dim by transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = UNDER_DIM_MS
                UNDER_DIM at 0
                UNDER_DIM at UNDER_DIM_HOLD_MS
                0f at UNDER_DIM_MS
            }
        },
        label = "underScrim",
        targetValueByState = { state -> if (state == EnterExitState.Visible) 0f else UNDER_DIM },
    )
    if (!revealing || dim <= 0.001f) return this
    return drawWithContent {
        drawContent()
        drawRect(Color.Black, alpha = dim)
    }
}
