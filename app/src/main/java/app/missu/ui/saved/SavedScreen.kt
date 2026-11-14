@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.saved

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
import androidx.compose.material.icons.filled.Favorite
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
fun SavedScreen(
    state: SavedUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = "我的收藏", onBack = onBack)

        PageStateTransition(state = state, modifier = Modifier.fillMaxSize()) { current ->
            when (current) {
                SavedUiState.Loading -> LoadingHint()

                SavedUiState.Empty -> EmptyHint(
                    icon = Icons.Filled.Favorite,
                    title = "还没有收藏",
                    subtitle = "在详情页点一下「收藏」，之后就能从这里直接打开",
                )

                is SavedUiState.Error -> ErrorHint(current.message, onRetry)

                is SavedUiState.Content -> LazyVerticalGrid(
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
