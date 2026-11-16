package app.missu.ui.detail

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.missu.R
import app.missu.ui.components.LocalNavAnimatedVisibilityScope
import app.missu.ui.components.videoHero
import app.missu.ui.player.ExoVideoPlayer
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val POSTER_FADE_MS = 200
private const val PROGRESS_DELAY_MS = 600L
private const val DEFOCUS_DELAY_MS = 300L
private const val DEFOCUS_RAMP_MS = 1500
private const val POSTER_BLUR_DP = 12
private const val POSTER_SCRIM_ALPHA = 0.22f

/** 静态封面独占共享转场；播放器只负责画面与音频，不参与容器动画。 */
@Composable
fun DetailHero(
    state: DetailUiState,
    poster: String?,
    heroKey: String,
    isCurrent: Boolean,
    fullscreen: Boolean,
    startPositionMs: Long,
    onPositionReport: (Long) -> Unit,
    onPlayerAvailable: (Player?) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    dragProgress: Float = 0f,
) {
    val detail = (state as? DetailUiState.Content)?.detail
    val playbackUrl = detail?.playbackUrl

    // CRITICAL: 状态只能按 heroKey 取 remember；playbackUrl 到达时重建会让正在运行的动画失效。
    var revealed by remember(heroKey) { mutableStateOf(false) }
    val posterAlpha by animateFloatAsState(
        targetValue = if (revealed) 0f else 1f,
        animationSpec = tween(durationMillis = POSTER_FADE_MS),
        label = "posterFade",
    )

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

    // 首帧到达时冻住当前虚化值，让封面整体淡出，不先闪回清晰。
    val defocus = remember(heroKey) { Animatable(0f) }
    LaunchedEffect(pending) {
        if (pending) {
            delay(DEFOCUS_DELAY_MS)
            defocus.animateTo(1f, tween(durationMillis = DEFOCUS_RAMP_MS))
        } else if (playbackUrl == null) {
            defocus.animateTo(0f, tween(durationMillis = DEFOCUS_RAMP_MS))
        }
    }

    // NOTE: 返回直接使用清晰封面；视频层同时隐去，不再先缩小视频、再播放一次封面转场。
    val leaving = !isCurrent ||
        LocalNavAnimatedVisibilityScope.current?.transition?.targetState == EnterExitState.PostExit
    val visiblePosterAlpha = if (leaving) 1f else posterAlpha
    val visibleDefocus = if (leaving) 0f else defocus.value
    val sharpAlpha = visiblePosterAlpha * (1f - visibleDefocus)
    val softAlpha = visiblePosterAlpha * visibleDefocus

    Box(
        modifier = (if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        })
            .then(modifier)
            .graphicsLayer {
                val scale = if (fullscreen) 1f - 0.12f * dragProgress else 1f + 0.35f * dragProgress
                scaleX = scale
                scaleY = scale
                translationY = (if (fullscreen) 64.dp else -24.dp).toPx() * dragProgress
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (playbackUrl != null) {
            ExoVideoPlayer(
                url = playbackUrl,
                modifier = Modifier.fillMaxSize().alpha(if (leaving) 0f else 1f),
                active = isCurrent,
                isFullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                initialPositionMs = startPositionMs,
                onPositionReport = onPositionReport,
                onPlayerAvailable = onPlayerAvailable,
                onFirstFrameRendered = { revealed = true },
                onPlaybackFailed = { revealed = true },
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .videoHero(heroKey, RectangleShape)
                .then(
                    if (poster == null) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    } else {
                        Modifier
                    },
                ),
        ) {
            // 两层静态图交叉淡入，避免逐帧重建 RenderEffect。
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
            // CRITICAL: 压暗必须乘封面可见度，否则封面消失后黑层仍会盖住视频和控件。
            if (visibleDefocus > 0.01f && visiblePosterAlpha > 0.01f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = POSTER_SCRIM_ALPHA * visibleDefocus * visiblePosterAlpha)),
                )
            }
        }

        if (showProgress && !leaving) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White,
                    trackColor = Color(0x40FFFFFF),
                )
            }
        }

        if (playbackUrl == null && detail != null) {
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
