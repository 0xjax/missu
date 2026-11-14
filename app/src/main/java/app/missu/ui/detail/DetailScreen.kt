@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.videoHero
import coil3.compose.AsyncImage

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
    // NOTE: 封面优先用详情页的 og:image，解析没回来时退回卡片带过来的那张；两者通常是同一张图，
    // 所以共享容器长到位时画面不会变。
    val poster = detail?.thumbnailUrl ?: posterUrl

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
                DetailUiState.Loading -> LoadingHint()

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

/**
 * 播放器框——同时是卡片封面的**共享容器**（container transform 的落点）。
 *
 * NOTE: 它必须在首帧就存在，Loading / Error 也不例外：点卡片时共享元素要有另一侧可长进去，
 * 否则动画直接跳过。解析播放地址的那几百毫秒里，这里铺的是封面而不是黑框。
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
        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = detail?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (playbackUrl != null) {
            ExoVideoPlayer(
                url = playbackUrl,
                modifier = Modifier.fillMaxSize(),
                isFullscreen = fullscreen,
                onToggleFullscreen = onToggleFullscreen,
                initialPositionMs = startPositionMs,
                onPositionReport = onPositionReport,
            )
        } else if (detail != null) {
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
