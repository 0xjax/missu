package app.missu.ui.literature

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import app.missu.ui.components.EmptyHint
import app.missu.ui.list.VideoOptionMenu
import app.missu.ui.list.VideoToolbarContainer
import app.missu.ui.search.LocalSearchBarInset
import app.missu.ui.search.SearchHostShell
import app.missu.ui.search.SearchTopVeil
import app.missu.ui.search.SearchTriggerHeight

@Composable
fun LiteratureNavigationScreen(
    literatureState: LiteratureUiState,
    query: String,
    filter: LiteratureFilter,
    favorites: Set<String>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (LiteratureFilter) -> Unit,
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
                scrollToTopSignal = scrollToTopSignal,
                showIntro = query.isBlank(),
                contentTopInset = LocalSearchBarInset.current,
            )
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            SearchTopVeil(
                statusTop = statusTop,
                veil = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                triggerHeight = SearchTriggerHeight,
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = statusTop)) {
                VideoToolbarContainer {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VideoOptionMenu(
                                text = stringResource(filter.labelRes()),
                                options = LiteratureFilter.entries.filterNot { it == LiteratureFilter.FAVORITES },
                                selected = filter,
                                onSelect = onFilterChange,
                                optionLabel = { stringResource(it.labelRes()) },
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            libraryAction()
                            searchAction()
                        }
                    }
                }
            }
        }
    }
}
