@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.playlists

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.data.model.PlaylistSummary
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageTopBar

@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    onRetry: () -> Unit,
    onOpen: (PlaylistSummary) -> Unit,
    onBack: () -> Unit,
    createMessage: String? = null,
    onCreate: (name: String, isPublic: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        PageTopBar(title = "我的片单", onBack = onBack)

        if (dialogOpen) {
            CreateDialog(
                message = createMessage,
                onConfirm = { name, isPublic ->
                    onCreate(name, isPublic)
                    dialogOpen = false
                },
                onDismiss = { dialogOpen = false },
            )
        }

        // NOTE: 列表区单独包一层 Box，M3E 的 FloatingActionButtonMenu 才能浮在内容上；
        // weight(1f) 让它占满余下高度，底栏与顶栏各司其职。
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                PlaylistsUiState.Loading -> LoadingHint()

                PlaylistsUiState.Empty -> EmptyHint(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "还没有片单",
                    subtitle = "用右下角的按钮新建第一个片单",
                )

                is PlaylistsUiState.Error -> ErrorHint(state.message, onRetry)

                is PlaylistsUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.playlists, key = { it.path }) { playlist ->
                        PlaylistRow(playlist = playlist, onClick = { onOpen(playlist) })
                    }
                }
            }

            FloatingActionButtonMenu(
                expanded = fabExpanded,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                button = {
                    // NOTE: FAB 默认用 primaryContainer（M3 默认调色板是紫色），与我们的粉色 accent 不搭，
                    // 这里显式取 primary/onPrimary。
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                            contentDescription = if (fabExpanded) "收起" else "更多操作",
                        )
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        dialogOpen = true
                    },
                    text = { Text("新建片单") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        onRetry()
                    },
                    text = { Text("刷新列表") },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** 新建片单：站点只有表单，这里只收「名字 + 是否公开」，token 由数据层去读。 */
@Composable
private fun CreateDialog(
    message: String?,
    onConfirm: (name: String, isPublic: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建片单") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("片单名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                // NOTE: 二元开关用 Switch（M3E：多选/确认才用 Checkbox，设置项用 Switch），
                // 和「编辑片单」页的「公开片单」保持一致。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "公开片单",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, isPublic) }) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/**
 * 一行片单：名字 + 「公开性 · 更新时间」+ 右箭头。
 *
 * NOTE: 每行包一张 `surfaceContainerLow` 的圆角面（M3E 的列表项容器），而不是裸文本堆叠——
 * 片单数量少、行高不齐，容器能把每一行的点击范围画出来。
 */
@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    // NOTE: owner 一定是自己（这是「我的片单」），不显示以免噪音。
    val meta = listOfNotNull(playlist.visibility, playlist.updatedAt?.let { "更新 $it" }).joinToString(" · ")
    val supporting: (@Composable () -> Unit)? = if (meta.isEmpty()) {
        null
    } else {
        { Text(text = meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            // NOTE: 列表项默认容器色取主题的 surface（纯黑），会盖掉分组容器的底色。
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            supportingContent = supporting,
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
