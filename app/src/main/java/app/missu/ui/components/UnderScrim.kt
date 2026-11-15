package app.missu.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/** 预测返回时下层页面压暗到的深度（纯黑 32%）。 */
private const val UNDER_DIM = 0.32f

/** 压暗层自己的淡入淡出时长；它不参与转场时长，动的是最后一层。 */
private const val UNDER_DIM_MS = 120

/**
 * 预测返回：把**下层页面**压暗一层（真的黑，不是页面透明度）。
 *
 * NOTE: 为什么不能靠「下层页面淡入」那招：那是改整页的 alpha，页面会朝背后的窗口底色
 * （同一个 `background`）混合——浅色主题下等于洗白，读不出「暗」；而且下层被退场卡片挡住九成，
 * 只剩四周一条边，12% 也好 30% 也好都看不出来。要的是**盖在上面的一层黑**。
 * NOTE: 这层必须画在下层页面里（退场页在它上面、由 NavHost 后画），所以它挂在页面自己的绘制上：
 * `drawWithContent { drawContent(); drawRect(black) }` 连底栏一起盖，不用把整页再包一层 Box。
 * NOTE: 只在「下层正被揭开」时存在。判断有三步：
 * 1. 转场方向——只有被揭开时目标状态才是 [EnterExitState.Visible]（被盖住时目标是 `PostExit`）；
 * 2. [covered]——我上面还压着另一个目的地（预测手势还没提交时它仍是栈顶，反之为 false）。
 *    少了第 2 步，松手落地之后 `currentState` 才变可见，那 200ms 里会补压一次再提亮（闪一下）；
 * 3. [fadingIn]——这一趟下层是**淡入**进来的（详情页的 container transform）。这种返回不能压暗：
 *    压暗层画在页面里，跟着它的 alpha 走，页面大半透明时看着亮、越不透明越黑，读起来就是
 *    「先亮后暗」地抖一下。缩放/平移那种揭示（列表、收藏、设置…）下层是实心露出来的，压暗才成立。
 */
@Composable
fun Modifier.underScrim(
    scope: AnimatedVisibilityScope,
    covered: Boolean,
    fadingIn: Boolean,
): Modifier {
    val transition = scope.transition
    val revealing = covered && !fadingIn &&
        transition.targetState == EnterExitState.Visible &&
        transition.currentState != EnterExitState.Visible
    val dim by animateFloatAsState(
        targetValue = if (revealing) UNDER_DIM else 0f,
        animationSpec = tween(durationMillis = UNDER_DIM_MS),
        label = "underScrim",
    )
    if (dim <= 0.001f) return this
    return drawWithContent {
        drawContent()
        drawRect(Color.Black, alpha = dim)
    }
}
