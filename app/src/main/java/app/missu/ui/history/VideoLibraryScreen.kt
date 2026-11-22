@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.missu.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.missu.R
import app.missu.data.model.PlaylistSummary
import app.missu.data.store.HistoryEntry
import app.missu.data.store.ListHistoryEntry
import app.missu.ui.common.UiMessage
import app.missu.ui.components.PageTopBar
import app.missu.ui.playlists.PlaylistsContent
import app.missu.ui.playlists.PlaylistsUiState
import app.missu.ui.saved.SavedContent
import app.missu.ui.saved.SavedUiState

@Composable
fun VideoLibraryScreen(
    entries: List<HistoryEntry>,
    listEntries: List<ListHistoryEntry>,
    savedState: SavedUiState,
    playlistsState: PlaylistsUiState,
    createMessage: UiMessage?,
    creating: Boolean,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onOpenList: (ListHistoryEntry) -> Unit,
    onToggleListPin: (ListHistoryEntry) -> Unit,
    onClearVideos: () -> Unit,
    onClearLists: () -> Unit,
    onSavedRetry: () -> Unit,
    onSavedLoadMore: () -> Unit,
    onPlaylistsRetry: () -> Unit,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onCreatePlaylist: (name: String, isPublic: Boolean, onSuccess: () -> Unit) -> Unit,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab) { onTabChange(selectedTab) }

    Column(modifier.fillMaxSize()) {
        PageTopBar(
            title = stringResource(R.string.video_library_title),
            onBack = onBack,
            actions = {
                val canClear = when (selectedTab) {
                    0 -> entries.isNotEmpty()
                    1 -> listEntries.isNotEmpty()
                    else -> false
                }
                if (canClear) {
                    TextButton(onClick = if (selectedTab == 0) onClearVideos else onClearLists) {
                        Text(stringResource(R.string.common_clear))
                    }
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.history_videos_tab)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.history_lists_tab)) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.saved_title)) },
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text(stringResource(R.string.playlists_title)) },
            )
        }

        when (selectedTab) {
            0 -> VideoHistory(entries = entries, onOpen = onOpen)
            1 -> ListHistory(
                entries = listEntries,
                onOpen = onOpenList,
                onTogglePin = onToggleListPin,
            )
            2 -> SavedContent(
                state = savedState,
                onRetry = onSavedRetry,
                onLoadMore = onSavedLoadMore,
                onOpen = onOpen,
            )
            else -> PlaylistsContent(
                state = playlistsState,
                onRetry = onPlaylistsRetry,
                onOpen = onOpenPlaylist,
                createMessage = createMessage,
                creating = creating,
                onCreate = onCreatePlaylist,
            )
        }
    }
}
