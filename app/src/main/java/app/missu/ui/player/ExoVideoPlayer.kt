package app.missu.ui.player

import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.material3.PlayerDefaults
import app.missu.data.store.SettingsStore
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MissavPlayer"
private const val AUTO_HIDE_MS = 3_000L
private const val SEEK_STEP_MS = 10_000L
private const val PROGRESS_REPORT_MS = 5_000L

/** 连击窗口：只有同侧的两次点击才当成一次双击（对齐 legacy 的 `TAP_CHAIN_WINDOW`）。 */
private const val TAP_CHAIN_MS = 350L

/** 累加窗口：同方向连续双击在这个窗口内累加，提示里显示累计值（legacy 的 `SEEK_ACCUMULATE_WINDOW`）。 */
private const val SEEK_ACCUMULATE_MS = 900L

/** 双击 seek 提示的停留时长（legacy 的 `HINT_VISIBLE`）。 */
private const val SEEK_HINT_MS = 700L

/** seek 之后的静默窗口：这段时间内不认播放器报的 paused（legacy 是 600ms）。 */
private const val SEEK_SETTLE_MS = 600L

/**
 * 中间那块「无双击语义」的区域占比（居中，左右各占剩下的部分做 seek）。
 *
 * NOTE: 中间**没有**双击动作，所以单击可以**立即**显隐控件、不用等 350ms 去和双击区分。
 * 控件一出现，第二次点击自然就落在中间的播放/暂停键上——播放暂停因此不需要额外的双击逻辑，
 * 直接复用了那颗按钮（比另开一套「双击中间 = 播放暂停」更简单，也没有误触 seek 的风险）。
 * WARNING: 严格说只有落在**那颗键上**（比这块区域窄）的第二次点击才会暂停；落在块内键外
 * 只是再收一次控件，不会误触 seek。
 */
private const val TAP_MIDDLE_FRACTION = 0.30f

/**
 * 播放器：ExoPlayer + 官方 Material3 控件（[PlayerOverlay]）。
 *
 * 关键取舍（都是真机踩出来的）：
 * - 画面输出用 TextureView 而不是 SurfaceView：SurfaceView 直接画在窗口最上层，Compose 控件会被盖住。
 * - 「点画面切换控件显隐」由 TextureView 自己的 OnTouchListener 接：interop 出来的 View 会把触摸吃掉，
 *   Compose 层的 clickable 收不到事件。
 * - 数据源必须用 OkHttp：媒体主机对 HttpURLConnection 的 TLS 指纹返回 403。
 * - 播放失败必须显示出来：否则界面只会停在 00:00，看不出原因。
 *
 * TODO: 双击左右快进/快退、亮度音量手势、后台播放。
 */
