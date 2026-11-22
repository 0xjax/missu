@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.missu.ui.literature

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.literature.LiteraryWorkSummary
import app.missu.data.store.LiteratureHistoryEntry
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.PageTopBar

@Composable
fun LiteratureLibraryScreen(
    entries: List<LiteratureHistoryEntry>,
    favoritesState: LiteratureUiState,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onRetryFavorites: () -> Unit,
    onOpen: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        PageTopBar(
            title = stringResource(R.string.literature_library_title),
            onBack = onBack,
            actions = {
                if (selectedTab == 0 && entries.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) { Text(stringResource(R.string.common_clear)) }
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.literature_history_tab)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.literature_favorites_tab)) },
            )
        }
        if (selectedTab == 1) {
            LiteratureList(
                state = favoritesState,
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onLoadMore = onLoadMoreFavorites,
                onRetry = onRetryFavorites,
                onOpen = onOpen,
            )
        } else if (entries.isEmpty()) {
            EmptyHint(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.literature_history_empty_title),
                subtitle = stringResource(R.string.literature_history_empty_subtitle),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    LiteratureCard(
                        work = LiteraryWorkSummary(
                            id = entry.id,
                            kind = entry.kind,
                            title = entry.title,
                            author = entry.author,
                            themes = emptyMap(),
                        ),
                        favorite = entry.id in favorites,
                        onToggleFavorite = { onToggleFavorite(entry.id) },
                        onClick = { onOpen(entry.id) },
                    )
                }
            }
        }
    }
}
