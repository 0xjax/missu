@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.playlists

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.data.model.PlaylistSummary
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
                PlaylistsUiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                PlaylistsUiState.Empty -> CenteredHint("还没有片单")

                is PlaylistsUiState.Error -> CenteredHint(state.message, onRetry)

                is PlaylistsUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
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
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPublic, onCheckedChange = { isPublic = it })
                    Text("公开片单", modifier = Modifier.padding(start = 8.dp))
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

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // NOTE: owner 一定是自己（这是「我的片单」），不显示以免噪音。
        val meta = listOfNotNull(playlist.visibility, playlist.updatedAt?.let { "更新 $it" })
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
