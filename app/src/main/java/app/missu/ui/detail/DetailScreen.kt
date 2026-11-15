@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.missu.R
import app.missu.data.model.DetailInfoRow
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist
import app.missu.ui.player.ExoVideoPlayer
import app.missu.ui.player.FullscreenEffect
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.MissuBottomSheet
import app.missu.ui.components.PageStateTransition
import app.missu.ui.common.UiMessage
import app.missu.ui.common.text
import app.missu.ui.theme.LocalDarkTheme
import app.missu.ui.components.heroKeyFor
import app.missu.ui.components.videoHero
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    // NOTE: 共享容器的 key 与卡片一侧一致（路由里的 id），详情数据还没回来时也拿得到。
    videoId: String = "",
    // NOTE: 卡片的来源标签：首页同一部影片可能出现在两个板块里，光靠 id 会和另一张卡片配对
    // （封面往屏外飞）。导航参数原样带过来，两侧用 heroKeyFor 算出同一个 key。
    heroTag: String = "",
    // NOTE: 卡片封面。详情解析出来之前先用它铺满播放器框——点卡片时那个框就是从这里长过来的。
    posterUrl: String? = null,
    // NOTE: 这个目的地还是不是导航栈顶。返回时它会先变 false，用来在**转场开始那一帧**
    // 把容器内容换回封面、停掉音频（比目的地被销毁早整整一个转场）。
    isCurrent: Boolean = true,
    message: UiMessage? = null,
    onToggleSaved: () -> Unit = {},
    startPositionMs: Long = 0L,
    onPositionReport: (Long) -> Unit = {},
    playlists: PlaylistUiState = PlaylistUiState.Hidden,
    onOpenPlaylists: () -> Unit = {},
    onClosePlaylists: () -> Unit = {},
    onTogglePlaylist: (VideoPlaylist) -> Unit = {},
    onOpenPath: (path: String, title: String) -> Unit = { _, _ -> },
) {
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    FullscreenEffect(fullscreen)
    // NOTE: 全屏时返回键/侧滑**先退全屏**，不退出详情：横屏里返回行被藏起来，播放器是唯一的画面，
    // 侧滑一下就把整部片连同这一页丢掉太狠；而且退出全屏要由我们主动发 `requestedOrientation`，
    // 交给导航走的话页面直接没了、横屏状态还得靠 onDispose 兜底。
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val detail = (state as? DetailUiState.Content)?.detail
    // NOTE: 卡片带过来的那张优先，而不是详情页的 og:image。理由有两个：它和卡片上看到的
    // 是同一张（容器长到位时画面不会变），而且它已经在 Coil 的内存缓存里（不会从纯色淡入）。
    val poster = posterUrl ?: detail?.thumbnailUrl

    // 上滑进全屏 / 下滑退全屏：跟手进度 0..1。转屏本身没法跟手（那是另一套窗口配置），
    // 所以松手过阈值之后再交给系统转，跟手这一段负责「越拖越大 / 越拖越缩」。
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val animationScope = rememberCoroutineScope()

    // NOTE: 状态栏那条是**黑的**，图标反过来用浅色：这一页顶上就是播放器，浅色页面底 + 深色图标
    // 在转全屏那一下会闪一条白边；黑条和播放器/横屏全屏的黑底是同一种颜色，旋转就看不出来了。
    // NOTE: 离开这一页必须还回去（按当前主题），否则回到列表还是浅色图标压在浅色页头上。
    val view = LocalView.current
    val darkTheme = LocalDarkTheme.current
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(window, darkTheme) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !darkTheme }
    }
    val density = LocalDensity.current
    val statusBarInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }

    // NOTE: 播放器必须和正文**并列**在一个 Box 里，不能只是 Column 的兄弟：上滑放大时它要长到
    // 正文上面去，而 Column 中后一个孩子会把前一个盖住。所以正文让出播放器高度的占位，
    // 播放器自己绝对定位在顶部、zIndex 抬到最上面。
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        // 状态栏那条黑底：铺在页面底色之上、播放器之下（播放器本身也整体下移了同样高度）。
        if (!fullscreen) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusBarInset)
                    .background(Color.Black),
            )
        }

        // 内嵌播放器的高度：整宽 16:9。正文从这个高度之下开始。
        val videoHeight = maxWidth * 9f / 16f
        val screenHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // 提交阈值：竖屏浅、横屏深（理由见 DragCommitPortrait / Landscape）。
        val commitThresholdPx = screenHeightPx * if (fullscreen) DragCommitLandscape else DragCommitPortrait

        val dragModifier = Modifier.pointerInput(fullscreen, commitThresholdPx) {
            var accumulated = 0f
            fun amountOf(): Float = if (fullscreen) accumulated else -accumulated
            detectVerticalDragGestures(
                onDragStart = { accumulated = 0f },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    accumulated += dy
                    // 竖屏只认上滑，横屏只认下滑；反方向一律不跟手。
                    val amount = amountOf()
                    dragProgress = if (amount > 0f) {
                        (amount / commitThresholdPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                onDragEnd = {
                    if (amountOf() >= commitThresholdPx) {
                        animationScope.launch {
                            animate(initialValue = dragProgress, targetValue = 1f, animationSpec = tween(DragCommitMs)) { value, _ ->
                                dragProgress = value
                            }
                            // 交出去：转屏由系统做，布局换成全屏后进度归零。
                            fullscreen = !fullscreen
                            dragProgress = 0f
                        }
                    } else {
                        animationScope.launch {
                            animate(initialValue = dragProgress, targetValue = 0f, animationSpec = tween(DragCancelMs)) { value, _ ->
                                dragProgress = value
                            }
                        }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        animate(initialValue = dragProgress, targetValue = 0f, animationSpec = tween(DragCancelMs)) { value, _ ->
                            dragProgress = value
                        }
                    }
                },
            )
        }

        if (!fullscreen) {
            Column(Modifier.fillMaxSize().padding(top = statusBarInset)) {
                // 播放器的位置占位（播放器本身在下面绝对定位，见上）。
                Spacer(Modifier.height(videoHeight))

                // NOTE: 正文（标题/信息行）和底部动作条本来只有在详情数据回来之后才存在。
                // 详情请求常在整页淡入窗口（90–300ms）**之后**才返回，那时把这两块直接摆上去
                // 就是一次硬切——而页签里的其它页面全都有 fade through 兜这一下。所以这里也走
                // `PageStateTransition`：同一个语言，快慢网的行为也一致（快网时它就是跟着整页淡入）。
                PageStateTransition(
                    state = state,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { current ->
                    when (current) {
                        // 加载态不在正文里放转圈——进度统一收在播放器框里（封面 + 延迟出现的
                        // 指示器），否则页面上会同时有两个"正在加载"。
                        DetailUiState.Loading -> Unit

                        is DetailUiState.Error -> ErrorHint(current.message.text(), onRetry)

                        is DetailUiState.Content -> DetailBody(
                            detail = current.detail,
                            message = message,
                            onToggleSaved = onToggleSaved,
                            playlists = playlists,
                            onOpenPlaylists = onOpenPlaylists,
                            onClosePlaylists = onClosePlaylists,
                            onTogglePlaylist = onTogglePlaylist,
                            onOpenPath = onOpenPath,
                        )
                    }
                }
            }
        }

        // 上滑跟手时整页压暗（YouTube：视频在放大、后面那页变暗）。全屏那边底本来就是黑的。
        if (!fullscreen && dragProgress > 0.001f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * dragProgress)))
        }

        // NOTE: 详情页**没有返回行**（2026-11-16 定的）：这一页的全部内容就是视频，返回行是纯
        // chrome，白占掉播放器上方 56dp。退出一律用系统返回（侧滑 / 三键 / 预测性返回的手势预览）。
        // NOTE: 也不做「浮在画面左上角」的那颗键——它会实实在在挡住画面（2026-11-15 实机否掉），
        // 所以那次是整行去掉，不是把它挪进画面里。
        DetailHero(
            state = state,
            poster = poster,
            heroKey = heroKeyFor(heroTag, videoId.ifEmpty { detail?.id.orEmpty() }),
            isCurrent = isCurrent,
            fullscreen = fullscreen,
            startPositionMs = startPositionMs,
            onPositionReport = onPositionReport,
            onToggleFullscreen = { fullscreen = !fullscreen },
            dragProgress = dragProgress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // CRITICAL: 这里只能 offset，**不能** padding。padding 在 modifier 链上会缩掉
                // 播放器自己的可用高度（16:9 的框被压成 16:9 - 状态栏高，实机量出来是 2.07:1），
                // offset 只是整体下移，框本身还是严丝合缝的 16:9。
                // CRITICAL: 只有内嵌才让出状态栏。全屏时系统栏是隐藏的，可它一旦被算进 inset
                // （比如全屏里开了抽屉、那个窗口抢走焦点），这里再 offset 就会把整块画面顶下去。
                .offset(y = if (fullscreen) 0.dp else statusBarInset)
                .zIndex(1f)
                .then(dragModifier),
        )
    }
}

