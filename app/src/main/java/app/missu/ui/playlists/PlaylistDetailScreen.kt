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
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.common.UiMessage
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageStateTransition
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
    message: UiMessage?,
    onRetry: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onRemove: (List<String>) -> Unit = {},
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

    // NOTE: 触觉只确认「状态改变」，不用在导航与滚动上（见 DESIGN.md「Motion」）。进入/退出整理
    // 改的是整页的交互语义（点卡片从「看详情」变成「选中」），而那一下的变化在屏幕底部与顶栏，
    // 手指还在卡片上——触觉在这里是唯一即时的确认。
    val haptics = LocalHapticFeedback.current

    fun enterManaging() {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
        managing = true
    }

    fun exitManaging() {
        haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
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
                actions = { TextButton(onClick = onEdit) { Text(stringResource(R.string.playlist_edit)) } },
            )

            message?.let { notice ->
                Text(
                    text = notice.text(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            PageStateTransition(state = state, modifier = Modifier.fillMaxSize()) { current ->
                when (current) {
                    PlaylistDetailUiState.Loading -> LoadingHint()

                    PlaylistDetailUiState.Empty -> EmptyHint(
                        icon = Icons.AutoMirrored.Outlined.List,
                        title = stringResource(R.string.playlist_empty_title),
                        subtitle = stringResource(R.string.playlist_empty_subtitle),
                    )

                    is PlaylistDetailUiState.Error -> ErrorHint(current.message.text(), onRetry)

                    is PlaylistDetailUiState.Content -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(LocalVideoListColumns.current),
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
                    items(items = current.videos, key = { it.id + it.pageUrl }) { video ->
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
                                onClick = { if (managing) toggle() else onOpen(video.id, video.thumbnailUrl) },
                                // 整理模式下长按预览会和「选中」打架。
                                previewOnLongPress = !managing,
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
        }

        if (managing && content != null) {
            // NOTE: `clickableItem(label = …)` 收的是普通 String，不是 @Composable 槽位，
            // 所以文案必须在组合作用域里先取好。
            val deselectLabel = stringResource(R.string.playlist_deselect)
            val doneLabel = stringResource(R.string.common_done)
            val selectAllLabel = if (allSelected) {
                stringResource(R.string.playlist_unselect_all)
            } else {
                stringResource(R.string.playlist_select_all)
            }
            val removeLabel = if (selected.isEmpty()) {
                stringResource(R.string.playlist_remove)
            } else {
                stringResource(R.string.playlist_remove_n, selected.size)
            }

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
                            icon = { Icon(Icons.Outlined.Clear, contentDescription = null) },
                            label = deselectLabel,
                            enabled = selected.isNotEmpty() && removing.isEmpty(),
                        )
                        clickableItem(
                            onClick = { exitManaging() },
                            icon = { Icon(Icons.Outlined.Done, contentDescription = null) },
                            label = doneLabel,
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
                        label = selectAllLabel,
                    )
                    clickableItem(
                        onClick = { if (selected.isNotEmpty()) pendingRemove = selected },
                        label = removeLabel,
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
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.playlist_manage),
                        tint = onFabContainer,
                    )
                }
            }
        }
    }

    if (pendingRemove.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingRemove = emptyList() },
            title = { Text(stringResource(R.string.playlist_remove_dialog_title)) },
            text = { Text(pluralStringResource(R.plurals.playlist_remove_dialog_body, pendingRemove.size, pendingRemove.size, title)) },
            confirmButton = {
                TextButton(onClick = {
                    val ids = pendingRemove
                    pendingRemove = emptyList()
                    selected = emptyList()
                    onRemove(ids)
                }) { Text(stringResource(R.string.playlist_remove_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = emptyList() }) { Text(stringResource(R.string.common_cancel)) }
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
    val badgeDescription = stringResource(if (selected) R.string.playlist_selected else R.string.playlist_unselected)
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xCC000000))
            .clickable(enabled = !removing, onClick = onClick)
            .semantics { contentDescription = badgeDescription },
        contentAlignment = Alignment.Center,
    ) {
        when {
            removing -> LoadingIndicator(modifier = Modifier.size(16.dp), color = Color.White)
            selected -> Icon(
                Icons.Outlined.Check,
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
