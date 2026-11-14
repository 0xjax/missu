@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.missu.data.model.DetailInfoRow
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist
import app.missu.ui.player.ExoVideoPlayer
import app.missu.ui.player.FullscreenEffect
import app.missu.ui.components.PageTopBar

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!fullscreen) {
            PageTopBar(title = "", onBack = onBack)
        }

        when (state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            is DetailUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
            }

            is DetailUiState.Content -> DetailContent(
                detail = state.detail,
                message = message,
                onToggleSaved = onToggleSaved,
                startPositionMs = startPositionMs,
                onPositionReport = onPositionReport,
                playlists = playlists,
                onOpenPlaylists = onOpenPlaylists,
                onClosePlaylists = onClosePlaylists,
                onTogglePlaylist = onTogglePlaylist,
                onOpenPath = onOpenPath,
                fullscreen = fullscreen,
                onToggleFullscreen = { fullscreen = !fullscreen },
            )
        }
    }
}

/**
 * 播放器固定放在 Column 的第一格：切全屏时只是换尺寸，**不会离开 composition**，
 * 所以 ExoPlayer 实例和播放进度都能保住（放进 LazyColumn 里就会被回收重建）。
 */
@Composable
private fun ColumnScope.DetailContent(
    detail: VideoDetail,
    message: String?,
    onToggleSaved: () -> Unit,
    startPositionMs: Long,
    onPositionReport: (Long) -> Unit,
    playlists: PlaylistUiState,
    onOpenPlaylists: () -> Unit,
    onClosePlaylists: () -> Unit,
    onTogglePlaylist: (VideoPlaylist) -> Unit,
    onOpenPath: (path: String, title: String) -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    val playbackUrl = detail.playbackUrl
    if (playbackUrl != null) {
        ExoVideoPlayer(
            url = playbackUrl,
            modifier = if (fullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            },
            isFullscreen = fullscreen,
            onToggleFullscreen = onToggleFullscreen,
            initialPositionMs = startPositionMs,
            onPositionReport = onPositionReport,
        )
    } else if (!fullscreen) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center,
        ) {
            Text("暂时无法提取播放地址", style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (fullscreen) return

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

        item(key = "actions") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // NOTE: 收藏状态来自 /api/items/{id}/view；401 会先自动重登一次（见 withAuthRetry）。
                Button(onClick = onToggleSaved) {
                    Text(if (detail.saved) "已收藏" else "收藏")
                }
                TextButton(onClick = onOpenPlaylists) { Text("片单") }
                message?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
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