/**
 * 切换全屏的提交阈值：整屏高度的几分之几。
 *
 * NOTE: 两个方向**不是**同一个值：竖屏是"想看大画面"的主动动作，给低一点（滑一点就进）；
 * 横屏的下滑要更刻意才退出——否则看片时手一动就掉回竖屏。
 */
private const val DragCommitPortrait = 0.12f
private const val DragCommitLandscape = 0.26f

/** 过阈值后先把跟手进度补完再交出去；没到阈值就回弹。 */
private const val DragCommitMs = 120
private const val DragCancelMs = 180

/** 视频出画时封面淡出的时长；与控件出现是同一段，看起来像「画面显影」。 */
private const val POSTER_FADE_MS = 200

/** 转圈延迟出现的时长：快的时候（内存缓存 / 秒开）不该闪一下转圈。 */
private const val PROGRESS_DELAY_MS = 600L

/**
 * 封面虚焦的时序与强度。
 *
 * NOTE: 延迟必须 ≥ 容器转场时长（300ms）。飞行途中就开始虚化，等于把刚建立的
 * 「同一个东西」的连续性自己拆掉——先让它落位、被看清，再让它退到"未就绪"的状态里。
 * NOTE: 爬升时长按**典型的视频起步延迟（2–3 秒）**来定，不是按普通状态切换的 300ms：
 * 它要在整段等待里持续可见地变化，而不是"一闪就完成、然后一直锁死"。
 */
