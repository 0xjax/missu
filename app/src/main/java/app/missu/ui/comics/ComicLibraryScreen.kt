@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.missu.ui.comics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import app.missu.data.comic.ComicSummary
import app.missu.data.store.ComicFavoriteEntry
import app.missu.data.store.ComicHistoryEntry
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.PageTopBar
import app.missu.ui.list.ToolbarRadioGroup
import app.missu.ui.settings.ContentSettingsButton
import app.missu.ui.settings.SectionLabel

@Composable
fun ComicLibraryScreen(
    entries: List<ComicHistoryEntry>,
    favorites: List<ComicFavoriteEntry>,
    comicColumns: Int,
    onOpen: (ComicHistoryEntry) -> Unit,
    onOpenFavorite: (ComicFavoriteEntry) -> Unit,
    onClear: () -> Unit,
    onComicColumnsChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier.fillMaxSize()) {
        PageTopBar(
            title = stringResource(R.string.comic_history_title),
            onBack = onBack,
            actions = {
                if (selectedTab == 1 && entries.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
                }
                ContentSettingsButton(
                    contentDescription = stringResource(R.string.settings_comic),
                ) { _ ->
                    SectionLabel(stringResource(R.string.settings_comic_list))
                    ToolbarRadioGroup(
                        title = stringResource(R.string.settings_comic_columns),
                        options = listOf(2, 3),
                        selected = comicColumns,
                        onSelect = { value -> value?.let(onComicColumnsChange) },
                        optionLabel = { value ->
                            stringResource(
                                if (value == 2) R.string.settings_comic_columns_double else R.string.settings_comic_columns_triple,
                            )
                        },
                    )
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.comic_favorites_tab)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.comic_reading_history_tab)) },
            )
        }
        when {
            selectedTab == 0 && favorites.isEmpty() -> EmptyHint(
                icon = Icons.Outlined.FavoriteBorder,
                title = stringResource(R.string.comic_favorites_empty_title),
                subtitle = stringResource(R.string.comic_favorites_empty_subtitle),
            )
            selectedTab == 1 && entries.isEmpty() -> EmptyHint(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.comic_history_empty_title),
                subtitle = stringResource(R.string.comic_history_empty_subtitle),
            )
            selectedTab == 0 -> LazyVerticalGrid(
                columns = GridCells.Fixed(comicColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(favorites, key = { "${it.source.id}:${it.mangaId}" }) { entry ->
                    ComicGridCard(
                        comic = ComicSummary(
                            id = entry.mangaId,
                            title = entry.title,
                            cover = entry.cover,
                            subtitle = entry.source.displayName,
                        ),
                        onClick = { onOpenFavorite(entry) },
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(comicColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(entries, key = { "${it.source.id}:${it.mangaId}" }) { entry ->
                    ComicGridCard(
                        comic = ComicSummary(
                            id = entry.mangaId,
                            title = entry.title,
                            cover = entry.cover,
                            subtitle = listOf(
                                entry.source.displayName,
                                entry.chapterTitle,
                                stringResource(R.string.comic_history_page, entry.page + 1),
                            ).joinToString(" · "),
                        ),
                        onClick = { onOpen(entry) },
                    )
                }
            }
        }
    }
}
