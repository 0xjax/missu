package app.missu.ui.comics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.comic.ComicImage
import app.missu.data.comic.ComicSummary
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageStateTransition
import coil3.compose.AsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun ComicCover(
    image: ComicImage?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = remember(image) {
        image?.let {
            if (it.scrambleId == null) it.url else it.url.toHttpUrl().newBuilder()
                .addQueryParameter("_missu_scramble", it.scrambleId.toString())
                .build()
                .toString()
        }
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Composable
fun ComicListBody(
    state: ComicListUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (ComicSummary) -> Unit,
    modifier: Modifier = Modifier,
    contentTopInset: Dp = 0.dp,
) {
    PageStateTransition(state = state, modifier = modifier) { current ->
        when (current) {
            ComicListUiState.Loading -> LoadingHint()
            ComicListUiState.Empty -> EmptyHint(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.comic_empty),
            )
            is ComicListUiState.Error -> ErrorHint(current.message.text(), onRetry)
            is ComicListUiState.Content -> {
                val gridState = rememberLazyGridState()
                LoadMoreOnScroll(
                    state = gridState,
                    enabled = current.more == app.missu.ui.common.LoadMoreState.Idle,
                    onLoadMore = onLoadMore,
                )
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = contentTopInset + 12.dp,
                        bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(current.items, key = { it.id }) { comic ->
                        ComicGridCard(comic, onClick = { onOpen(comic) })
                    }
                    item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreFooter(current.nextPage, current.more, onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicGridCard(comic: ComicSummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ComicCover(
            image = comic.cover,
            contentDescription = comic.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium),
        )
        Text(
            text = comic.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        comic.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
