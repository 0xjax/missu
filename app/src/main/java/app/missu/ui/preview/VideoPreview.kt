package app.missu.ui.preview

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.missu.R
import app.missu.data.api.LocalAppDependencies
import app.missu.data.model.VideoSummary
import app.missu.ui.player.mediaDataSourceFactory
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val TAG = "MissuPreview"
private const val FADE_MS = 150
private const val SCRIM_ALPHA = 0.86f
private val CardMaxWidth = 480.dp

/**
 * 长按预览的当前目标。`null` = 没有浮层。
 *
 * NOTE: 全应用只有一个实例（挂在根部），换片不重建宿主——旧播放器随卡片内容卸载时释放。
 * 与 legacy 的 `PreviewProvider` 同一个思路，只是这边用 CompositionLocal 传下去，卡片不必逐层要参数。
 */
@Stable
class VideoPreviewState {
    var current by mutableStateOf<VideoSummary?>(null)
        private set
    var armed by mutableStateOf(false)
        private set
    var gestureActive by mutableStateOf(false)
        private set

    private var onOpenDetail: (() -> Unit)? = null
    private var previewBounds: Rect? = null
    private var gestureStart = Offset.Zero
    private var activationSlop = 0f
    private var moved = false

    fun open(video: VideoSummary, onOpenDetail: () -> Unit) {
        current = video
        this.onOpenDetail = onOpenDetail
        previewBounds = null
        armed = false
        gestureActive = false
        moved = false
    }

    fun beginGesture(position: Offset, activationSlop: Float) {
        gestureStart = position
        this.activationSlop = activationSlop
        gestureActive = true
    }

    fun moveTo(position: Offset) {
        if (!gestureActive) return
        if ((position - gestureStart).getDistance() >= activationSlop) moved = true
        armed = moved && previewBounds?.contains(position) == true
    }

    fun release(position: Offset) {
        moveTo(position)
        val openDetail = armed
        val action = onOpenDetail
        close()
        if (openDetail) action?.invoke()
    }

    fun openDetail() {
        val action = onOpenDetail
        close()
        action?.invoke()
    }

    fun updatePreviewBounds(bounds: Rect) {
        previewBounds = bounds
    }

    fun close() {
        current = null
        onOpenDetail = null
        armed = false
        gestureActive = false
        moved = false
    }
}

/** 卡片长按预览的入口。默认 `null`：没挂宿主的场景（预览、测试）里卡片行为不变。 */
val LocalVideoPreview = staticCompositionLocalOf<VideoPreviewState?> { null }

/**
 * 长按预览浮层：整屏暗底 + 居中的 16:9 预览片（静音、循环、自动播放）+ 标题。
 *
 * 关键取舍：
 * - 预览地址来自卡片 HTML 的 `data-src`（不是按番号拼出来的），没有就只显示封面。
 * - 播放器是**第二个实例**，与详情页那个互不影响；数据源同一个工厂（Referer/UA 缺一不可）。
 * - 用根部同窗口浮层而不是 [androidx.compose.ui.window.Dialog]：新窗口会打断卡片尚未结束的按压，
 *   同窗口才能继续接收移动与抬起，同时仍能盖住底栏和系统栏。
 * - 关闭必须能淡出。浮层直接离开组合就是硬切，所以这里留住最后一个目标，等淡出走完再拿掉。
 */
@Composable
fun VideoPreviewHost(state: VideoPreviewState) {
    var shown by remember { mutableStateOf<VideoSummary?>(null) }
    val transition = remember { MutableTransitionState(false) }
    val open = state.current

    LaunchedEffect(open) {
        if (open != null) {
            shown = open
            transition.targetState = true
        } else {
            transition.targetState = false
            if (shown != null) {
                delay(FADE_MS.toLong())
                shown = null
            }
        }
    }

    val video = shown ?: return
    val close = state::close

    BackHandler(enabled = open != null, onBack = close)
    AnimatedVisibility(
        visibleState = transition,
        enter = fadeIn(tween(FADE_MS)) + scaleIn(initialScale = 0.92f, animationSpec = tween(FADE_MS)),
        exit = fadeOut(tween(FADE_MS)),
    ) {
        PreviewContent(video = video, state = state)
    }
}

@Composable
private fun PreviewContent(video: VideoSummary, state: VideoPreviewState) {
    val closeLabel = stringResource(R.string.preview_close)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
                onClick = state::close,
            )
            // 系统栏（状态栏、手势条）也要压在暗底里，卡片内容再避开它们。
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                // 平板/横屏上不要让预览片铺成整屏：与 legacy 一样限制在 480dp 以内。
                .widthIn(max = CardMaxWidth)
                // NOTE: 标题和提示保持静态点击时的空白行为；进入详情只由预览片触发。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            PreviewMedia(
                video = video,
                activated = state.armed,
                onOpen = state::openDetail,
                onBounds = state::updatePreviewBounds,
            )
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                // NOTE: 浮层在媒体上，用固定白字而不是主题色——主题的 onSurface 在暗底上会偏低。
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(
                    when {
                        state.armed -> R.string.preview_release_to_open
                        state.gestureActive -> R.string.preview_gesture_hint
                        else -> R.string.preview_close_hint
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (state.armed) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * 预览片本体：封面垫底，第一帧出画后再把视频盖上去。
 *
 * NOTE: 没有预览片、或者预览片加载失败，都停在封面上——不会留一个黑框。
 */
@OptIn(UnstableApi::class)
@Composable
private fun PreviewMedia(
    video: VideoSummary,
    activated: Boolean,
    onOpen: () -> Unit,
    onBounds: (Rect) -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) }
            .border(2.dp, if (activated) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clip(shape)
            .background(Color.Black)
            .clickable(onClickLabel = stringResource(R.string.preview_open_detail), onClick = onOpen),
    ) {
        video.thumbnailUrl?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val url = video.previewUrl ?: return@Box
        val context = LocalContext.current
        val httpClient = LocalAppDependencies.current.httpClient
        var ready by remember(url) { mutableStateOf(false) }
        val player = remember(url) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(mediaDataSourceFactory(httpClient)))
                .build()
                .apply {
                    // 与网页上的预览一致：静音、循环、自动播放。
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(player, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                    Lifecycle.Event.ON_START -> player.playWhenReady = true
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                player.release()
            }
        }

        // NOTE: 监听器只装一次，回调里的 snapshot state 写的是同一个 `ready`（remember(url) 重建）。
        DisposableEffect(player, url) {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    ready = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "preview failed: ${error.errorCodeName} / ${error.message}", error)
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }

        LaunchedEffect(player, url) {
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }

        // NOTE: 视频层必须**一直在**（先透明）：`onRenderedFirstFrame` 要等一个 surface 才可能发生，
        // 而"等首帧再建 view"就是死锁——没有 view 就没有首帧，没有首帧就永远不建 view（实测踩过）。
        ContentFrame(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (ready) 1f else 0f),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Crop,
        )
    }
}
