@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.missu.ui.components.VideoGridCard
import kotlinx.coroutines.launch

/**
 * 搜索页：M3E 的 slot + `SearchBarState` 搜索栏。
 *
 * NOTE: 折叠态是页面顶部的一条 `SearchBar`，展开态是独立的 `ExpandedFullScreenSearchBar` 浮层；
 * 输入框获得焦点会自动展开，所以不需要手动调 `animateToExpanded()`。
 * NOTE: `SearchBarState` 的 `animateToExpanded/Collapsed` 都是 suspend，只能放进 `scope.launch`。
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    recent: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
    initialKeyword: String = "",
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()

    // 从发现页/历史胶囊带关键词进来时直接搜（只跑一次）。
    LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank()) {
            textFieldState.edit { replace(0, length, initialKeyword) }
            onSearch(initialKeyword)
        }
    }

    // NOTE: foundation 1.13 的 TextFieldState 只有 edit { }（没有 setTextAndPlaceCursorAtEnd/clearText）。
    fun submit(keyword: String) {
        if (keyword.isBlank()) return
        scope.launch { searchBarState.animateToCollapsed() }
        onSearch(keyword)
    }

    fun pick(keyword: String) {
        textFieldState.edit { replace(0, length, keyword) }
        submit(keyword)
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = { submit(textFieldState.text.toString().trim()) },
            placeholder = { Text("搜索番号、女优、关键词") },
            leadingIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { textFieldState.edit { replace(0, length, "") } }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        SearchBar(state = searchBarState, inputField = inputField)

        when (val current = state) {
            SearchUiState.Idle -> RecentSearches(
                keywords = recent,
                onPick = { pick(it) },
                onClear = onClearHistory,
            )

            SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            SearchUiState.Empty -> Hint("没有找到结果")

            is SearchUiState.Error -> Hint(current.message)

            is SearchUiState.Content -> Results(
                state = current,
                onLoadMore = onLoadMore,
                onOpen = onOpen,
            )
        }
    }

    // 展开态：全屏浮层里放最近搜索。
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        RecentSearches(
            keywords = recent,
            onPick = { pick(it) },
            onClear = onClearHistory,
        )
    }
}

/** 最近搜索：点一下直接搜。 */
@Composable
private fun RecentSearches(
    keywords: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (keywords.isEmpty()) {
        Hint("输入关键词后回车搜索")
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "最近搜索",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空") }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keywords.forEach { keyword ->
                SuggestionChip(
                    onClick = { onPick(keyword) },
                    label = { Text(keyword) },
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Results(state: SearchUiState.Content, onLoadMore: () -> Unit, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "「${state.keyword}」共 ${state.videos.size} 条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(items = state.videos, key = { it.id + it.pageUrl }) { video ->
            VideoGridCard(video = video, onClick = { onOpen(video.id) })
        }

        if (state.nextPage != null) {
            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) {
                        LoadingIndicator()
                    } else {
                        Button(onClick = onLoadMore) { Text("加载更多") }
                    }
                }
            }
        } else {
            item(key = "end", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