private const val DEFOCUS_DELAY_MS = 300L
private const val DEFOCUS_RAMP_MS = 1500
private const val POSTER_BLUR_DP = 12
private const val POSTER_SCRIM_ALPHA = 0.22f

/**
 * 播放器框——同时是卡片封面的**共享容器**（container transform 的落点）。
 *
 * 这里也是「静帧 → 动态画面」的接缝，四件事必须按顺序发生，否则接缝会露出来：
 * 1. 容器长大到位时，里面还是**同一张封面**（卡片那张，从内存缓存来），此时不虚化；
 * 2. 落位 300ms 后开始虚焦 + 轻压暗（600ms 渐变）：降低封面的"认知抓力"，
 *    让画面只有一个主角——还没出现的视频。等待中若一直清晰，用户会以为这就是内容；
 * 3. 视频第一帧真正出画之前，封面一直在；中途容器里不会空一下，也不会先出现控件；
 * 4. 第一帧出画 → 封面 200ms 淡出（画面在下面显影），控件同时出现，之后 3 秒无操作自动隐藏。
 *
 * NOTE: 虚化用**两层静态图交叉淡入**（清晰层 + 虚焦层），不逐帧改 blur 半径——
 * 后者每帧重建 RenderEffect，中端机会掉帧；前者只动 alpha，成本恒定且可逆。
 * `Modifier.blur` 在 API < 31 是空操作，那时只剩压暗，降级不崩。
 * NOTE: 封面画在播放器**之上**（含控件层之上），所以过渡只能是「封面淡出露出画面」，
 * 而不可能是「画面替换封面」那种硬切。
 * NOTE: 它必须在首帧就存在，Loading / Error 也不例外：点卡片时共享元素要有另一侧可长进去。
 * NOTE: 播放器本身固定在这一格（不进 LazyColumn），切全屏只是换尺寸、不会离开组合，
 * ExoPlayer 实例与播放进度都能保住。
 */
