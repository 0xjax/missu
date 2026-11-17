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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
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
        PageTopBar(title = stringResource(R.string.saved_title), onBack = onBack)

        PageStateTransition(state = state, modifier = Modifier.fillMaxSize()) { current ->
            when (current) {
                SavedUiState.Loading -> LoadingHint()

                SavedUiState.Empty -> EmptyHint(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = stringResource(R.string.saved_empty_title),
                    subtitle = stringResource(R.string.saved_empty_subtitle),
                )

                is SavedUiState.Error -> ErrorHint(current.message.text(), onRetry)

                is SavedUiState.Content -> {
                    val gridState = rememberLazyGridState()
                    LoadMoreOnScroll(
                        state = gridState,
                        enabled = current.more == LoadMoreState.Idle,
                        onLoadMore = onLoadMore,
                    )
                    LazyVerticalGrid(
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        columns = GridCells.Fixed(LocalVideoListColumns.current),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(items = current.videos, key = { it.id + it.pageUrl }) { video ->
                            VideoGridCard(video = video, onClick = { onOpen(video.id, video.thumbnailUrl) })
                        }

                        item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                            LoadMoreFooter(
                                nextPage = current.nextPage,
                                state = current.more,
                                onRetry = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
