package app.missu.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.missu.R
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 双击快进/快退的档位（秒）：与 legacy、以及播放器 `seekBack/ForwardIncrementMs` 一致。 */
const val SEEK_STEP_SECONDS = 10

/**
 * 播放器控件层：自己的三层布局（顶部胶囊 / 中间播放键 / 底部进度条），不借 media3 的
 * `PlayerDefaults` 布局。
 *
 * NOTE: 为什么不用它的布局：它那条底部带子有 98dp 高（时间行 + 进度条 + below 槽位），而 16:9 的
 * 画面在手机上只有 ~200dp 高——中间那排按钮必然压到进度条上（2026-11-15 实机：滑块从播放键底下
 * 穿过去）。这里用 `Column` + `weight(1f)` 把播放键放进「剩下的空间」里居中，结构性保证不重叠。
 * NOTE: 快进/快退没有按钮，改成**双击画面左右半边**（对齐 legacy）。
 * NOTE: 压在画面上的东西一律**黑底 + 白内容**，和倍速那颗 `OverlayChip` 同一个约定。
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerOverlay(
    player: Player,
    visible: Boolean,
    /** 播放态：由外层给（它带 seek 静默窗口，seek 期间不会闪成暂停图标）。 */
    isPlaying: Boolean,
    isFullscreen: Boolean,
    onSettingsClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** 双击 seek 的提示（带符号的秒数，如 -20 / +10）；为空表示不显示。 */
    seekHintSeconds: Int? = null,
) {
    // 进度与播放态：可见时每 200ms 取一次（不可见就不轮询）。media3 的 Player 不是 Compose 状态，
    // 不这样取的话 play/pause 图标和进度条都不会动。
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    // NOTE: 全屏时进度条必须离屏幕边缘留一点：圆球半径 6dp，条贴在物理边上时它下半截会被屏幕裁掉，
    // 两端位置（0% / 100%）也会被左右裁掉一半。内嵌时相反——球**故意**骑在视频下沿上，所以留 0。
    val barInset = if (isFullscreen) FullscreenBarInset else 0.dp
    LaunchedEffect(player, visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            delay(200)
        }
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 底部挡板：画面亮的时候白字、白图标也得读得出来（所有播放器都这么做）。
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(BottomScrimHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            ),
                        ),
                )

                // 顶部挡板 + 右上角的设置按钮：亮画面上白色图标也读得清。
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(TopScrimHeight)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                            ),
                        ),
                )

                Column(Modifier.fillMaxSize()) {
                    // 顶部右侧：设置（倍速等）。
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 设置也套一层圆底：和播放键、时间胶囊同一个悬浮语言（裸白图标在亮画面上不稳）。
                        RoundButton(size = 32.dp, onClick = onSettingsClick) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    // 中间：播放/暂停（快进快退是双击手势）。`weight(1f)` 让它只占「顶部与底部之间」
                    // 这段，无论画面多高都不会压到底部那一行。中间没有挡板，所以这里套一颗黑圆保证对比。
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        RoundButton(
                            size = 56.dp,
                            dim = 0.5f,
                            // CRITICAL: 点击判定必须按**播放器的意图**（`playWhenReady`），不能用
                            // 上面那个 `isPlaying` 状态——seek 静默窗口会屏蔽 paused 事件，状态可能
                            // 还是「在播」，于是点一下又发 pause()，永远起不来（2026-11-15 实测）。
                            // `isPlaying` 只用来决定图标。
                            onClick = { if (player.playWhenReady) player.pause() else player.play() },
                        ) {
                            if (isPlaying) {
                                Icon(
                                    imageVector = PauseIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp),
                                )
                            }
                        }
                    }

                    // 底部：左下角是「当前 / 总时长」胶囊，右下角是全屏。进度条**不在这行里**——
                    // 它贴在视频最底边、一直可见（见下面 `SeekBar`），这一行要给它留出位置。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, bottom = ProgressBarReservedHeight + barInset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimePill("${formatTime(positionMs)} / ${formatTime(durationMs)}")
                        Spacer(Modifier.weight(1f))
                        RoundButton(size = 32.dp, onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = if (isFullscreen) FullscreenExitIcon else FullscreenIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        // 进度条：**内嵌时永远可见**，贴在视频最底边、占满宽度——控件隐藏时只藏圆球（细线还在），
        // 所以看进度不需要唤出控件。**全屏时反过来**：它跟着控件一起淡出（用户实测要求：全屏藏控件
        // 就得连它一起藏），否则横屏上一条孤零零的线挂在画面下沿很脏。
        AnimatedVisibility(
            visible = !isFullscreen || visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                showThumb = visible,
                onSeek = { target -> player.seekTo(target) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = barInset, end = barInset, bottom = barInset),
            )
        }

        // 双击 seek 的提示：控件隐藏时也要看得见，所以放在 AnimatedVisibility 之外。
        val hint = seekHintSeconds
        if (hint != null) {
            Text(
                text = stringResource(
                    R.string.player_seek_hint,
                    if (hint > 0) "+" else "-",
                    kotlin.math.abs(hint),
                ),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 进度条：自己画的细条，**贴在视频最底边**、一直可见（YouTube 移动端那种）。
 *
 * NOTE: 控件隐藏时只藏圆球、细线照画——看进度不需要唤出控件，双击快进快退时条直接跳，
 * 观看过程不被打断。所以这条不在 `AnimatedVisibility` 里面。
 * NOTE: 拖动只在控件可见时接管；隐藏时这 20dp 条带不消费事件，点击照样落到下面的手势层。
 * NOTE: 已播部分与圆球都取**主题色**；未播部分是 30% 白，压在任意画面上都能看出剩余。
 * NOTE: 不用 M3 `Slider`——1.5 的拇指是一根竖条，播放位置 0 时就是左下角一根突兀的白杠。
 */
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    showThumb: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var widthPx by remember { mutableStateOf(1) }
    // NOTE: 松手后不能立刻切回「轮询到的位置」——播放器要过一会儿才报新位置，中间那一两帧
    // 会按旧位置算，看起来就是「进度条闪回一下再跳过去」。所以先挂一个待生效目标，等轮询到的
    // 位置真的接近它（或超时兜底）再交还给轮询。
    var pendingFraction by remember { mutableStateOf<Float?>(null) }
    val polledFraction = progressFraction(positionMs, durationMs)
    LaunchedEffect(pendingFraction, positionMs) {
        val pending = pendingFraction ?: return@LaunchedEffect
        if (durationMs > 0L && abs(positionMs - pending * durationMs) < PendingSeekToleranceMs) {
            pendingFraction = null
        }
    }
    LaunchedEffect(pendingFraction) {
        if (pendingFraction != null) {
            delay(PendingSeekTimeoutMs)
            pendingFraction = null
        }
    }
    val fraction = when {
        dragging -> dragFraction
        pendingFraction != null -> pendingFraction!!
        else -> polledFraction
    }
    val activeColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ProgressBarReservedHeight)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .then(
                if (showThumb) {
                    Modifier.pointerInput(durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                dragFraction = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                            },
                            onHorizontalDrag = { change, _ ->
                                dragFraction = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                val target = dragFraction
                                dragging = false
                                if (durationMs > 0L) {
                                    pendingFraction = target
                                    onSeek((target * durationMs).toLong())
                                }
                            },
                            onDragCancel = { dragging = false },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val trackHeight = 3.dp.toPx()
        // 线贴**自己的**下沿：内嵌时那就是视频最底边，**圆球故意跨出去**、下半部分压进页面里
        // （YouTube 移动端就是这样，球骑在视频与页面的分界上）。所以这里不裁剪、也不为了
        // "不越界"把球上移——全屏时屏幕边缘会裁掉它，那由调用方给整条留 inset（见 barInset）。
        val centerY = size.height - trackHeight / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.3f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        val playedX = size.width * fraction
        drawLine(
            color = activeColor,
            start = Offset(0f, centerY),
            end = Offset(playedX, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
        )
        if (showThumb) {
            drawCircle(color = activeColor, radius = 6.dp.toPx(), center = Offset(playedX, centerY))
        }
    }
}

/** 「当前 / 总时长」胶囊：黑底 40% + 白字，和播放/暂停那颗圆底是同一个悬浮语言。 */
@Composable
private fun TimePill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color(0x66000000))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

/** 时间标签：不足一小时只给分:秒，和播放器控件条的常见写法一致。 */
private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val mm = if (hours > 0L) minutes.toString().padStart(2, '0') else minutes.toString()
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0L) "$hours:$mm:$ss" else "$mm:$ss"
}

