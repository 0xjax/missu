@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.VideoGridCard

@Composable
fun VideoListScreen(
    title: String,
    state: VideoListUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = title, onBack = onBack)

        PageStateTransition(state = state, modifier = Modifier.fillMaxSize()) { current ->
            when (current) {
                VideoListUiState.Loading -> LoadingHint()

                VideoListUiState.Empty -> EmptyHint(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "这里还没有内容",
                )

                is VideoListUiState.Error -> ErrorHint(current.message, onRetry)

                is VideoListUiState.Content -> LazyVerticalGrid(
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items = current.videos, key = { it.id + it.pageUrl }) { video ->
                        VideoGridCard(video = video, onClick = { onOpen(video.id, video.thumbnailUrl) })
                    }

                    if (current.nextPage != null) {
                        item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                            LoadMoreFooter(
                                loading = current.loadingMore,
                                onLoadMore = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