@Composable
private fun DetailHero(
    state: DetailUiState,
    poster: String?,
    heroKey: String,
    isCurrent: Boolean,
    fullscreen: Boolean,
    startPositionMs: Long,
    onPositionReport: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    /** 上滑 / 下滑切换全屏的跟手进度（0..1）。转屏那一下没法跟手，这一段负责"越拖越大 / 越拖越缩"。 */
    dragProgress: Float = 0f,
) {
    val detail = (state as? DetailUiState.Content)?.detail
    val playbackUrl = detail?.playbackUrl

    // CRITICAL: 这里的状态**只能按 heroKey（路由 id）取 remember，不能按 playbackUrl**。
    // playbackUrl 会从 null 变成真正的地址，按它做 key 等于在详情数据回来那一刻重建状态：
    // Animatable 被重置成 0，正在跑的虚化动画作用在旧实例上、直接被丢掉——表现就是
    // "虚化一小段 → 弹回清晰封面 → 一直清晰等视频"。
    /** 封面已经可以让位给画面（视频出过画，或播放失败要让错误层露出来）。 */
    var revealed by remember(heroKey) { mutableStateOf(false) }
    /** 正在离开当前目的地（返回）。容器里要换回封面，缩回去的才不是一个活视频。 */
    var leaving by remember(heroKey) { mutableStateOf(false) }

    val coverVisible = leaving || !revealed
    val posterAlpha by animateFloatAsState(
        targetValue = if (coverVisible) 1f else 0f,
        animationSpec = tween(durationMillis = POSTER_FADE_MS),
        label = "posterFade",
    )

    // 还在等：要么详情没回来，要么播放地址有了但视频还没出画。
    val pending = detail == null || (playbackUrl != null && !revealed)
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (!pending) {
            showProgress = false
            return@LaunchedEffect
        }
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }

    // NOTE: 用 Animatable 而不是 animateFloatAsState：出画时要把当前值**冻住**让封面整体淡出，
    // 不能反向"重新对焦"（那会在淡出前先闪一下变清晰）。
    val defocus = remember(heroKey) { Animatable(0f) }
    LaunchedEffect(pending) {
        if (pending) {
            delay(DEFOCUS_DELAY_MS)
            defocus.animateTo(1f, tween(durationMillis = DEFOCUS_RAMP_MS))
        } else if (detail != null && playbackUrl == null) {
            // 站点没给 m3u8：不会再播放了，把封面恢复清晰（这里反向是对的）。
            defocus.animateTo(0f, tween(durationMillis = DEFOCUS_RAMP_MS))
        }
    }

    // NOTE: 离开（返回 / 被压栈）时容器里要换回封面：反向的 container transform 落点必须是
    // 卡片上那张封面，不能是一帧视频画面。用 `isCurrent` 而不是拦截返回键——它在 pop 开始那一帧
    // 就变 false（比目的地被销毁早整整一个转场），而且不参与返回决策，预测性返回的手势预览照旧。
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            // 预测性返回被取消：视频重新盖过封面（音频由播放器自己恢复）。
            leaving = false
        } else {
            leaving = true
            // 还在等待（封面是虚的）时离开：把它恢复清晰再飞回去。
            if (!revealed) defocus.snapTo(0f)
        }
    }

    val sharpAlpha = posterAlpha * (1f - defocus.value)
    val softAlpha = posterAlpha * defocus.value

    Box(
        modifier = (if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        })
            .then(modifier)
            .graphicsLayer {
                // 跟手：竖屏里放大（从顶边往下长），横屏里缩小并下移——两个方向都只改绘制，
                // 不参与布局，所以正文不会因为拖动而抖动。
                val scale = if (fullscreen) 1f - 0.12f * dragProgress else 1f + 0.35f * dragProgress
                scaleX = scale
                scaleY = scale
                translationY = (if (fullscreen) 64.dp else -24.dp).toPx() * dragProgress
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .videoHero(heroKey, RectangleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (playbackUrl != null) {
            ExoVideoPlayer(
                url = playbackUrl,
                modifier = Modifier.fillMaxSize(),
                active = isCurrent,
                isFullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                initialPositionMs = startPositionMs,
                onPositionReport = onPositionReport,
                onFirstFrameRendered = { revealed = true },
                // 播放失败：把封面撤掉让错误层露出来（否则它永远被压在最上面）。
                onPlaybackFailed = { revealed = true },
            )
        }

        // 封面盖在播放器之上：出画前它是容器内容，出画时它淡出。
        if (poster != null && sharpAlpha > 0.01f) {
            AsyncImage(
                model = poster,
                contentDescription = detail?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(sharpAlpha),
            )
        }
        if (poster != null && softAlpha > 0.01f) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(POSTER_BLUR_DP.dp)
                    .alpha(softAlpha),
            )
        }
        // 压暗：只压暗不虚化，封面的"同一张图"辨识度就还在，那正是要削掉的东西。
        // CRITICAL: 必须乘上 `posterAlpha`。`defocus` 在视频出画后**停在 1 不回零**（那是刻意的，
        // 免得封面淡出时先“重新对焦”），只按它算透明度的话，这层 22% 黑会永久盖住整块画面——
        // 连播放器的白色控件一起压成 199/255（2026-11-15 量像素确认：白色控件发灰的根因）。
        if (defocus.value > 0.01f && posterAlpha > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = POSTER_SCRIM_ALPHA * defocus.value * posterAlpha)),
            )
        }

        if (showProgress) {
            // NOTE: 压在照片上必须自带底衬，否则浅色封面上的指示器会看不清。
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                // NOTE: 这里刻意**不用**页面级的 `LoadingIndicator`。视频缓冲和「页面在取数」是
                // 两种等待：一个是媒体流在续，一个是界面在等数据。形状家族不同（波浪环 vs 形变团块）
                // 才能一眼分清——同一个团块出现在两个地方，只会让人觉得"整个 App 都在转"。
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                    trackColor = Color(0x40FFFFFF),
                )
            }
        }

        if (playbackUrl == null && detail != null) {
            // 详情拿到了但站点没给 m3u8：压在封面上说一句，否则用户只看到一张静止的封面。
            Text(
                text = stringResource(R.string.detail_no_playback),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xCC000000), MaterialTheme.shapes.small)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 标题：固定在动作条下面，不随信息卡滚动。
 *
 * NOTE: 最多 3 行、超出折成省略号：站点标题常常是整句番号 + 剧情梗概，不限行时一屏会被它吃掉
 * （2026-11-15 实机）。装不下的可点展开/收起，判断和信息行同一套（看实测排版，不猜内容长度）。
 * NOTE: 时长不在这里重复——播放器控件条右侧已经显示总时长，两处一样的信息。
 */
