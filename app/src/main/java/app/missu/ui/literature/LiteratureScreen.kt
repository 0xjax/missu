package app.missu.ui.literature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.literature.LiteraryKind
import app.missu.data.literature.LiteraryWork
import app.missu.data.literature.LiteraryWorkSummary
import app.missu.data.literature.LiteratureFilter
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.MissuBottomSheet
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.TooltipIconButton
import kotlinx.coroutines.launch

@Composable
fun LiteratureList(
    state: LiteratureUiState,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
    showIntro: Boolean = false,
    contentTopInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    PageStateTransition(state = state, modifier = modifier.fillMaxSize()) { current ->
        when (current) {
            LiteratureUiState.Loading -> LoadingHint()
            is LiteratureUiState.Error -> ErrorHint(current.message.text(), onRetry)
            is LiteratureUiState.Content -> LiteratureContent(
                state = current,
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
                scrollToTopSignal = scrollToTopSignal,
                showIntro = showIntro,
                contentTopInset = contentTopInset,
            )
        }
    }
}

@Composable
private fun LiteratureContent(
    state: LiteratureUiState.Content,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    scrollToTopSignal: Int,
    showIntro: Boolean,
    contentTopInset: androidx.compose.ui.unit.Dp,
) {
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }
    LoadMoreOnScroll(
        state = listState,
        enabled = state.nextOffset != null && state.more == LoadMoreState.Idle,
        onLoadMore = onLoadMore,
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        },
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentTopInset + 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showIntro) {
            item {
                Text(
                    text = stringResource(R.string.poetry_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        if (state.works.isEmpty()) {
            item {
                EmptyHint(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.literature_empty_title),
                    subtitle = stringResource(R.string.literature_empty_subtitle),
                )
            }
        } else {
            items(state.works, key = { it.id }) { work ->
                LiteratureCard(
                    work = work,
                    favorite = work.id in favorites,
                    onToggleFavorite = {
                        focusManager.clearFocus()
                        onToggleFavorite(work.id)
                    },
                    onClick = {
                        focusManager.clearFocus()
                        onOpen(work.id)
                    },
                )
            }
            item {
                LoadMoreFooter(
                    nextPage = state.nextOffset,
                    state = state.more,
                    onRetry = onLoadMore,
                )
            }
        }
    }
}

@Composable
internal fun LiteratureCard(
    work: LiteraryWorkSummary,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    val locale = literatureLocale()
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = work.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = work.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(work.kind.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (work.theme(locale).isNotEmpty()) {
                        Text(
                            text = work.theme(locale),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FavoriteButton(favorite = favorite, onClick = onToggleFavorite)
        }
    }
}

@Composable
fun LiteratureScreen(
    work: LiteraryWork,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = literatureLocale()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var contentsOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        PageTopBar(
            title = work.title,
            onBack = onBack,
            actions = {
                if (work.chapters.size > 1) {
                    TooltipIconButton(
                        icon = Icons.Outlined.List,
                        contentDescription = stringResource(R.string.literature_chapters),
                        onClick = { contentsOpen = true },
                    )
                }
                FavoriteButton(favorite = favorite, onClick = onToggleFavorite)
            },
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = work.author,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (work.theme(locale).isNotEmpty()) {
                        Text(
                            text = work.theme(locale),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = work.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(work.chapters, key = { it.id }) { chapter ->
                LiteratureSection(title = chapter.title, body = chapter.text)
            }
            if (work.notes(locale).isEmpty() && work.translation(locale).isEmpty() && work.appreciation(locale).isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.literature_content_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (work.notes(locale).isNotEmpty()) {
                item {
                    LiteratureSection(title = stringResource(R.string.poetry_notes)) {
                        Surface(
                            shape = MaterialTheme.shapes.largeIncreased,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                work.notes(locale).forEach { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (work.translation(locale).isNotEmpty()) {
                item {
                    LiteratureSection(
                        title = stringResource(R.string.poetry_translation),
                        body = work.translation(locale),
                    )
                }
            }
            if (work.appreciation(locale).isNotEmpty()) {
                item {
                    LiteratureSection(
                        title = stringResource(R.string.poetry_appreciation),
                        body = work.appreciation(locale),
                    )
                }
            }
        }
    }
    if (contentsOpen) {
        MissuBottomSheet(onDismissRequest = { contentsOpen = false }) {
            Text(
                text = stringResource(R.string.literature_chapters),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                itemsIndexed(work.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    ListItem(
                        headlineContent = { Text(chapter.title) },
                        modifier = Modifier.clickable {
                            contentsOpen = false
                            coroutineScope.launch { listState.scrollToItem(index + 1) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    TooltipIconButton(
        icon = if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = stringResource(
            if (favorite) R.string.literature_favorite_remove else R.string.literature_favorite_add,
        ),
        onClick = onClick,
        tint = if (favorite) MaterialTheme.colorScheme.primary else null,
    )
}

@Composable
private fun LiteratureSection(
    title: String,
    body: String? = null,
    content: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun literatureLocale(): String {
    val locale = LocalConfiguration.current.locales[0]
    return when {
        locale.language == "zh" && locale.script.equals("Hant", ignoreCase = true) -> "zh-Hant"
        locale.language == "zh" -> "zh"
        locale.language == "ja" -> "ja"
        else -> "en"
    }
}

internal fun LiteraryKind.labelRes(): Int = when (this) {
    LiteraryKind.POEM -> R.string.literature_kind_poem
    LiteraryKind.CI -> R.string.literature_kind_ci
    LiteraryKind.QU -> R.string.literature_kind_qu
    LiteraryKind.PROSE -> R.string.literature_kind_prose
    LiteraryKind.BOOK -> R.string.literature_kind_book
}

internal fun LiteratureFilter.labelRes(): Int = when (this) {
    LiteratureFilter.ALL -> R.string.literature_filter_all
    LiteratureFilter.POEM -> R.string.literature_kind_poem
    LiteratureFilter.CI -> R.string.literature_kind_ci
    LiteratureFilter.QU -> R.string.literature_kind_qu
    LiteratureFilter.PROSE -> R.string.literature_kind_prose
    LiteratureFilter.BOOK -> R.string.literature_kind_book
    LiteratureFilter.FAVORITES -> R.string.literature_filter_favorites
}
