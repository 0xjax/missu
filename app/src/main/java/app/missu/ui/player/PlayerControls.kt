@file:androidx.media3.common.util.UnstableApi

package app.missu.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.ProgressStateWithTickInterval
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import app.missu.R
import app.missu.data.api.SourceConfig
import app.missu.data.model.SeekPreviewSpec
import app.missu.ui.components.MissuBottomSheet
import coil3.compose.rememberAsyncImagePainter
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
@UnstableApi
@Composable
fun PlayerOverlay(
    player: Player,
    visible: Boolean,
    seekEnabled: Boolean,
    /** 播放态：由外层给（它带 seek 静默窗口，seek 期间不会闪成暂停图标）。 */
    isPlaying: Boolean,
    isFullscreen: Boolean,
    onSettingsClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    seekPreview: SeekPreviewSpec?,
    onSeekDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** 双击 seek 的提示（带符号的秒数，如 -20 / +10）；为空表示不显示。 */
    seekHintSeconds: Int? = null,
) {
    // 内嵌进度条常显，所以控件隐藏后仍要更新；全屏才随控件停止轮询。
    // 状态只在时间文本与进度条内部读取，避免每 200ms 重组整个控件层。
    val progress = rememberProgressStateWithTickInterval(player, 200L)
    // 只有时间胶囊与缩略图读取预览位置，拖动时不会重组整个控件层。
    val seekPreviewPositionMs = remember { mutableStateOf<Long?>(null) }
    var seekDragging by remember { mutableStateOf(false) }
    // NOTE: 全屏时进度条必须离屏幕边缘留一点：圆球有半径，条贴在物理边上时它下半截会被屏幕裁掉，
    // 两端位置（0% / 100%）也会被左右裁掉一半。内嵌时相反——球**故意**骑在视频下沿上，所以留 0。
    // NOTE: 全屏的条已经和控件一起显示/隐藏，就是控件组的一员：左右内缩跟齿轮、时间胶囊、全屏键
    // 落在同一条竖线上（都是 16dp），不能比它们更靠外。
    val barInset = if (isFullscreen) FullscreenBarInset else 0.dp
    val barSideInset = if (isFullscreen) FullscreenBarSideInset else 0.dp
    // 条占的条带：全屏的球更大，条带也要跟着加高，否则球会压到底部那一行。
    val barBand = if (isFullscreen) FullscreenBarHeight else ProgressBarReservedHeight
    LaunchedEffect(player, visible, isFullscreen) {
        if (isFullscreen && !visible) return@LaunchedEffect
        progress.observe()
    }

    Box(modifier.fillMaxSize()) {
        // 挡板在进度条下面，按钮在上面：扩大触摸区后，按钮仍优先命中，圆球也不会被挡板压暗。
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BottomScrimHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )
        }

        // 进度条：内嵌时常显；全屏时跟随其余控件显隐。
        AnimatedVisibility(
            visible = !isFullscreen || visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            SeekBar(
                progress = progress,
                showThumb = visible,
                enabled = seekEnabled,
                onSeek = { target -> player.seekTo(target) },
                onDraggingChange = { dragging ->
                    seekDragging = dragging
                    onSeekDraggingChange(dragging)
                },
                previewPositionMs = seekPreviewPositionMs,
                fullscreen = isFullscreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = barSideInset, end = barSideInset, bottom = barInset),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
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

                Column(
                    Modifier
                        .fillMaxSize()
                        // NOTE: 全屏时整层控件统一让出侧边：圆底外面还有一圈 48dp 的触摸区，比圆底
                        // 每边大 8dp，所以这一层加 8dp 之后，「看得见的边」离屏幕边正好 16dp
                        // （时间胶囊本来就还有 8dp 的行内 padding，跟着一起走到 16dp）。
                        // 内嵌不加：16:9 的框本来就窄，再收就压到画面了。
                        .padding(horizontal = if (isFullscreen) FullscreenControlSideInset else 0.dp),
                ) {
                    // 顶部右侧：设置（倍速等）。
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 设置也套一层圆底：和播放键、时间胶囊同一个悬浮语言（裸白图标在亮画面上不稳）。
                        RoundButton(
                            size = 36.dp,
                            label = stringResource(R.string.player_settings),
                            onClick = onSettingsClick,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
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
                            label = stringResource(
                                if (isPlaying) R.string.player_pause else R.string.player_play,
                            ),
                            // CRITICAL: 点击判定必须按**播放器的意图**（`playWhenReady`），不能用
                            // 上面那个 `isPlaying` 状态——seek 静默窗口会屏蔽 paused 事件，状态可能
                            // 还是「在播」，于是点一下又发 pause()，永远起不来（2026-11-15 实测）。
                            // `isPlaying` 只用来决定图标。
                            onClick = { if (player.playWhenReady) player.pause() else player.play() },
                        ) {
                            if (isPlaying) {
                                Icon(
                                    imageVector = Icons.Outlined.Pause,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
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
                            .padding(start = 8.dp, bottom = barBand + barInset),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaybackTimePill(progress, seekPreviewPositionMs)
                        Spacer(Modifier.weight(1f))
                        RoundButton(
                            size = 36.dp,
                            label = stringResource(
                                if (isFullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
                            ),
                            onClick = onToggleFullscreen,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInFull,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp).graphicsLayer { scaleX = -1f },
                            )
                        }
                    }
                }
            }
        }

        seekPreview?.let { spec ->
            AnimatedVisibility(
                visible = seekDragging,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(100)),
            ) {
                SeekPreviewThumbnail(
                    spec = spec,
                    positionMs = seekPreviewPositionMs,
                    durationMs = progress.durationMs,
                    fullscreen = isFullscreen,
                )
            }
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

