package app.missu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.missu.data.model.HomeSection
import app.missu.ui.components.VideoGridCard

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> Centered(modifier) { CircularProgressIndicator() }

        HomeUiState.Empty -> Centered(modifier) {
            Text("首页没有内容", style = MaterialTheme.typography.bodyMedium)
        }

        is HomeUiState.Error -> Centered(modifier) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }

        is HomeUiState.Content -> HomeGrid(state.sections, onOpen, onSearch, modifier)
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun HomeGrid(
    sections: List<HomeSection>,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        modifier = modifier,
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "search-entry", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // NOTE: 搜索入口先做成一个只读的"输入框"，点开进独立搜索页；真正输入在那边。
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF212225), RoundedCornerShape(10.dp))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "搜索番号、女优、关键词",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

            }
        }

        sections.forEach { section ->
            item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(
                items = section.videos,
                key = { video -> "${section.id}-${video.id}" },
            ) { video ->
                VideoGridCard(video = video, onClick = { onOpen(video.id) })
            }
        }
    }
}
