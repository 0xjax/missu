package app.missu.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.common.text
import app.missu.ui.components.GroupSurface
import app.missu.ui.components.MissuBottomSheet
import app.missu.ui.components.TooltipIconButton
import app.missu.ui.list.ToolbarOptionsSheet

@Composable
internal fun ContentSettingsButton(
    contentDescription: String,
    content: @Composable ColumnScope.(onDismiss: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    TooltipIconButton(
        icon = Icons.Outlined.Settings,
        contentDescription = contentDescription,
        onClick = { open = true },
    )
    if (open) {
        MissuBottomSheet(
            onDismissRequest = { open = false },
            skipPartiallyExpanded = true,
        ) {
            ToolbarOptionsSheet {
                content { open = false }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
internal fun LiteraturePackSection(
    state: LiteraturePackUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    SectionLabel(stringResource(R.string.settings_literature_pack))
    GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            val progress = if (state.fullBytes > 0) {
                (state.downloadedBytes.toFloat() / state.fullBytes).coerceIn(0f, 1f)
            } else {
                0f
            }
            val percent = (progress * 100).toInt()
            val fullMiB = (state.fullBytes + 1024 * 1024 - 1) / (1024 * 1024)
            val description = when {
                state.downloading -> stringResource(R.string.settings_literature_pack_downloading, percent)
                state.error != null -> stringResource(
                    R.string.settings_literature_pack_error,
                    state.workCount,
                    state.error.text(),
                )
                state.fullActive -> stringResource(R.string.settings_literature_pack_full, state.workCount)
                else -> stringResource(
                    R.string.settings_literature_pack_core,
                    state.workCount,
                    state.fullWorkCount,
                    fullMiB,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = when {
                    state.downloading -> onCancel
                    state.fullActive -> onRemove
                    else -> onDownload
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = when {
                        state.downloading -> stringResource(R.string.settings_literature_pack_cancel_download)
                        state.fullActive -> stringResource(R.string.settings_literature_pack_remove)
                        state.error != null -> stringResource(R.string.settings_literature_pack_retry)
                        else -> stringResource(R.string.settings_literature_pack_download)
                    },
                )
            }
        }
    }
}