@Composable
private fun SeekPreviewThumbnail(
    spec: SeekPreviewSpec,
    positionMs: MutableState<Long?>,
    durationMs: Long,
    fullscreen: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val position = positionMs.value ?: return@BoxWithConstraints
        val frame = spec.frameAt(position, durationMs) ?: return@BoxWithConstraints
        val density = LocalDensity.current
        val context = LocalContext.current
        // 与媒体流相同：CDN 会拒绝没有站点 Referer / Android UA 的雪碧图请求。
        val imageRequest = remember(frame.url) {
            ImageRequest.Builder(context)
                .data(frame.url)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set("Referer", SourceConfig.BASE_URL + "/")
                        .set("User-Agent", SourceConfig.DEFAULT_USER_AGENT)
                        .build(),
                )
                .build()
        }
        val width = if (fullscreen) 160.dp else 120.dp
        val height = width * spec.frameHeight.toFloat() / spec.frameWidth.toFloat()
        val widthPx = with(density) { width.toPx() }
        val heightPx = with(density) { height.toPx() }
        val edgePx = with(density) { 8.dp.toPx() }
        val bottomPx = with(density) { (if (fullscreen) FullscreenBarInset + 12.dp else 12.dp).toPx() }
        val maxX = (constraints.maxWidth - widthPx - edgePx).coerceAtLeast(edgePx)
        val x = (constraints.maxWidth * progressFraction(position, durationMs) - widthPx / 2f)
            .coerceIn(edgePx, maxX)
        val y = (constraints.maxHeight - heightPx - bottomPx).coerceAtLeast(0f)
        val scale = widthPx / spec.frameWidth.toFloat()
        val painter = rememberAsyncImagePainter(imageRequest)

        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(width, height)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
        ) {
            // 整张雪碧图交给 Coil 缓存；Canvas 按原始像素坐标缩放并移动，只显示目标帧。
            Canvas(Modifier.fillMaxSize()) {
                withTransform({
                    translate(left = -frame.x * scale, top = -frame.y * scale)
                    scale(scale, scale, Offset.Zero)
                }) {
                    with(painter) {
                        draw(
                            Size(
                                spec.frameWidth * spec.columns.toFloat(),
                                spec.frameHeight * spec.rows.toFloat(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 进度条：细线贴在视频最底边，透明触摸区向上 36dp、向下 12dp，不改变视觉布局。
 *
 * NOTE: 内嵌时控件隐藏也能直接按下拖动；按下唤醒控件，松手才 seek，取消则不改变播放位置。
 * NOTE: 已播部分与圆球都取**主题色**；未播部分是 30% 白，压在任意画面上都能看出剩余。
 * NOTE: 不用 M3 `Slider`——1.5 的拇指是一根竖条，播放位置 0 时就是左下角一根突兀的白杠。
 */
@Composable
private fun SeekBar(
    progress: ProgressStateWithTickInterval,
    showThumb: Boolean,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    previewPositionMs: MutableState<Long?>,
    modifier: Modifier = Modifier,
    /** 全屏的条**更粗**、球更大：横屏是一整屏，3dp 的细线在大画面上轻得像根毛。 */
    fullscreen: Boolean = false,
) {
    val positionMs = progress.currentPositionMs
    val durationMs = progress.durationMs
    val thickness = if (fullscreen) FullscreenTrackThickness else InlineTrackThickness
    val thumbRadius = if (fullscreen) FullscreenThumbRadius else InlineThumbRadius
    val bandHeight = if (fullscreen) FullscreenBarHeight else ProgressBarReservedHeight
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(1) }
    // NOTE: 松手后不能立刻切回「轮询到的位置」——播放器要过一会儿才报新位置，中间那一两帧
    // 会按旧位置算，看起来就是「进度条闪回一下再跳过去」。所以先挂一个待生效目标，等轮询到的
    // 位置真的接近它（或超时兜底）再交还给轮询。
    var pendingFraction by remember { mutableStateOf<Float?>(null) }
    val polledFraction = progressFraction(positionMs, durationMs)
    LaunchedEffect(pendingFraction, positionMs) {
        val pending = pendingFraction ?: return@LaunchedEffect
        if (durationMs > 0L && abs(positionMs - pending * durationMs) < PendingSeekToleranceMs) {
            pendingFraction = null
            previewPositionMs.value = null
        }
    }
    LaunchedEffect(pendingFraction) {
        if (pendingFraction != null) {
            delay(PendingSeekTimeoutMs)
            pendingFraction = null
            previewPositionMs.value = null
        }
    }
    val fraction = when {
        dragging -> dragFraction
        pendingFraction != null -> pendingFraction!!
        else -> polledFraction
    }
    val currentFraction by rememberUpdatedState(fraction)
    val trackThickness by animateDpAsState(
        targetValue = if (dragging) thickness + 2.dp else thickness,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "seekTrackThickness",
    )
    val currentThumbRadius by animateDpAsState(
        targetValue = if (dragging) thumbRadius + 2.dp else thumbRadius,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "seekThumbRadius",
    )
    val activeColor = MaterialTheme.colorScheme.primary
    val progressLabel = stringResource(R.string.player_progress)

    Box(
        modifier
            .fillMaxWidth()
            .height(SeekTouchTargetHeight)
            .offset(y = SeekTouchBelow)
            .semantics {
                contentDescription = progressLabel
                progressBarRangeInfo = ProgressBarRangeInfo(current = fraction, range = 0f..1f)
                setProgress { target ->
                    if (durationMs <= 0L) return@setProgress false
                    val coerced = target.coerceIn(0f, 1f)
                    pendingFraction = coerced
                    previewPositionMs.value = (coerced * durationMs).toLong()
                    onSeek((coerced * durationMs).toLong())
                    true
                }
            }
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(durationMs, enabled) {
                if (!enabled || durationMs <= 0L) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startX = down.position.x
                    val startFraction = currentFraction
                    val thumbTouch = abs(startX - startFraction * widthPx) <= ThumbTouchRadius.toPx()
                    var dragStarted = false
                    var seekCommitted = false
                    down.consume()
                    onDraggingChange(true)
                    dragging = true
                    dragFraction = startFraction
                    previewPositionMs.value = (startFraction * durationMs).toLong()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                val target = if (dragStarted) {
                                    dragFraction
                                } else {
                                    (startX / widthPx.toFloat()).coerceIn(0f, 1f)
                                }
                                pendingFraction = target
                                previewPositionMs.value = (target * durationMs).toLong()
                                onSeek((target * durationMs).toLong())
                                seekCommitted = true
                                change.consume()
                                break
                            }
                            val distanceX = change.position.x - startX
                            if (!dragStarted && abs(distanceX) >= viewConfiguration.touchSlop) {
                                dragStarted = true
                            }
                            if (dragStarted) {
                                dragFraction = (if (thumbTouch) {
                                    change.position.x / widthPx.toFloat()
                                } else {
                                    startFraction + distanceX / widthPx.toFloat()
                                }).coerceIn(0f, 1f)
                                previewPositionMs.value = (dragFraction * durationMs).toLong()
                                change.consume()
                            }
                        }
                    } finally {
                        dragging = false
                        if (!seekCommitted) previewPositionMs.value = null
                        onDraggingChange(false)
                    }
                }
            },
    ) {
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = SeekTouchAbove - bandHeight)
                .fillMaxWidth()
                .height(bandHeight),
        ) {
            val trackHeight = trackThickness.toPx()
            // 圆球故意骑在视频与页面的分界上；全屏由调用方留 inset，避免被物理边缘裁掉。
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
            if (showThumb || dragging) {
                drawCircle(
                    color = activeColor,
                    radius = currentThumbRadius.toPx(),
                    center = Offset(playedX, centerY),
                )
            }
        }
    }
}

