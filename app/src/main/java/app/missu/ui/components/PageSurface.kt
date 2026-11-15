package app.missu.ui.components

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
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 机器报不出屏幕圆角时的兜底（MD3 的 extraLarge）。 */
private val FallbackCorner = 24.dp

/**
 * 卡片态的圆角：取**这台机器自己的屏幕圆角**（`RoundedCorner`，API 31+）。
 *
 * NOTE: 不同机器读到的值本来就不一样（有的上报 0），这是预期——要的是「同一台机器上恒定，
 * 且缩小后的卡片和屏幕的圆角是同一档」，不是跨机型统一。读不到就退回 [FallbackCorner]。
 * NOTE: 只在进入组合时读一次，避免每帧访问 window insets。
 */
@Composable
private fun deviceCardCorner(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view) {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        } else {
            0
        }
        if (px > 0) with(density) { px.toDp() } else FallbackCorner
    }
}

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
    // 圆角**不做过渡**：页面一开始缩小就是最终圆角，整个拖动过程里它不变。
    // （之前用 spring 从 0 长到 24dp，手一推圆角还在长，看起来就是「先尖后钝」。）
    val corner = if (carded) deviceCardCorner() else 0.dp
    val shape = RoundedCornerShape(corner)

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val inWindow = coords.boundsInWindow()
                if (coords.size.width > 0 && coords.size.height > 0) {
                    val w = inWindow.width / coords.size.width
                    val h = inWindow.height / coords.size.height
                    // 只有**等比**的测量才算数：等比缩小/放大才更新状态；非等比（横滑进出、
                    // 松手后向右滑出被窗口裁掉一边）一律沿用上一次的值——否则滑出的那一刻
                    // 圆角会突然消失，而它明明还是那张缩小中的卡片。
                    if (kotlin.math.abs(w - h) < 0.02f) shrink = minOf(w, h)
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
