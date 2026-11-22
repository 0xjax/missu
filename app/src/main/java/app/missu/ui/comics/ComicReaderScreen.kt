package app.missu.ui.comics

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import app.missu.R
import app.missu.data.comic.ComicImage
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.PageTopBar
import coil3.compose.rememberAsyncImagePainter

@Composable
fun ComicReaderScreen(
    title: String,
    state: ComicReaderUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = title, onBack = onBack)
        PageStateTransition(state = state, modifier = Modifier.weight(1f)) { current ->
            when (current) {
                ComicReaderUiState.Loading -> LoadingHint()
                ComicReaderUiState.Empty -> EmptyHint(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.comic_reader_empty),
                )
                is ComicReaderUiState.Error -> ErrorHint(current.message.text(), onRetry)
                is ComicReaderUiState.Content -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(current.pages, key = { _, page -> page.url }) { index, page ->
                        ComicReaderImage(page = page, index = index)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicReaderImage(page: ComicImage, index: Int) {
    var pageRatio by remember(page.url) { mutableFloatStateOf(1f) }
    val url = remember(page) { comicImageUrl(page) }
    val painter = rememberAsyncImagePainter(
        model = url,
        contentScale = ContentScale.FillWidth,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
                pageRatio = size.width / size.height
            }
        },
    )
    Image(
        painter = painter,
        contentDescription = stringResource(R.string.comic_page_number, index + 1),
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().aspectRatio(pageRatio),
    )
}