/** 「当前 / 总时长」胶囊：seek 预览期间仅当前时间用主题色，总时长与容器保持不变。 */
@Composable
private fun PlaybackTimePill(
    progress: ProgressStateWithTickInterval,
    previewPositionMs: MutableState<Long?>,
) {
    val preview = previewPositionMs.value
    TimePill(
        currentTime = formatTime(preview ?: progress.currentPositionMs),
        duration = formatTime(progress.durationMs),
        seeking = preview != null,
    )
}

@Composable
private fun TimePill(
    currentTime: String,
    duration: String,
    seeking: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.Black.copy(alpha = 0.36f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = currentTime,
            color = if (seeking) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 11.sp,
        )
        Text(text = " / $duration", color = Color.White, fontSize = 11.sp)
    }
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
    label: String,
    dim: Float = 0.36f,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (size > 48.dp) size else 48.dp)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
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
 * 播放器设置面板：**两级**。
 *
 * 一级只列设置项（倍速 / 画面），每行右边是当前值；点进去才在同一个抽屉里展开那一组选项
 * 并给一个返回箭头。一级永远只有两行高，绝不把六档倍速铺在一级里——那样光倍速就占掉半个
 * 屏幕（2026-11-16 反复反馈）。
 *
 * NOTE: 抽屉本身**窄**（`PlayerSheetMaxWidth`），不是横屏整条白板。
 * NOTE: 由播放器持有、不放进控件层——放在会被自动隐藏的控件树里会跟着一起被销毁
 * （实测「菜单一闪就关」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    speed: Float,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    fill: Boolean,
    onFillChange: (Boolean) -> Unit,
) {
    // 一个抽屉、两级内容。切换只做「共享轴 X」：水平滑 1/10 屏宽 + 交叉淡入淡出，
    // 规范从 `MaterialTheme.motionScheme` 取（不写死时长，见 md3e 的动效规则）。
    // 为什么不是一个抽屉滑下去再升一个：竖屏里整块出进又慢又晃，用户否过。
    var level by remember { mutableStateOf(PlayerSection.Root) }
    // 系统返回键：二级时先回一级（标准的下钻行为），一级时交给抽屉自己关闭。
    BackHandler(enabled = level != PlayerSection.Root) { level = PlayerSection.Root }

    MissuBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = PlayerSheetMaxWidth,
    ) {
        Column(Modifier.fillMaxWidth().height(PlayerSheetHeight)) {
                // NOTE: spec 必须在 @Composable 作用域里取好——transitionSpec 那个 lambda 不是
                // Composable，里面读 MaterialTheme 会编译不过。
                val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    AnimatedContent(
                        targetState = level,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            val direction = if (forward) 1 else -1
                            (
                                slideInHorizontally(spatialSpec) { width -> direction * width / 10 } +
                                    fadeIn(effectsSpec)
                                ).togetherWith(
                                slideOutHorizontally(spatialSpec) { width -> -direction * width / 10 } +
                                    fadeOut(effectsSpec),
                            )
                        },
                        label = "playerSection",
                    ) { section ->
                        Column(Modifier.fillMaxSize()) {
                            when (section) {
                                PlayerSection.Root -> {
                                    PlayerSettingRow(
                                        label = stringResource(R.string.player_speed),
                                        value = formatSpeed(speed),
                                        onClick = { level = PlayerSection.Speed },
                                    )
                                    PlayerSettingRow(
                                        label = stringResource(R.string.player_aspect),
                                        value = stringResource(
                                            if (fill) {
                                                R.string.player_aspect_fill
                                            } else {
                                                R.string.player_aspect_fit
                                            },
                                        ),
                                        onClick = { level = PlayerSection.Aspect },
                                    )
                                }

                                PlayerSection.Speed -> {
                                    PlayerSectionHeader(
                                        title = stringResource(R.string.player_speed),
                                        onBack = { level = PlayerSection.Root },
                                    )
                                    OptionChips(
                                        options = SPEEDS.map { it to formatSpeed(it) },
                                        selected = speed,
                                        onSelect = {
                                            onSpeedChange(it)
                                            onDismiss()
                                        },
                                        colors = FilterChipDefaults.filterChipColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                PlayerSection.Aspect -> {
                                    PlayerSectionHeader(
                                        title = stringResource(R.string.player_aspect),
                                        onBack = { level = PlayerSection.Root },
                                    )
                                    OptionChips(
                                        options = ASPECT_OPTIONS.map { (value, labelRes) ->
                                            value to stringResource(labelRes)
                                        },
                                        selected = fill,
                                        onSelect = {
                                            onFillChange(it)
                                            onDismiss()
                                        },
                                        colors = FilterChipDefaults.filterChipColors(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

/** 设置面板的层级。一级只列项目，二级才给选项。 */
private enum class PlayerSection { Root, Speed, Aspect }

/** 一级的一行：标签 + 当前值 + 右边箭头（表示"点进去"）。 */
@Composable
private fun PlayerSettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 二级的标题行：返回箭头 + 这一组的名字。返回键和系统返回都只回一级。 */
@Composable
private fun PlayerSectionHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
            )
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium)
    }
}

/** 设置抽屉的宽度：一级只是两行设置项，不该占满横屏（YouTube 的一级抽屉也是窄的一列）。 */
private val PlayerSheetMaxWidth = 360.dp

/** 抽屉的固定高度：一级/二级切换时高度不变，同时给将来新增的设置项留出余量。 */
private val PlayerSheetHeight = 240.dp


private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 一组单选胶囊（不带标签）。宽度由调用方给，换行由 `FlowRow` 负责。 */
@Composable
private fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    colors: SelectableChipColors,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, text) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                colors = colors,
                label = { Text(text) },
                // NOTE: 选中态只靠容器色表达，不加勾：勾会让选中的那颗变宽，一选整排就重排。
            )
        }
    }
}