@Composable
private fun DetailTitle(detail: VideoDetail) {
    var expanded by rememberSaveable(detail.id) { mutableStateOf(false) }
    var overflowing by remember(detail.id) { mutableStateOf(false) }
    val label = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand)
    Text(
        text = detail.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (expanded) {
                if (result.lineCount > 3) overflowing = true
            } else {
                overflowing = result.hasVisualOverflow
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // NOTE: clickable 在 padding 之后：命中区只覆盖文字本身，和它当初在列表里时一致。
            .then(
                if (overflowing) {
                    Modifier.clickable(onClickLabel = label) { expanded = !expanded }
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * 播放器下面的一切：标题 / 信息行 / 简介，以及底部的收藏与片单动作。
 *
 * NOTE: 播放器不在这里——它由 [DetailHero] 固定在页首，本函数只负责剩余内容。
 * NOTE: 这里自己开一个 Column：它是 [PageStateTransition] 的内容，拿不到外层的 ColumnScope，
 * 所以 `weight(1f)` 只能由这一层的 Column 提供。
 * NOTE: 版式跟设置页 / 我的同一套：标题是 `titleMedium`，其余内容装进**分组容器**
 * （`DetailGroup`），行间用 `outlineVariant` 分隔线，内外边距 16dp。之前这里是一串裸
 * `bodySmall` 文字，密度和其余页面差一档，读起来像另一个 App。
 * NOTE: 收藏 / 片单那一行固定在**本函数最上面**（也就是视频正下方），不随正文滚动。
 * NOTE: 标题**也是固定的**（2026-11-16 定的）：它和那两个动作是同一块「这个视频」的决策区，
 * 跟着信息卡滚走之后，滚到页尾就再也看不到自己在看哪一部。滚动的只剩信息卡与简介。
 */
@Composable
private fun DetailBody(
    detail: VideoDetail,
    message: UiMessage?,
    onToggleSaved: () -> Unit,
    playlists: PlaylistUiState,
    onOpenPlaylists: () -> Unit,
    onClosePlaylists: () -> Unit,
    onTogglePlaylist: (VideoPlaylist) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
) {
    // NOTE: 触觉只用来确认「状态改变」，不用在导航与滚动上（见 DESIGN.md「Motion」）。收藏要等一次
    // 网络请求，界面上的文字与颜色都要等到结果回来才变——按下去这一下是它唯一即时的回应。
    val haptics = LocalHapticFeedback.current
    // 读屏用的「已收藏」说明：按钮上的文字固定是「收藏」，状态只能从这里出去。
    val savedText = stringResource(R.string.detail_saved)

    // NOTE: 固定区（动作条 + 标题）**浮在列表上面**，不是和列表上下拼接。拼接时内容滚到标题
    // 下面会被一条看不见的边硬切断（2026-11-16 反馈「直接硬切割」）；浮起来之后卡片是从标题
    // 底下滑进去的，再按滚动状态给 6dp 阴影，边界就自然读得出来。不滚动时没有阴影，不留痕迹。
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.canScrollBackward } }
    var headerHeightPx by remember { mutableStateOf(0) }
    val headerHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
    val headerShadow by animateDpAsState(
        targetValue = if (scrolled) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "headerShadow",
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // NOTE: 顶部让出固定区的高度 + **8dp**，不是 16dp：固定区自己已经带了 4dp 下边距，
            // 标题的行身框底下也还有一截 descent——按 16 排下来，第一张卡上面的视觉间距会比
            // 卡片之间（16dp）宽一档（2026-11-16 反馈）。4 + 8 + descent ≈ 16，看着才等距。
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = headerHeight + 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // NOTE: 收藏 / 片单两个动作在固定区里（视频正下方那行 SplitButton），标题也是；
            // 这里只剩可以滚的内容。
            message?.let { notice ->
                item(key = "message") {
                    Text(
                        text = notice.text(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (detail.info.isNotEmpty()) {
                item(key = "info") {
                    DetailGroup {
                        detail.info.forEachIndexed { index, row ->
                            if (index > 0) GroupDivider()
                            InfoRow(row = row, onOpenPath = onOpenPath)
                        }
                    }
                }
            }

            detail.description?.let { description ->
                item(key = "description") {
                    DetailGroup { DescriptionCard(id = detail.id, description = description) }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .zIndex(1f)
                // 阴影要在底色之前：先按矩形投影，再把整块盖成页面底色。
                .shadow(headerShadow)
                .background(MaterialTheme.colorScheme.background)
                // NOTE: onSizeChanged 必须在 padding **之前**：量出来的高度要含这 4dp 下边距，
                // 否则列表让出的位置比固定区矮一截，第一张卡会贴着它的下沿。
                .onSizeChanged { headerHeightPx = it.height }
                // NOTE: 固定区留一点下边距就够：阴影挂在**这一块的边**上，边上直接贴着标题文字
                // 会粘在一起；但也不需要厚——4dp 刚好托住阴影，再多就是白占地方。
                .padding(bottom = 4.dp),
        ) {
            // NOTE: 动作条贴在**视频正下方**（视频和它都是固定不滚的），不是常驻底栏。
            // 理由：这两个动作属于「这个视频」，玩家和它们是同一个决策块；而视频本身不滚，
            // 贴着它同样一屏可见，还省掉了底部那条栏：信息卡可以一直滚到屏幕底，也不存在
            // 「底栏容器色 vs 卡片容器色」的色阶问题（2026-11-15 实机）。
            // NOTE: 收藏状态来自 /api/items/{id}/view；401 会先自动重登一次（见 withAuthRetry）。
            // NOTE: 它和正文一起走 fade through——动作条属于「内容到达」，不该比文字先到或后到。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // NOTE: 上面 12dp、下面只有 6dp，**看起来**才是等距：标题是文字，字身框自带一截
                    // 上边距（titleMedium 24sp 行高里，字形还没开始就已经占了几 dp），按 12/12 排下来
                    // 标题那一侧的视觉间距会明显宽一档（2026-11-16 反馈）。
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            ) {
                SplitButtonLayout(
                    leadingButton = {
                        // NOTE: 用 **tonal** 变体而不是默认填充：容器色落成 `secondaryContainer`
                        // （这套色板里是柔和玫瑰），和页面其余部分（surface 系 + 色阶）同一档，
                        // 不会出现一块高饱和的蓝压在内容中间（DESIGN.md 的 Anti-references）。
                        SplitButtonDefaults.TonalLeadingButton(
                            onClick = {
                                // 语义按「这次点击会把它变成什么」给：已收藏 → ToggleOff。
                                haptics.performHapticFeedback(
                                    if (detail.saved) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn,
                                )
                                onToggleSaved()
                            },
                        ) {
                            // NOTE: 文字固定「收藏」不随状态变（`detail_saved` 会多一个字，左右半的宽度
                            // 会跟着跳、右半跟着位移）；状态由**爱心**表达：空心 = 未收藏，实心红 = 已收藏。
                            // NOTE: 图标自己不带标签（文字已经是动作名），收藏态通过 `stateDescription`
                            // 交给读屏，否则“已收藏”这个状态对无障碍用户不可见。
                            Row(
                                modifier = Modifier.semantics {
                                    if (detail.saved) stateDescription = savedText
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (detail.saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (detail.saved) FavoriteRed else LocalContentColor.current,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.detail_save))
                            }
                        }
                    },
                    trailingButton = {
                        // NOTE: `SplitButtonDefaults` 没有「tonal + 普通点击」的尾半（TonalTrailingButton
                        // 只有开关变体，带 checked/onCheckedChange），所以尾半保留默认按钮、只把
                        // 颜色换成 tonal——两半的容器色就一致了。
                        SplitButtonDefaults.TrailingButton(
                            onClick = onOpenPlaylists,
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            // NOTE: 右半也带文字：两半都只有文字或都只有图标才"整齐"；左半有字、右半裸图标
                            // 会读成两个不同层级的控件。图标 18dp 与左半对齐，标签就是动作名，图标不再另挂
                            // contentDescription（按钮会把文字作为无障碍名）。
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = PlaylistAddIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                // NOTE: 标签是「片单」两个字，不是「加入片单」——和左半的「收藏」等长，
                                // 两半才齐整；动词由图标 + 位置表达。完整的「加入片单」留给片单面板标题。
                                Text(stringResource(R.string.detail_playlist))
                            }
                        }
                    },
                )
            }
            DetailTitle(detail)
        }
    }

    if (playlists !is PlaylistUiState.Hidden) {
        PlaylistSheet(state = playlists, onDismiss = onClosePlaylists, onToggle = onTogglePlaylist)
    }
}

/** 梗概折叠时的行数：三行足够判断"要不要读下去"，又不至于把首屏吃掉。 */
private const val DescriptionCollapsedLines = 3

/**
 * 剧情梗概卡片：默认只露 3 行，底部给一个**看得见的**「展开 / 收起」。
 *
 * NOTE: 控件必须看得见——正文里的一段文字不会让人觉得它可以点（标题可以点文字，是因为点标题
 * 本来就是常识；正文不行，2026-11-16 定）。
 * NOTE: 只有真的装不下才出现这一行：短梗概原样全显示，不多一次点击。
 * NOTE: 「装不下」是粘性的，和 [InfoRow] 同一套：展开后 hasVisualOverflow 会变 false，
 * 那正是要收起的时刻，所以展开态改用「行数 > 3」判断。
 */
@Composable
private fun DescriptionCard(id: String, description: String) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    var overflowing by remember(id) { mutableStateOf(false) }
    val label = stringResource(if (expanded) R.string.detail_collapse else R.string.detail_expand)

    Column(Modifier.padding(16.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else DescriptionCollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (expanded) {
                    if (result.lineCount > DescriptionCollapsedLines) overflowing = true
                } else {
                    overflowing = result.hasVisualOverflow
                }
            },
        )
        if (overflowing) {
            Row(
                // NOTE: 居右：同一张信息卡里那些「装不下」的箭头（`InfoRow`）就在右边，整页只讲
                // 一种语言——展开的指示器靠右。居左会和正文首行抢起跑线，读起来像正文的一部分。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = label) { expanded = !expanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 加入片单的面板。NOTE: 只展示与勾选，新建片单要走站点的网页表单，暂未实现。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSheet(
    state: PlaylistUiState,
    onDismiss: () -> Unit,
    onToggle: (VideoPlaylist) -> Unit,
) {
    MissuBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.detail_add_to_playlist),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            when (state) {
                PlaylistUiState.Hidden -> Unit
                PlaylistUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                is PlaylistUiState.Error -> Text(
                    text = state.message.text(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )

                is PlaylistUiState.Content -> {
                    if (state.items.isEmpty()) {
                        Text(
                            text = stringResource(R.string.detail_no_playlists),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    state.items.forEach { item ->
                        ListItem(
                            modifier = Modifier.clickable { onToggle(item) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            trailingContent = {
                                // NOTE: 整行已经可点，勾选框不再接一次点击——否则读屏会看到两个节点
                                // （与设置页的单选行同一处理）。
                                Checkbox(
                                    checked = item.added,
                                    onCheckedChange = null,
                                    enabled = state.pendingKey == null || state.pendingKey == item.key,
                                )
                            },
                        ) {
                            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
/**
 * 收藏心形的红。**语义色**，不属于主题色板（和卡片角标那几档同性质）：收藏的红在浅色/深色
 * 主题里都该是同一个可识别的心形红，跟着主题变就不是「收藏」了。
 */
private val FavoriteRed = Color(0xFFE5484D)

/**
 * 「加入片单」图标（Material 的 `playlist_add`：三条横线 + 右侧一个加号）。
 *
 * NOTE: 自己画，不引 `material-icons-extended`——为了一个图标把整套图标塞进 APK 不值得，
 * 本工程一直只用 `material-icons-core`（见 `app/build.gradle.kts` 里那条注释）。
 * 几何按 24×24 视口画成实心矩形，`Icon` 的 tint 照常生效。
 */
private val PlaylistAddIcon: ImageVector = ImageVector.Builder(
    name = "PlaylistAdd",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // 三条横线（x 2..13，线宽 2，行距 5）
        moveTo(2f, 6f); horizontalLineToRelative(11f); verticalLineToRelative(2f); horizontalLineToRelative(-11f); close()
        moveTo(2f, 11f); horizontalLineToRelative(11f); verticalLineToRelative(2f); horizontalLineToRelative(-11f); close()
        moveTo(2f, 16f); horizontalLineToRelative(11f); verticalLineToRelative(2f); horizontalLineToRelative(-11f); close()
        // 右侧加号：中心 (18.5, 13)，横竖各 7×2 / 2×7
        moveTo(15f, 12f); horizontalLineToRelative(7f); verticalLineToRelative(2f); horizontalLineToRelative(-7f); close()
        moveTo(17.5f, 9.5f); horizontalLineToRelative(2f); verticalLineToRelative(7f); horizontalLineToRelative(-2f); close()
    }
}.build()

/**
 * 详情页的分组容器（与设置页 / 我的同一份规格：`surfaceContainer` + `largeIncreased` 圆角）。
 *
 * NOTE: 不用 `surfaceContainerLow`——它在纯黑底上会画成纯黑（实测），容器会看不见。
 */
@Composable
private fun DetailGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** 分组容器里的行间分隔线：从内容左边缘起，与设置页一致。 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 信息块的一行：标签固定占三成，值在右侧自动换行（`FlowRow`，长标签/长值都不会挤坏）。
 *
 * NOTE: 值默认**折成一行**，装不下才给展开。判断不做内容猜测（不按长度、不按标点、更不按
 * 标签文案——标签随内容语言变），直接看**实测排版**：`Text` 在 `maxLines = 1` 下
 * `hasVisualOverflow` 为真，说明这一行放不下。所以站点那条整句的「标题」也是「一行 + 展开」，
 * 而不是被删掉，也不会把卡片撑到一屏放不下。
 * NOTE: 多值的行（类型、标签）每颗值各自一行、靠 `FlowRow` 换行——它们不会被折起来。
 */
@Composable
private fun InfoRow(row: DetailInfoRow, onOpenPath: (path: String, title: String) -> Unit) {
    // key 用标签：同一部影片的信息块里标签唯一，切片时状态自然重置。
    var expanded by rememberSaveable(row.label) { mutableStateOf(false) }
    // 「装不下」是粘性的：展开之后排在多行，`hasVisualOverflow` 会变 false，
    // 那正是要收起的时候——所以展开态改用「行数 > 1」判断。
    var overflowing by remember(row.label) { mutableStateOf(false) }
    val label = if (expanded) {
        stringResource(R.string.detail_collapse)
    } else {
        stringResource(R.string.detail_expand)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                // 只有装不下的行才可点（否则整行都是无效命中区）。
                if (overflowing) {
                    Modifier.clickable(onClickLabel = label) { expanded = !expanded }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            // NOTE: 标签在页面里自带冒号（如「发行日期:」），这里只补一个空格。
            text = row.label + " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.34f),
        )
        // NOTE: 带 path 的值（女优/类型/厂商…）可点，跳到对应的 /latest 列表页。
        FlowRow(
            modifier = Modifier.weight(0.66f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            row.values.forEach { value ->
                // NOTE: 先取成局部变量——跨模块的 data class 属性在 lambda 里不会被智能转换。
                val path = value.path
                Text(
                    text = value.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (path != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if (expanded) {
                            if (result.lineCount > 1) overflowing = true
                        } else {
                            overflowing = result.hasVisualOverflow
                        }
                    },
                    modifier = if (path != null) {
                        Modifier.clickable { onOpenPath(path, value.text) }
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (overflowing) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
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
