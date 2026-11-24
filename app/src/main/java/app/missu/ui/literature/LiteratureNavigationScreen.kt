package app.missu.ui.literature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.literature.LiteratureFilter
import app.missu.data.literature.LiteratureSort
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.LiquidGlassIsland
import app.missu.ui.components.LiquidToolbar
import app.missu.ui.list.ToolbarOptionsButton
import app.missu.ui.list.ToolbarOptionsSheet
import app.missu.ui.list.ToolbarRadioGroup
import app.missu.ui.search.LocalSearchBarInset
import app.missu.ui.search.SearchHostShell
import app.missu.ui.search.SearchTopVeil
import app.missu.ui.search.SearchToolbarChromeHeight
import app.missu.ui.search.searchGlassSource

@Composable
fun LiteratureNavigationScreen(
    literatureState: LiteratureUiState,
    query: String,
    filter: LiteratureFilter,
    sort: LiteratureSort,
    favorites: Set<String>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (LiteratureFilter) -> Unit,
    onSortChange: (LiteratureSort) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenLiterature: (String) -> Unit,
    libraryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrollToTopSignal: Int = 0,
) {
    SearchHostShell(
        searching = query.isNotBlank(),
        onSearch = onQueryChange,
        onClear = { onQueryChange("") },
        modifier = modifier,
        initialKeyword = query,
        placeholder = stringResource(R.string.literature_search_hint),
        inlineTrigger = true,
        onQueryChange = onQueryChange,
        expandedContent = {
            LiteratureList(
                state = literatureState,
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onOpen = onOpenLiterature,
            )
        },
        idleContent = {
            EmptyHint(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.literature_search_hint),
            )
        },
    ) { searchAction ->
        Box(Modifier.fillMaxSize()) {
            LiteratureList(
                state = literatureState,
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
                onOpen = onOpenLiterature,
                modifier = Modifier.searchGlassSource(),
                scrollToTopSignal = scrollToTopSignal,
                contentTopInset = LocalSearchBarInset.current,
            )
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            SearchTopVeil(
                statusTop = statusTop,
                veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                triggerHeight = SearchToolbarChromeHeight,
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = statusTop)) {
                LiquidToolbar {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidGlassIsland { searchAction() }
                        Spacer(Modifier.weight(1f))
                        LiquidGlassIsland {
                            Row {
                                ToolbarOptionsButton { dismiss ->
                                    ToolbarOptionsSheet {
                                    ToolbarRadioGroup(
                                        title = stringResource(R.string.list_sort_title),
                                        options = LiteratureSort.entries,
                                        selected = sort,
                                        onSelect = { value ->
                                            if (value != null && value != sort) onSortChange(value)
                                            dismiss()
                                        },
                                        optionLabel = { stringResource(it.labelRes()) },
                                    )
                                    ToolbarRadioGroup(
                                        title = stringResource(R.string.literature_filter_title),
                                        options = LiteratureFilter.entries.filterNot { it == LiteratureFilter.FAVORITES },
                                        selected = filter,
                                        onSelect = { value ->
                                            if (value != null && value != filter) onFilterChange(value)
                                            dismiss()
                                        },
                                        optionLabel = { stringResource(it.labelRes()) },
                                    )
                                }
                            }
                                libraryAction()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LiteratureSort.labelRes(): Int = when (this) {
    LiteratureSort.PROMINENCE -> R.string.literature_sort_prominence
    LiteratureSort.TITLE -> R.string.literature_sort_title
    LiteratureSort.AUTHOR -> R.string.literature_sort_author
}