/** 画面比例的两个选项：false = 适应（完整画面），true = 填充（裁满内嵌框）。 */
@OptIn(ExperimentalMaterial3Api::class)
private val ASPECT_OPTIONS = listOf(
    false to R.string.player_aspect_fit,
    true to R.string.player_aspect_fill,
)

/** 底部挡板高度：正好覆盖进度条那一行，不往画面中间糊。 */
private val BottomScrimHeight = 72.dp

/** 拖完松手后，播放器报的位置离目标多近才算「已经到那儿」（毫秒）。 */
private const val PendingSeekToleranceMs = 600f

/** 待生效目标的兜底时长：播放器一直不报新位置也不能让进度条永远挂着（毫秒）。 */
private const val PendingSeekTimeoutMs = 1_200L

/** 内嵌条的绘制高度，也是底部控件行给它预留的空间；透明触摸区单独向上扩展。 */
private val ProgressBarReservedHeight = 12.dp

/** 进度条触摸区：上 36dp、下 12dp；按钮层在上方，不会抢走时间胶囊与全屏键的点击。 */
private val SeekTouchAbove = 36.dp
private val SeekTouchBelow = 12.dp
private val SeekTouchTargetHeight = SeekTouchAbove + SeekTouchBelow

/** 圆球抓取范围：大于可见圆球，抓住时跟随手指；其余轨道拖动按位移相对调整。 */
private val ThumbTouchRadius = 24.dp

