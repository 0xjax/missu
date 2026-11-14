@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.missu.data.model.DetailInfoRow
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist
import app.missu.ui.player.ExoVideoPlayer
import app.missu.ui.player.FullscreenEffect
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.videoHero
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // NOTE: 共享容器的 key 与卡片一侧一致（路由里的 id），详情数据还没回来时也拿得到。
    videoId: String = "",
    // NOTE: 卡片封面。详情解析出来之前先用它铺满播放器框——点卡片时那个框就是从这里长过来的。
    posterUrl: String? = null,
    message: String? = null,
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

    val detail = (state as? DetailUiState.Content)?.detail
    // NOTE: 卡片带过来的那张优先，而不是详情页的 og:image。理由有两个：它和卡片上看到的
    // 是同一张（容器长到位时画面不会变），而且它已经在 Coil 的内存缓存里（不会从纯色淡入）。
    val poster = posterUrl ?: detail?.thumbnailUrl

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!fullscreen) {
            PageTopBar(title = "", onBack = onBack)
        }

        DetailHero(
            state = state,
            poster = poster,
            heroKey = videoId.ifEmpty { detail?.id.orEmpty() },
            fullscreen = fullscreen,
            startPositionMs = startPositionMs,
            onPositionReport = onPositionReport,
            onToggleFullscreen = { fullscreen = !fullscreen },
        )

        if (!fullscreen) {
            when (state) {
                // NOTE: 加载态不在正文里放转圈——进度统一收在播放器框里（封面 + 延迟出现的
                // LoadingIndicator），否则页面上会同时有两个"正在加载"。
                DetailUiState.Loading -> Unit

                is DetailUiState.Error -> ErrorHint(state.message, onRetry)

                is DetailUiState.Content -> DetailBody(
                    detail = state.detail,
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

/** 视频出画时封面淡出的时长；与控件出现是同一段，看起来像「画面显影」。 */
private const val POSTER_FADE_MS = 200

/** 转圈延迟出现的时长：快的时候（内存缓存 / 秒开）不该闪一下转圈。 */
private const val PROGRESS_DELAY_MS = 600L

/**
 * 播放器框——同时是卡片封面的**共享容器**（container transform 的落点）。
 *
 * 这里也是「静帧 → 动态画面」的接缝，三件事必须按顺序发生，否则接缝会露出来：
 * 1. 容器长大到位时，里面还是**同一张封面**（卡片那张，从内存缓存来）；
 * 2. 视频第一帧真正出画之前，封面一直在；中途容器里不会空一下，也不会先出现控件；
 * 3. 第一帧出画 → 封面 200ms 淡出（画面在下面显影），控件同时出现，之后 3 秒无操作自动隐藏。
 *
 * NOTE: 封面画在播放器**之上**（含控件层之上），所以过渡只能是「封面淡出露出画面」，
 * 而不可能是「画面替换封面」那种硬切。
 * NOTE: 转圈延迟 600ms 才出现——秒开时不该闪一下。
 * NOTE: 它必须在首帧就存在，Loading / Error 也不例外：点卡片时共享元素要有另一侧可长进去。
 * NOTE: 播放器本身固定在这一格（不进 LazyColumn），切全屏只是换尺寸、不会离开组合，
 * ExoPlayer 实例与播放进度都能保住。
 */
@Composable
private fun DetailHero(
    state: DetailUiState,
    poster: String?,
    heroKey: String,
    fullscreen: Boolean,
    startPositionMs: Long,
    onPositionReport: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val detail = (state as? DetailUiState.Content)?.detail
    val playbackUrl = detail?.playbackUrl

    // NOTE: 换一集（playbackUrl 变）就重置，否则新视频会继承上一集的"已出画"状态。
    var firstFrame by remember(playbackUrl) { mutableStateOf(false) }
    val posterAlpha by animateFloatAsState(
        targetValue = if (firstFrame) 0f else 1f,
        animationSpec = tween(durationMillis = POSTER_FADE_MS),
        label = "posterFade",
    )

    // 还在等：要么详情没回来，要么播放地址有了但视频还没出画。
    val pending = detail == null || (playbackUrl != null && !firstFrame)
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(pending) {
        if (!pending) {
            showProgress = false
            return@LaunchedEffect
        }
        delay(PROGRESS_DELAY_MS)
        showProgress = true
    }

    Box(
        modifier = (if (fullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        })
            .videoHero(heroKey, RectangleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (playbackUrl != null) {
            ExoVideoPlayer(
                url = playbackUrl,
                modifier = Modifier.fillMaxSize(),
                isFullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                initialPositionMs = startPositionMs,
                onPositionReport = onPositionReport,
                onFirstFrameRendered = { firstFrame = true },
            )
        }

        // 封面盖在播放器之上：出画前它是容器内容，出画时它淡出。
        if (poster != null && posterAlpha > 0.01f) {
            AsyncImage(
                model = poster,
                contentDescription = detail?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(posterAlpha),
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
                text = "暂时无法提取播放地址",
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
 * 播放器下面的一切：标题 / 信息行 / 简介，以及底部的收藏与片单动作。
 *
 * NOTE: 播放器不在这里——它由 [DetailHero] 固定在页首，本函数只负责剩余内容。
 */
@Composable
private fun ColumnScope.DetailBody(
    detail: VideoDetail,
    message: String?,
    onToggleSaved: () -> Unit,
    playlists: PlaylistUiState,
    onOpenPlaylists: () -> Unit,
    onClosePlaylists: () -> Unit,
    onTogglePlaylist: (VideoPlaylist) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item(key = "meta") {
            Text(
                text = listOfNotNull(detail.duration, detail.releaseDate).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // NOTE: 收藏 / 片单两个动作搬到页面底部的 FlexibleBottomAppBar 里（SplitButton：
        // 左半是主操作「收藏」，右半是「加入片单」），内容区不再被一行按钮打断。
        message?.let { text ->
            item(key = "message") {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        items(detail.info, key = { row -> row.label }) { row ->
            InfoRow(row = row, onOpenPath = onOpenPath)
        }

        detail.description?.let { description ->
            item(key = "description") {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    // NOTE: M3E 的底栏动作区：左半是主操作（收藏），右半是次要入口（加入片单）。
    // NOTE: 收藏状态来自 /api/items/{id}/view；401 会先自动重登一次（见 withAuthRetry）。
    FlexibleBottomAppBar {
        SplitButtonLayout(
            modifier = Modifier.weight(1f),
            leadingButton = {
                SplitButtonDefaults.LeadingButton(onClick = onToggleSaved) {
                    Text(if (detail.saved) "已收藏" else "收藏")
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(onClick = onOpenPlaylists) {
                    Icon(Icons.Filled.Add, contentDescription = "加入片单")
                }
            },
        )
    }

    if (playlists !is PlaylistUiState.Hidden) {
        PlaylistSheet(state = playlists, onDismiss = onClosePlaylists, onToggle = onTogglePlaylist)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "加入片单",
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
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )

                is PlaylistUiState.Content -> {
                    if (state.items.isEmpty()) {
                        Text(
                            text = "还没有片单，去网页端新建一个吧",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    state.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = item.added,
                                onCheckedChange = { onToggle(item) },
                                enabled = state.pendingKey == null || state.pendingKey == item.key,
                            )
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun InfoRow(row: DetailInfoRow, onOpenPath: (path: String, title: String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            // NOTE: 标签在页面里自带冒号（如「发行日期:」），这里只补一个空格。
            text = row.label + " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // NOTE: 带 path 的值（女优/类型/厂商…）可点，跳到对应的 /latest 列表页。
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.values.forEach { value ->
                // NOTE: 先取成局部变量——跨模块的 data class 属性在 lambda 里不会被智能转换。
                val path = value.path
                Text(
                    text = value.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (path != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = if (path != null) {
                        Modifier.clickable { onOpenPath(path, value.text) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