@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    // NOTE: 这个目的地还是不是栈顶。返回时它先变 false，用它在**转场开始那一帧**停音频——
    // 等 onDispose 才 release 要晚整整一个转场，用户会听到"画面已经缩回卡片了，声音还在响"。
    active: Boolean = true,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    initialPositionMs: Long = 0L,
    onPositionReport: ((Long) -> Unit)? = null,
    // NOTE: 第一帧真正出画。上层（详情页）要靠它决定什么时候撤掉封面：
    // 在它之前容器里必须一直有画面，否则"长大到位"的封面会凭空消失一下。
    onFirstFrameRendered: () -> Unit = {},
    // NOTE: 播放失败也要通知上层。上层的封面画在播放器**之上**，不撤掉的话错误层永远被挡住——
    // 用户只会看到一张虚焦封面和一个永远在转的环。
    onPlaybackFailed: () -> Unit = {},
) {
    val context = LocalContext.current

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    // NOTE: 画面比例（宽/高）。TextureView 不像 `PlayerView` 那样自带 `AspectRatioFrameLayout`：
    // 它把解码缓冲**拉伸**到自己的尺寸，16:9 的片子铺进 20:9 的横屏就是横向拉宽（实机可见）。
    // 所以按真实比例给视频层定尺寸、居中，多出来的部分由黑底垫住；0 表示还不知道。
    var videoAspect by remember { mutableStateOf(0f) }
    // NOTE: 倍速是**全局设置**，不是本次播放的临时状态：它跟着偏好走，所以每部片的播放器
    // 建起来就按它设好（见下面 player 的 apply）。1.0x 只是没存过值时的默认。
    val settings = remember(context) { SettingsStore(context.applicationContext) }
    val initialSpeed = remember { settings.playbackSpeed() }
    var speed by remember { mutableStateOf(initialSpeed) }
    // NOTE: 播放器设置面板（当前只有倍速一项）。不叫 speedMenu 了：倍速已经收进设置项里。
    var settingsOpen by remember { mutableStateOf(false) }
    // NOTE: 控件只在第一帧出画之后才允许出现。加载中就把进度条和播放键摆出来，
    // 等于在"还没有东西可控"的时候先给一堆控件——那正是详情页打开时最脏的一帧。
    var ready by remember(url) { mutableStateOf(false) }
    // NOTE: seek 之后的静默窗口（对齐 legacy 的 `seekingRef`）：seek 会让播放器短暂报 paused，
    // 那是加载不是用户暂停，这段时间内不认它，播放键图标才不会闪。
    var seekingUntil by remember { mutableLongStateOf(0L) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(mediaDataSourceFactory()))
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
            // 播放参数挂在 player 实例上：在这里设一次，之后换集/重试都还在。
            .apply { setPlaybackSpeed(initialSpeed) }
    }

    // 观看进度上报：每 5 秒一次 + 退出时一次，交给历史存储。
    // NOTE: 必须声明在 DisposableEffect 之前——它的 onDispose 会用到。
    val reportPosition = rememberUpdatedState<(Long) -> Unit> { position ->
        onPositionReport?.invoke(position)
    }
    // NOTE: 监听器在 DisposableEffect 里只装一次，回调必须走 rememberUpdatedState 才不会拿到旧 lambda。
    val notifyFirstFrame = rememberUpdatedState { onFirstFrameRendered() }
    val notifyPlaybackFailed = rememberUpdatedState { onPlaybackFailed() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                // WARNING: seek 窗口内播放器报的 paused 是加载、不是用户暂停——忽略它，
                // 否则双击快进快退时播放键会闪一下 ▶（对齐 legacy 的 seekingRef）。
                if (!playing && System.currentTimeMillis() < seekingUntil) return
                isPlaying = playing
                // NOTE: 这里**不**点亮控件层。控件显隐只由用户操作驱动（初始可见 + 3 秒自动隐藏
                // + 单击唤出），否则 seek/缓冲引起的 false→true 会把整层控件唤出来。
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // NOTE: 要按像素宽高比折算——变形宽银幕的片子 `width/height` 不是画面比例。
                val width = videoSize.width * videoSize.pixelWidthHeightRatio
                if (videoSize.height > 0 && width > 0f) videoAspect = width / videoSize.height
            }

            override fun onRenderedFirstFrame() {
                ready = true
                notifyFirstFrame.value()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "playback failed: ${error.errorCodeName} / ${error.message}", error)
                errorMessage = "${error.errorCodeName}: ${error.message}"
                notifyPlaybackFailed.value()
            }
        }
        player.addListener(listener)
        onDispose {
            reportPosition.value(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    // NOTE: 自动播放关掉时仍然 `prepare()`——首帧会画出来、控件也会出现，只是停在暂停态，
    // 用户点一下播放键就接着走。不要在这里跳过 prepare：那样连首帧都没有，只剩一张封面。
    val autoplay = remember { settings.autoplay() }
    LaunchedEffect(url) {
        errorMessage = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        // 继续播放：接着上次的位置。
        if (initialPositionMs > 0) player.seekTo(initialPositionMs)
        player.playWhenReady = autoplay
    }

    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_REPORT_MS)
            if (player.isPlaying) reportPosition.value(player.currentPosition)
        }
    }

    // 离开栈顶就静音停走；回到栈顶（预测性返回被取消）再续上。
    var pausedByLeaving by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) {
            if (player.playWhenReady) {
                player.playWhenReady = false
                pausedByLeaving = true
            }
        } else if (pausedByLeaving) {
            pausedByLeaving = false
            player.playWhenReady = true
        }
    }

    // 播放中 3 秒无操作隐藏控件。
    // NOTE: 设置面板开着的时候不隐藏——否则面板的锚点（控件层）会先消失。
    LaunchedEffect(controlsVisible, isPlaying, settingsOpen) {
        if (controlsVisible && isPlaying && !settingsOpen) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    // 双击快进/快退（对齐 legacy）：同侧两次点击在 350ms 内算一次双击；同方向连续双击在 900ms
    // 内累加（提示显示累计值）。单击要延后 350ms 才显隐控件——否则每次双击都会顺带把控件切一次。
    val scope = rememberCoroutineScope()
    var seekHintSeconds by remember { mutableStateOf<Int?>(null) }
    val tapState = remember { TapState() }
    var toggleJob by remember { mutableStateOf<Job?>(null) }
    var hintJob by remember { mutableStateOf<Job?>(null) }

    fun seekStep(direction: Int) {
        val now = System.currentTimeMillis()
        tapState.seekTotal = if (
            tapState.seekDirection == direction && now - tapState.lastSeekAt < SEEK_ACCUMULATE_MS
        ) {
            tapState.seekTotal + SEEK_STEP_SECONDS
        } else {
            SEEK_STEP_SECONDS
        }
        tapState.seekDirection = direction
        tapState.lastSeekAt = now
        seekingUntil = now + SEEK_SETTLE_MS
        if (direction > 0) player.seekForward() else player.seekBack()
        // NOTE: 不再唤出控件层——底边那条进度一直可见，双击快进快退时它直接跳，提示飘一下即可，
        // 观看过程完全不被打断（YouTube 移动端就是这个手感）。
        seekHintSeconds = direction * tapState.seekTotal
        hintJob?.cancel()
        hintJob = scope.launch {
            delay(SEEK_HINT_MS)
            seekHintSeconds = null
            tapState.seekTotal = 0
            tapState.seekDirection = 0
        }
    }

    fun applySpeed(value: Float) {
        player.setPlaybackSpeed(value)
        speed = value
        // 立刻落盘：用户在这里改的其实是「以后都按这个速度放」。
        settings.setPlaybackSpeed(value)
        settingsOpen = false
    }

    fun handleTap(x: Float, width: Float) {
        // NOTE: 设置菜单开着时，点画面只是关菜单（YouTube 的行为），不要顺手把控件也切一次。
        if (settingsOpen) {
            settingsOpen = false
            return
        }
        val edge = width * ((1f - TAP_MIDDLE_FRACTION) / 2f)
        // 中间区：没有双击语义，单击**立即**显隐控件。
        if (x >= edge && x <= width - edge) {
            // 清掉两侧可能挂着的延迟 toggle 与连击链，否则跨区连点会「立即切一次 + 0.35s 后再切一次」。
            toggleJob?.cancel()
            toggleJob = null
            tapState.lastTapAt = 0L
            controlsVisible = !controlsVisible
            return
        }
        // 左右两侧：同一侧 350ms 内两次点击 = 双击 seek；单击延后 350ms 再显隐控件。
        val direction = if (x < width / 2f) -1 else 1
        val now = System.currentTimeMillis()
        val chained = now - tapState.lastTapAt < TAP_CHAIN_MS &&
            tapState.lastTapZone == direction
        tapState.lastTapAt = now
        tapState.lastTapZone = direction
        toggleJob?.cancel()
        if (chained) {
            seekStep(direction)
        } else {
            toggleJob = scope.launch {
                delay(TAP_CHAIN_MS)
                controlsVisible = !controlsVisible
            }
        }
    }

    // NOTE: 黑底是**视频层自己的底**：画面按真实比例居中后多出来的部分就是它（横屏时左右两条黑边），
    // 与页面底色无关。竖屏详情页里那个 16:9 的框因此看起来没变，差别只在横屏全屏。
    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier
                .align(Alignment.Center)
                .then(
                    if (videoAspect > 0.01f) Modifier.aspectRatio(videoAspect) else Modifier.fillMaxSize(),
                ),
            factory = { ctx ->
                TextureView(ctx).apply {
                    player.setVideoTextureView(this)
                }
            },
            onRelease = { texture -> player.clearVideoTextureView(texture) },
        )

        // 点击层：单击显隐控件，双击左右半边快进/快退。放在画面之上、控件之下——
        // 控件自己的按钮（播放键、进度条、全屏…）仍然优先命中。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset -> handleTap(offset.x, size.width.toFloat()) }
                },
        )

        PlayerOverlay(
            player = player,
            visible = ready && controlsVisible,
            isPlaying = isPlaying,
            isFullscreen = isFullscreen,
            onSettingsClick = { settingsOpen = !settingsOpen },
            onToggleFullscreen = onToggleFullscreen,
            seekHintSeconds = seekHintSeconds,
        )

        // 设置面板放在控件层之外：控件被自动隐藏时它仍然存在。
        // NOTE: 全屏用**贴着齿轮的悬浮菜单**，内嵌（16:9 那点高度）仍用底部抽屉。
        // CRITICAL: 全屏绝不能用抽屉：`ModalBottomSheet` 是独立窗口，它一出现系统栏就跟着那个窗口
        // 重新可见——状态栏冒出来、页面重新 inset，画面整块被顶下去（实机可见）。
        if (settingsOpen && !isFullscreen) {
            PlayerSettingsSheet(
                speed = speed,
                onDismiss = { settingsOpen = false },
                onSpeedChange = ::applySpeed,
            )
        }
        AnimatedVisibility(
            visible = settingsOpen && isFullscreen,
            modifier = Modifier.align(Alignment.TopEnd),
            // 从齿轮那一角长出来（transformOrigin 打在右上角），和面板贴着的锚点对得上。
            enter = fadeIn(tween(120)) +
                scaleIn(tween(120), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f)),
            exit = fadeOut(tween(90)),
        ) {
            PlayerSettingsMenu(
                speed = speed,
                onSpeedChange = ::applySpeed,
                // 齿轮下面那颗键的圆底 32dp + 上下留白，面板顶边就落在它下面。
                modifier = Modifier.padding(top = 56.dp, end = 8.dp),
            )
        }

        if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlayerDefaults.ErrorOverlay(player = player)
            }
        }
    }
}

/**
 * 连击与累加的记账。
 *
 * NOTE: 这些值只参与判定、不需要触发重组，所以用一个普通类而不是一堆 `mutableStateOf`
 * （对齐 legacy 的 `lastTap` / `lastTapDirection` / `seekState`）。
 */
private class TapState {
    var lastTapAt = 0L
    var lastTapZone = 0
    var lastSeekAt = 0L
    var seekDirection = 0
    var seekTotal = 0
}
