@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.playlists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageTopBar

/**
 * 编辑片单：名称 / 描述 / 是否公开 + 删除（destructive，二次确认）。
 *
 * NOTE: 删除入口按 DESIGN.md 放在片单相关页面的页头/尾部，列表页不放破坏性入口。
 * NOTE: 视觉上把「保存」做成整宽主行动，删除压到分隔线以下并只用 error 文字色——
 * 破坏性动作不能和主行动同权重（M3E：颜色与形状表达层级）。
 */
@Composable
fun PlaylistEditScreen(
    state: PlaylistEditUiState,
    message: String?,
    busy: Boolean,
    onSave: (name: String, description: String, isPublic: Boolean) -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        PageTopBar(title = "编辑片单", onBack = onBack)

        when (state) {
            PlaylistEditUiState.Loading -> LoadingHint()

            is PlaylistEditUiState.Error -> ErrorHint(state.message, onRetry)

            is PlaylistEditUiState.Ready -> EditForm(
                initial = state,
                message = message,
                busy = busy,
                onSave = onSave,
                onDeleteRequest = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这个片单？") },
            text = { Text("删除后无法恢复，片单里的影片不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EditForm(
    initial: PlaylistEditUiState.Ready,
    message: String?,
    busy: Boolean,
    onSave: (name: String, description: String, isPublic: Boolean) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    // NOTE: key 用初始值，页面数据到达后重建输入框（否则会一直显示空）。
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var isPublic by remember(initial) { mutableStateOf(initial.isPublic) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("片单名") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("描述（可选）") },
            minLines = 2,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "公开片单",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = isPublic, onCheckedChange = { isPublic = it }, enabled = !busy)
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSave(name, description, isPublic) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "保存中…" else "保存")
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TextButton(
            onClick = onDeleteRequest,
            enabled = !busy,
        ) {
            Text(
                text = "删除片单",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
