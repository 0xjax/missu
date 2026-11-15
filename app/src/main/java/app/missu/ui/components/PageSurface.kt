package app.missu.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/** 卡片态（返回预览里被缩小的那一页）的圆角。 */
private val CardCorner = 24.dp

/** 卡片态的投影。深色主题下几乎看不见，只负责把「浮起来的一页」和底下那页分开。 */
private val CardElevation = 12.dp

/**
 * 页面底材。两件事挂在**页面自身的窗口缩放比**上：
 *
 * 1. **常驻一层不透明底色**，并且铺满整屏（含状态栏下面那条）。页面自己不画底色的话，
 *    缩小时只有文字透下去、两页会糊成一页；铺满整屏则保证缩小时卡片把状态栏一起带上。
 * 2. 被缩小时（返回预览的上层）：切 24dp 圆角 + 投影。**不换底色、不加描边**——
 *    卡片还是那张页面，只是变小了，不该凭空多出线或变一个色。
 *
 * NOTE: 判断不用「这是不是一次 pop」——手势期间导航栈并没有真的 pop（`NavHost` 只是
 * `prepareTransition` + `seekTo`）。用几何事实：窗口坐标里**宽高同时**变小 = 被缩放；
 * 横滑的页面只是平移，被窗口裁掉的一边会让宽度变小但高度仍是满的。
 * NOTE: 顶部安全区让在这里（不是外层 Scaffold），页面底色才能铺到状态栏底下。
 */
@Composable
fun PageSurface(
    modifier: Modifier = Modifier,
    contentInset: Boolean = true,

    content: @Composable () -> Unit,
) {
    var shrink by remember { mutableStateOf(1f) }
    val carded = shrink < 0.999f
    val corner by animateDpAsState(
        targetValue = if (carded) CardCorner else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pageCardCorner",
    )
    val shape = RoundedCornerShape(corner)

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val inWindow = coords.boundsInWindow()
                if (coords.size.width > 0 && coords.size.height > 0) {
                    val w = inWindow.width / coords.size.width
                    val h = inWindow.height / coords.size.height
                    shrink = minOf(w, h)
                }
            }
            .graphicsLayer {
                this.shape = shape
                clip = corner > 0.dp
                shadowElevation = if (corner > 0.dp) CardElevation.toPx() else 0f

            }
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (contentInset) {
                        Modifier.windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}