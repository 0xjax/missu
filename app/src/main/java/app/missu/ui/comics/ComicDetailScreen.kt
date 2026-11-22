package app.missu.ui.comics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.comic.ComicChapter
import app.missu.data.comic.ComicDetail
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.PageTopBar

@Composable
fun ComicDetailScreen(
    sourceName: String,
    state: ComicDetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenChapter: (ComicChapter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = (state as? ComicDetailUiState.Content)?.detail?.title ?: sourceName, onBack = onBack)
        PageStateTransition(state = state, modifier = Modifier.weight(1f)) { current ->
            when (current) {
                ComicDetailUiState.Loading -> LoadingHint()
                is ComicDetailUiState.Error -> ErrorHint(current.message.text(), onRetry)
                is ComicDetailUiState.Content -> ComicDetailContent(current.detail, onOpenChapter)
            }
        }
    }
}

@Composable
private fun ComicDetailContent(detail: ComicDetail, onOpenChapter: (ComicChapter) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth()) {
                ComicCover(
                    image = detail.cover,
                    contentDescription = detail.title,
                    modifier = Modifier.width(112.dp).height(168.dp).clip(MaterialTheme.shapes.medium),
                )
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(detail.title, style = MaterialTheme.typography.titleLarge)
                    if (detail.authors.isNotEmpty()) {
                        Text(
                            detail.authors.joinToString(" / "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    detail.status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        if (detail.tags.isNotEmpty()) {
            item(key = "tags") {
                Text(
                    detail.tags.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!detail.description.isNullOrBlank()) {
            item(key = "description") {
                Surface(
                    shape = MaterialTheme.shapes.largeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        detail.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (detail.chapters.isNotEmpty()) {
            item(key = "read") {
                FilledTonalButton(
                    onClick = { onOpenChapter(detail.chapters.first()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.comic_start_reading))
                }
            }
            item(key = "chapter-title") {
                Text(
                    stringResource(R.string.comic_chapters, detail.chapters.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(detail.chapters, key = { it.id }) { chapter ->
                Surface(
                    onClick = { onOpenChapter(chapter) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        } else {
            item(key = "empty") {
                Spacer(Modifier.height(16.dp))
                EmptyHint(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.comic_no_chapters),
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            }
        }
    }
}