/**
 * 圆形按钮：黑底（[dim] 透明度）+ 白内容，压在任意画面上都能读。
 *
 * WARNING: 不能用 `IconButton` —— 它内部会强制一层 40dp 的容器（`IconButtonTokens.StateLayerSize`），
 * 传进去的 `.size(28.dp)` 被它覆盖，圆底永远渲染成 40dp（2026-11-15 量像素发现的）。
 * 所以这里自己画：外层 48dp 只负责触摸区（无 ripple，避免比圆底大一圈的水波纹），
 * 内层才是 **[size] 的可见圆底**，图标就贴着它居中。
 */
@Composable
private fun RoundButton(
    size: Dp,
    dim: Float = 0.42f,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (size > 48.dp) size else 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(Color.Black.copy(alpha = dim), CircleShape),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * 播放器图标：全部用 Material 官方的 **24dp 路径数据**建 `ImageVector`。
 *
 * NOTE: 之前这几个图标是拿 `Canvas` 按比例现画的（全屏的四段括号、设置的三个旋钮），
 * 没有落在 24dp 网格上、描边和拐角半径都是估的，缩到 20dp 就明显歪（2026-11-15 反馈）。
 * 这里换成官方 glyph 的路径，字形和 Material Symbols 一致；设置直接用 `material-icons-core`
 * 里已有的 `Icons.Filled.Settings`，不必自己画。
 * NOTE: 仍然不引 `material-icons-extended`——整套图标几 MB，为这几个字形不值得。
 */
private fun materialIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)
    }.build()

