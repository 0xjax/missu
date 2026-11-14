@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package app.missu.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
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
import androidx.compose.ui.unit.dp
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.VideoGridCard

/**
 * 片单详情：标题 + 影片网格，页头「整理」进入批量选择模式。
 *
 * NOTE: 整理模式用 M3E 的 [HorizontalFloatingToolbar] 承载批量动作（全选 / 移出所选），
 * 动作本身是一组连体 [ButtonGroup]——窄屏放不下时由组件自己收进溢出菜单，不用我们算宽度。
 * NOTE: 点卡片在整理模式下是「选中/取消」，不再进详情，避免想移除却跳走。
 * NOTE: 破坏性操作仍弹一次确认（站点自己也会 confirm），不做滑动删除、不藏进长按。
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
    onRemove: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val content = state as? PlaylistDetailUiState.Content
    val title = content?.name ?: fallbackTitle
    // NOTE: 用 List 而不是 Set —— rememberSaveable 能直接存 ArrayList，Set 不行。
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pendingRemove by remember { mutableStateOf(emptyList<String>()) }
    var managing by rememberSaveable { mutableStateOf(false) }
    // 列表空了就没有可整理的东西，整理模式自动退出。
    if (content == null && managing) managing = false

    val removing = content?.removingIds.orEmpty()
    val allSelected = content != null && content.videos.isNotEmpty() &&
        selected.size == content.videos.size

    fun enterManaging() {
        managing = true
    }

    fun exitManaging() {
        managing = false
        selected = emptyList()
    }

    // NOTE: containerColor 是 (进度) -> Color 的普通 lambda（不是 @Composable），
    // 所以主题色要先在组合作用域里读出来再捕获。
    val fabContainer = MaterialTheme.colorScheme.primary
    val onFabContainer = MaterialTheme.colorScheme.onPrimary

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageTopBar(
                title = title,
                onBack = onBack,
                // NOTE: 「整理」不再占顶栏动作位——它是个开关，交给右下角的 ToggleFAB（M3E 里
                // 双态操作用会 morph 的 ToggleFloatingActionButton）。顶栏只留「编辑」。
                actions = { TextButton(onClick = onEdit) { Text("编辑") } },
            )

            message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            when (state) {
                PlaylistDetailUiState.Loading -> LoadingHint()

                PlaylistDetailUiState.Empty -> EmptyHint(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "这个片单还是空的",
                    subtitle = "在详情页点「加入片单」，影片就会出现在这里",
                )

                is PlaylistDetailUiState.Error -> ErrorHint(state.message, onRetry)

                is PlaylistDetailUiState.Content -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(2),
                    // NOTE: 整理模式下底部要给浮动工具栏留出空间，否则最后一行被它压住。
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        // NOTE: 底部留白：整理模式给浮动工具条让位，平时给右下角的整理 FAB 让位。
                        bottom = if (managing) 120.dp else 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items = state.videos, key = { it.id + it.pageUrl }) { video ->
                        val toggle = {
                            selected = if (video.id in selected) {
                                selected - video.id
                            } else {
                                selected + video.id
                            }
                        }
                        Box {
                            VideoGridCard(
                                video = video,
                                onClick = { if (managing) toggle() else onOpen(video.id) },
                            )
                            if (managing) {
                                SelectionBadge(
                                    selected = video.id in selected,
                                    removing = video.id in removing,
                                    onClick = toggle,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (managing && content != null) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                // NOTE: 少见动作交给 AppBarRow 当溢出容器——它是 FloatingToolbar 的 trailingContent
                // 标准搭档：放不下时自己收进溢出菜单，不用我们算宽度。
                trailingContent = {
                    AppBarRow {
                        clickableItem(
                            onClick = { selected = emptyList() },
                            icon = { Icon(Icons.Filled.Clear, contentDescription = null) },
                            label = "取消选择",
                            enabled = selected.isNotEmpty() && removing.isEmpty(),
                        )
                        clickableItem(
                            onClick = { exitManaging() },
                            icon = { Icon(Icons.Filled.Done, contentDescription = null) },
                            label = "完成",
                        )
                    }
                },
            ) {
                ButtonGroup(
                    overflowIndicator = { menuState ->
                        ButtonGroupDefaults.OverflowIndicator(menuState)
                    },
                ) {
                    clickableItem(
                        onClick = {
                            selected = if (allSelected) emptyList() else content.videos.map { it.id }
                        },
                        label = if (allSelected) "取消全选" else "全选",
                    )
                    clickableItem(
                        onClick = { if (selected.isNotEmpty()) pendingRemove = selected },
                        label = if (selected.isEmpty()) "移出影片" else "移出 ${selected.size} 部",
                        enabled = selected.isNotEmpty() && removing.isEmpty(),
                    )
                }
            }
        } else if (content != null) {
            // 未进入整理模式：右下角只有一个「整理」开关（morph 成 ×）。
            // WARNING: align 必须挂在外面这层 Box 上——直接挂在 ToggleFloatingActionButton 上时
            // 它的内部布局会吞掉 parent data，结果被摆到左上角（实测踩过）。
            Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
                ToggleFloatingActionButton(
                    checked = false,
                    onCheckedChange = { if (it) enterManaging() },
                    containerColor = { fabContainer },
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "整理",
                        tint = onFabContainer,
                    )
                }
            }
        }
    }

    if (pendingRemove.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingRemove = emptyList() },
            title = { Text("移出所选影片？") },
            text = { Text("会把选中的 ${pendingRemove.size} 部从「$title」里移除，影片本身不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = pendingRemove
                    pendingRemove = emptyList()
                    selected = emptyList()
                    onRemove(ids)
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = emptyList() }) { Text("取消") }
            },
        )
    }
}

/** 整理模式下的选中标记：未选是空心点，已选中是 accent 实心 + 对勾，移除中是 M3E 转圈。 */
@Composable
private fun SelectionBadge(
    selected: Boolean,
    removing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xCC000000))
            .clickable(enabled = !removing, onClick = onClick)
            .semantics { contentDescription = if (selected) "已选中" else "未选中" },
        contentAlignment = Alignment.Center,
    ) {
        when {
            removing -> LoadingIndicator(modifier = Modifier.size(16.dp), color = Color.White)
            selected -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            else -> Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF)),
            )
        }
    }
}
