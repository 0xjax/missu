@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.VideoPlaylist
import app.missu.ui.common.text
import app.missu.ui.components.MissuBottomSheet

@Composable
fun PlaylistSheet(
    state: PlaylistUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (VideoPlaylist) -> Unit,
) {
    MissuBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(R.string.detail_add_to_playlist),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            when (state) {
                PlaylistUiState.Hidden -> Unit
                PlaylistUiState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                is PlaylistUiState.Error -> Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = state.message.text(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }

                is PlaylistUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = PlaylistSheetMaxHeight),
                ) {
                    if (state.items.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.detail_no_playlists),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(items = state.items, key = { it.key }) { item ->
                        ListItem(
                            modifier = Modifier.clickable { onToggle(item) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            trailingContent = {
                                Checkbox(
                                    checked = item.added,
                                    onCheckedChange = null,
                                    enabled = state.pendingKey == null || state.pendingKey == item.key,
                                )
                            },
                        ) {
                            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

private val PlaylistSheetMaxHeight = 432.dp
