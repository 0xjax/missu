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
    /** 一级页（三个页签）永远不会被缩成卡片，关掉这一层，别在上面留下痕迹。 */
    card: Boolean = true,

    content: @Composable () -> Unit,
) {
    var shrink by remember { mutableStateOf(1f) }
    val carded = card && shrink < 0.999f
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
                    // 只有**等比**缩小才算「被缩放成卡片」（返回预览）。横滑时页面只是平移，
                    // 被窗口裁掉的一边会让宽高比不同，这一帧不能画圆角/投影，否则切页签时
                    // 会在页头下方闪出一条卡片阴影。
                    shrink = if (kotlin.math.abs(w - h) < 0.02f) minOf(w, h) else 1f
                }
            }
            .graphicsLayer {
                this.shape = shape
                clip = corner > 0.dp

            }
            // 底色**常驻**：页面在返回预览里必须是不透明的，否则下层会从它的空白处透上来
            // （手势刚起手的瞬间最明显）。之前那条「页头下方接缝」的真凶是卡片态的投影，
            // 投影已经删掉了，底色可以放心地一直铺。
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