/** Material `fullscreen`。 */
private val FullscreenIcon: ImageVector = materialIcon("Fullscreen") {
    moveTo(7f, 14f); horizontalLineTo(5f); verticalLineToRelative(5f); horizontalLineToRelative(5f)
    verticalLineToRelative(-2f); horizontalLineTo(7f); verticalLineToRelative(-3f); close()
    moveTo(5f, 10f); horizontalLineToRelative(2f); verticalLineTo(7f); horizontalLineToRelative(3f)
    verticalLineTo(5f); horizontalLineTo(5f); verticalLineToRelative(5f); close()
    moveTo(17f, 17f); horizontalLineToRelative(-3f); verticalLineToRelative(2f); horizontalLineToRelative(5f)
    verticalLineToRelative(-5f); horizontalLineToRelative(-2f); verticalLineToRelative(3f); close()
    moveTo(14f, 5f); verticalLineToRelative(2f); horizontalLineToRelative(3f); verticalLineToRelative(3f)
    horizontalLineToRelative(2f); verticalLineTo(5f); horizontalLineToRelative(-5f); close()
}

/** Material `fullscreen_exit`（括号朝内）。 */
private val FullscreenExitIcon: ImageVector = materialIcon("FullscreenExit") {
    moveTo(5f, 16f); horizontalLineToRelative(3f); verticalLineToRelative(3f); horizontalLineToRelative(2f)
    verticalLineToRelative(-5f); horizontalLineTo(5f); verticalLineToRelative(2f); close()
    moveTo(8f, 8f); horizontalLineTo(5f); verticalLineToRelative(2f); horizontalLineToRelative(5f)
    verticalLineTo(5f); horizontalLineTo(8f); verticalLineToRelative(3f); close()
    moveTo(14f, 19f); horizontalLineToRelative(2f); verticalLineToRelative(-3f); horizontalLineToRelative(3f)
    verticalLineToRelative(-2f); horizontalLineToRelative(-5f); verticalLineToRelative(5f); close()
    moveTo(16f, 8f); verticalLineTo(5f); horizontalLineToRelative(-2f); verticalLineToRelative(5f)
    horizontalLineToRelative(5f); verticalLineTo(8f); horizontalLineToRelative(-3f); close()
}