/**
 * 全屏时进度条离屏幕边缘的距离（底部与左右各一份）。
 *
 * NOTE: 比球半径（8dp）明显大一些：球在 0% / 100% 和屏幕下沿都不能被裁，横屏画面本身又是
 * 居中的（左右各有黑边），条再贴边就会和黑边打架——和控件那圈同一个 24dp。
 * NOTE: 底部那一行（时间胶囊 / 全屏键）要跟着抬起同样的高度，否则它会压在进度条上。
 */
private val FullscreenBarInset = 24.dp

/**
 * 全屏时进度条左右的留白。
 *
 * NOTE: 比控件组再往里一档（32dp vs 按钮圆边 24dp）：条对的是时间胶囊里**文字**的左沿和
 * 全屏键的图标，不是胶囊/圆底的边——对到边的条会显得比那一行还宽，看着不齐。
 */
private val FullscreenBarSideInset = 32.dp

/**
 * 全屏时整层控件（齿轮 / 时间胶囊 / 全屏键）离屏幕左右边的额外留白。
 *
 * NOTE: 圆底的触摸区是 48dp、可见圆底 32dp，每边虚 8dp——所以这一层加 16dp，露出来的圆边
 * 才是离屏幕 24dp；时间胶囊那边还有 8dp 行内 padding，同样落到 24dp 上。
 */
private val FullscreenControlSideInset = 16.dp

/** 内嵌条：3dp 细线 + 12dp 球，贴视频下沿（球故意骑在分界上）。 */
private val InlineTrackThickness = 3.dp
private val InlineThumbRadius = 6.dp

/** 全屏条：更粗、球更大，条带也更高（球不能压到底部那一行）。 */
private val FullscreenTrackThickness = 5.dp
private val FullscreenThumbRadius = 8.dp
private val FullscreenBarHeight = 20.dp


/** 顶部挡板高度：给右上角的设置图标垫一层，亮画面上也读得清。 */
private val TopScrimHeight = 56.dp
