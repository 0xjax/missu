package app.missu.ui.player

import android.util.Log
import android.view.MotionEvent
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.material3.PlayerDefaults
import app.missu.data.api.AndroidCookieJar
import app.missu.data.api.SourceConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

private const val TAG = "MissavPlayer"
private const val AUTO_HIDE_MS = 3_000L
private const val SEEK_STEP_MS = 10_000L
private const val PROGRESS_REPORT_MS = 5_000L

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
    var speed by remember { mutableStateOf(1f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    // NOTE: 控件只在第一帧出画之后才允许出现。加载中就把进度条和播放键摆出来，
    // 等于在"还没有东西可控"的时候先给一堆控件——那正是详情页打开时最脏的一帧。
    var ready by remember(url) { mutableStateOf(false) }

    val player = remember {
        val callFactory = OkHttpClient.Builder()
            .cookieJar(AndroidCookieJar())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val httpFactory = OkHttpDataSource.Factory(callFactory)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to SourceConfig.BASE_URL + "/",
                    "User-Agent" to SourceConfig.DEFAULT_USER_AGENT,
                ),
            )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
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
                isPlaying = playing
                if (playing) controlsVisible = true
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

    LaunchedEffect(url) {
        errorMessage = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        // 继续播放：接着上次的位置。
        if (initialPositionMs > 0) player.seekTo(initialPositionMs)
        player.playWhenReady = true
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
    // NOTE: 倍速菜单开着的时候不隐藏——否则菜单会跟着控件一起被销毁。
    LaunchedEffect(controlsVisible, isPlaying, speedMenuOpen) {
        if (controlsVisible && isPlaying && !speedMenuOpen) {
            delay(AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    val toggleControls = rememberUpdatedState {
        controlsVisible = !controlsVisible
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            toggleControls.value()
                        }
                        true
                    }
                    player.setVideoTextureView(this)
                }
            },
            onRelease = { texture -> player.clearVideoTextureView(texture) },
        )

        PlayerOverlay(
            player = player,
            visible = ready && controlsVisible,
            speed = speed,
            isFullscreen = isFullscreen,
            onSpeedClick = { speedMenuOpen = true },
            onToggleFullscreen = onToggleFullscreen,
        )

        // 菜单本体放在控件层之外：控件被自动隐藏时它仍然存在。
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            SpeedMenu(
                expanded = speedMenuOpen,
                speed = speed,
                onDismiss = { speedMenuOpen = false },
                onSpeedChange = { value ->
                    player.setPlaybackSpeed(value)
                    speed = value
                    speedMenuOpen = false
                },
            )
        }

        if (errorMessage != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlayerDefaults.ErrorOverlay(player = player)
            }
        }
    }
}
