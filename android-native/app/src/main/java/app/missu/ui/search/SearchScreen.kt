@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.missu.ui.components.VideoGridCard

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
    var keywordValue by rememberSaveable { mutableStateOf("") }
    // 从发现页点最近搜索进来时直接搜（只跑一次）。
    LaunchedEffect(initialKeyword) {
        if (initialKeyword.isNotBlank()) {
            keywordValue = initialKeyword
            onSearch(initialKeyword)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch(keywordValue)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("←") }
            OutlinedTextField(
                value = keywordValue,
                onValueChange = { keywordValue = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索番号、女优、关键词") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }) { Text("搜索") }
            Spacer(Modifier.width(8.dp))
        }

        when (val current = state) {
            SearchUiState.Idle -> RecentSearches(
                keywords = recent,
                onPick = { keyword ->
                    keywordValue = keyword
                    onSearch(keyword)
                },
                onClear = onClearHistory,
                onSearch = submit,
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
}

/** 最近搜索：点一下直接搜。 */
@Composable
private fun RecentSearches(
    keywords: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    if (keywords.isEmpty()) {
        Hint("输入关键词后点搜索")
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

        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            keywords.forEach { keyword ->
                androidx.compose.material3.SuggestionChip(
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
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
