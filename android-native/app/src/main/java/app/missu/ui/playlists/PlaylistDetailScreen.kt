package app.missu.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.missu.data.model.VideoSummary
import app.missu.ui.components.VideoGridCard

/**
 * 片单详情：标题 + 影片网格，页头「整理」打开后才在卡片上显示「移除」。
 *
 * NOTE: 移除入口不常驻——常驻的角标会压住封面，也容易误触。按 DESIGN.md 的做法做成
 * 「整理」/「完成」二态 + 二次确认对话框（站点自己也会 confirm），不做滑动删除、不藏进长按。
 * NOTE: 管理模式里点卡片不再进详情，避免想移除却跳走了。
 * NOTE: 入口叫「整理」而不是「管理」——页头右边是「编辑」（改片单名/删片单），两个词要拉开距离。
 */
@Composable
fun PlaylistDetailScreen(
    fallbackTitle: String,
    state: PlaylistDetailUiState,
    message: String?,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onRemove: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val content = state as? PlaylistDetailUiState.Content
    val title = content?.name ?: fallbackTitle
    var pendingRemove by remember { mutableStateOf<VideoSummary?>(null) }
    // NOTE: 用 saveable —— 轮屏或系统回收后回来仍停在管理模式，不会让入口突然消失。
    var managing by rememberSaveable { mutableStateOf(false) }
    // 列表空了就没有可移除的东西，管理模式自动退出。
    if (content == null && managing) managing = false

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            if (content != null) {
                TextButton(onClick = { managing = !managing }) {
                    Text(if (managing) "完成" else "整理")
                }
            }
            TextButton(onClick = onEdit) { Text("编辑") }
        }

        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        when (state) {
            PlaylistDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            PlaylistDetailUiState.Empty -> CenteredHint("这个片单还是空的")

            is PlaylistDetailUiState.Error -> CenteredHint(state.message, onRetry)

            is PlaylistDetailUiState.Content -> LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items = state.videos, key = { it.id + it.pageUrl }) { video ->
                    Box {
                        VideoGridCard(
                            video = video,
                            onClick = { if (!managing) onOpen(video.id) },
                        )
                        if (managing) {
                            RemoveButton(
                                removing = state.removingId == video.id,
                                onClick = { pendingRemove = video },
                                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    pendingRemove?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("从片单移除？") },
            text = { Text("《${video.title}》会从「$title」里移除，影片本身不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemove = null
                    onRemove(video.id)
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 封面右上角的移除入口。
 * NOTE: 自绘一个半透明圆点而不是用 IconButton——默认的图标内边距与最小触控尺寸会把封面压花，
 * 这里的点击区域靠 padding 撑到 26dp，视觉上只占很小一块。
 */
@Composable
private fun RemoveButton(removing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xCC000000))
            .clickable(enabled = !removing, onClick = onClick)
            .semantics { contentDescription = "从片单移除" },
        contentAlignment = Alignment.Center,
    ) {
        if (removing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
        } else {
            Text("×", color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
private fun CenteredHint(text: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
    }
}