/** Material `pause`。 */
private val PauseIcon: ImageVector = materialIcon("Pause") {
    moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
    moveToRelative(8f, -14f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
}

/**
 * 播放器设置面板：倍速收进这里当**一个设置项**（不再是一颗直接摆在画面上的倍速胶囊）。
 *
 * NOTE: 由播放器持有、不放进控件层——放在会被自动隐藏的控件树里会跟着一起被销毁
 * （实测「菜单一闪就关」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    speed: Float,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                // NOTE: 横屏只有 360dp 高，标题 + 六档倍速一定装不下；不给滚动就会被屏幕直接裁掉
                // （实机：只露得出 0.5x 一行，其余全在屏幕外）。
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.player_speed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
            )
            // 单选行（和设置页同一套：整行可点，行尾 RadioButton 不接点击）。
            SPEEDS.forEach { option ->
                val selected = option == speed
                ListItem(
                    modifier = Modifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSpeedChange(option) },
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    trailingContent = { RadioButton(selected = selected, onClick = null) },
                ) {
                    Text(text = formatSpeed(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * 全屏时的设置菜单：**贴着设置键展开的悬浮面板**（YouTube 移动端那颗齿轮的形态），
 * 不再是底部抽屉。
 *
 * NOTE: 全屏**不能**用 `ModalBottomSheet`。它是独立窗口：一打开系统栏就跟着那个窗口重新可见
 * （状态栏冒出来，页面被重新 inset、画面整块缩下去）；而且横屏只有 360dp 高，抽屉只能盖住半条
 * 画面。菜单画在播放器自己的组合里，窗口状态栏一点没动。
 * NOTE: 面板画在控件层**之外**、播放器**之内**：控件 3 秒自动隐藏时它还得在（见调用方）。
 */
@Composable
fun PlayerSettingsMenu(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(FullscreenMenuWidth)
            // 面板上的点击不要穿到画面上去（穿过去就会把控件切一次）。
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(20.dp),
        // NOTE: 和播放器所有悬浮件同一套语言：黑底 + 白内容（画面亮的时候也读得清）。
        color = Color.Black.copy(alpha = 0.78f),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.player_speed),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            SPEEDS.forEach { option ->
                val selected = option == speed
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSpeedChange(option) },
                        )
                        .padding(horizontal = 12.dp)
                        .height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatSpeed(option),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 底部挡板高度：正好覆盖进度条那一行，不往画面中间糊。 */
private val BottomScrimHeight = 72.dp

/** 拖完松手后，播放器报的位置离目标多近才算「已经到那儿」（毫秒）。 */
private const val PendingSeekToleranceMs = 600f

/** 待生效目标的兜底时长：播放器一直不报新位置也不能让进度条永远挂着（毫秒）。 */
private const val PendingSeekTimeoutMs = 1_200L

/** 最底边进度条占的条带高度：既是它的触摸区，也是底部那一行要给它让出的位置。
 *  NOTE: 这个值直接决定「时间胶囊 / 全屏」离下边有多远——条带越矮，它们越贴底；但也不能小于
 *  12dp，否则那两颗按钮的下沿会落进进度条的拖动区，点按钮会变成拖进度。 */
private val ProgressBarReservedHeight = 12.dp

/**
 * 全屏时进度条离屏幕边缘的距离（底部与左右各一份）。
 *
 * NOTE: 比球半径（6dp）明显大一些：球在 0% / 100% 和屏幕下沿都不能被裁，同时横屏画面本身是
 * 居中的（左右各有黑边），条再贴边就会和黑边打架——16dp 是"看得出留白但不悬浮"的量级。
 * NOTE: 底部那一行（时间胶囊 / 全屏键）要跟着抬起同样的高度，否则它会压在进度条上。
 */
private val FullscreenBarInset = 16.dp

/** 全屏设置面板的宽度：六档倍速（"0.75x" + 勾）够用，又不至于把画面挡住。 */
private val FullscreenMenuWidth = 176.dp

/** 顶部挡板高度：给右上角的设置图标垫一层，亮画面上也读得清。 */
private val TopScrimHeight = 56.dp
