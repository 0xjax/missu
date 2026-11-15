package app.missu.ui.preview

import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.missu.R
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

    fun open(video: VideoSummary) {
        current = video
    }

    fun close() {
        current = null
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
 * - 用 [Dialog] 而不是页面内的 Box：它能盖住底栏与系统栏，也自带返回键关闭。
 * - 关闭必须能淡出。Dialog 从组合里消失就是硬切，所以这里留住最后一个目标，等淡出走完再拿掉。
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

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            // 整屏铺满，底由我们自己画——窗口自带的那层暗底盖不到系统栏，留着会双份叠黑。
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            scrimAlpha = 0f,
        ),
    ) {
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(tween(FADE_MS)) + scaleIn(initialScale = 0.92f, animationSpec = tween(FADE_MS)),
            exit = fadeOut(tween(FADE_MS)),
        ) {
            PreviewContent(video = video, onClose = close)
        }
    }
}

@Composable
private fun PreviewContent(video: VideoSummary, onClose: () -> Unit) {
    val closeLabel = stringResource(R.string.preview_close)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
                onClick = onClose,
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
                // NOTE: 空 lambda 用来吞掉落在卡片上的点击——否则点视频会把整个浮层关掉。
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            PreviewMedia(video)
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
                text = stringResource(R.string.preview_close_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.6f),
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
private fun PreviewMedia(video: VideoSummary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black),
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
        var ready by remember(url) { mutableStateOf(false) }
        val player = remember(url) {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(mediaDataSourceFactory()))
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
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (ready) 1f else 0f),
            factory = { ctx ->
                TextureView(ctx).apply { player.setVideoTextureView(this) }
            },
            onRelease = { texture -> player.clearVideoTextureView(texture) },
        )
    }
}
