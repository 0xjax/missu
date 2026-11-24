@file:OptIn(ExperimentalSharedTransitionApi::class)

package app.missu.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * 卡片 → 详情页的 **container transform**（封面长成播放器）。
 *
 * 这是 Material 动效系统里「有持续存在的容器」那一类：卡片封面和详情页顶部的播放器框
 * 是同一个东西的两种尺寸，所以不能横滑，也不能纯淡入——只能让那一个容器自己长大。
 *
 * 页面本身用 **fade through** 交接（不是交叉淡入）：旧页 90ms 淡出、新页延迟 90ms 用
 * 210ms 淡入，两者不重叠。两页底色是同一个 `background` 色，所以中间那 90ms 的空档看到
 * 的仍是页面底色，不会闪；如果换成 700ms 的并发淡入就会重影。
 *
 * NOTE: 共享元素动画的时长必须与导航转场**完全一致**。导航转场一结束，退场的目的地就被
 * 从组合里移除，共享元素也失去另一侧——那时动画还在跑就会被截断、直接跳到终点。
 */
object HeroMotion {
    const val FADE_OUT_MS = 90
    const val FADE_IN_MS = 210
    const val TOTAL_MS = FADE_OUT_MS + FADE_IN_MS
}

/**
 * 共享元素的跨页面作用域。
 *
 * NOTE: 这两个作用域来自两处不同的组合——[SharedTransitionScope] 包在 NavHost 外层，
 * `AnimatedVisibilityScope` 每个目的地各有一个（`composable { }` 的 receiver 就是它）。
 * 用 CompositionLocal 传下去，避免把两个参数一路加到每个页面的签名里。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 共享元素的**命名空间**：卡片只和「自己这个来源目的地」配对。
 *
 * NOTE: 共享元素是按 key 配对的，而首页预览、完整列表、收藏、历史里都有同一批影片卡片。
 * 不分区的话，**首页 ↔ 完整页**之间也会互相配对——转场时那几张两边都有的封面会自己飞，
 * 其余卡片跟着页面横滑，出现「只有前 6 个在动」的参差效果（实测）。
 * 详情页会用「来源目的地的路由名」当命名空间，所以它只和真正被点的那张卡片配对。
 */
val LocalHeroNamespace = compositionLocalOf { "app" }

/**
 * 卡片的共享 key：页码内可能重复出现的影片，靠 [tag] 区分是哪一张卡片。
 *
 * NOTE: 首页是多个板块拼成的一页，同一部影片完全可能同时出现在两个板块里（站点自己的数据就是
 * 这样）。共享元素只认 key，同一页里两张卡片算出同一个 key 时，配对会落到**另一张**上——
 * 点 B 板块那张，封面却从 A 板块那张（可能已经滚出屏幕）飞过来，或者飞向屏外。
 * 普通列表一页里 id 不会重复，那里 [tag] 传空串即可。
 *
 * @param tag 卡片来源（首页用板块 id）；详情页从导航参数原样取回，两侧算出同一个 key
 */
fun heroKeyFor(tag: String, videoId: String): String =
    if (tag.isEmpty()) videoId else "$tag/$videoId"

/** 详情页淡入：延迟到旧页淡出之后才开始，避免两层内容叠在一起。 */
fun heroEnter(): EnterTransition =
    fadeIn(tween(durationMillis = HeroMotion.FADE_IN_MS, delayMillis = HeroMotion.FADE_OUT_MS))

/** 旧页淡出：只占整段交接的前 90ms。 */
fun heroExit(): ExitTransition = fadeOut(tween(durationMillis = HeroMotion.FADE_OUT_MS))

/**
 * 把当前目的地的可见性作用域交给下面的卡片。
 *
 * NOTE: `composable("detail/{id}") { }` 的 receiver 本身就是这个作用域，包一层只是为了让
 * 页面内容不必知道转场的存在——卡片和详情页只认「id 相同的那个容器」。
 * NOTE: [namespace] 用来把配对范围限定在「同一个来源目的地」内（见 [LocalHeroNamespace]）。
 */
@Composable
fun ProvideHeroVisibility(
    scope: AnimatedVisibilityScope,
    namespace: String = "app",
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalNavAnimatedVisibilityScope provides scope,
        LocalHeroNamespace provides namespace,
        content = content,
    )
}

/**
 * 把一个元素登记成「封面容器」：卡片封面与详情页的静态封面层用同一个 key。
 * 这个修饰符只能挂在静态封面层上，不能挂到播放器或整个 `DetailHero`：否则返回会先缩小视频，
 * 再交接给另一层封面，一个空间动作就变成两段。
 *
 * NOTE: 这里给的是 `tween` 而不是 M3E 的空间 spring。容器转场的动画不能比导航转场活得久，
 * 否则退场页被移除时会被硬切成终点；spring 的时长不确定，只能锁定成与转场同长。
 *
 * @param key 影片 id，两侧必须一致（卡片用 `video.id`，详情页用路由参数）
 * @param shape 覆盖层的裁剪形状——卡片是圆角，播放器框是直角
 */
@Composable
fun Modifier.videoHero(key: String, shape: Shape): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    val namespace = LocalHeroNamespace.current
    if (key.isEmpty()) return this
    return with(shared) {
        this@videoHero.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "hero-video-$namespace-$key"),
            animatedVisibilityScope = visibility,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = { _, _ -> tween(durationMillis = HeroMotion.TOTAL_MS) },
            // NOTE: 覆盖层默认按父布局裁剪（ParentClip）——卡片那一格会把长大的封面切掉，
            // 所以显式给一个形状：卡片是圆角，播放器框是直角。
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
