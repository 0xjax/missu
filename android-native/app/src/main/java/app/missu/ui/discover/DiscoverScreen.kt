package app.missu.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.data.api.ActressFilterOptions
import app.missu.data.model.DirectoryItem
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    states: Map<String, DirectoryUiState>,
    sort: String,
    filters: Map<String, String>,
    onLoad: (String) -> Unit,
    onLoadMore: (String) -> Unit,
    onOpen: (path: String, title: String) -> Unit,
    onSortChange: (String) -> Unit,
    onFilterChange: (key: String, value: String?) -> Unit,
    onResetFilters: () -> Unit,
    recentSearches: List<String> = emptyList(),
    onSearchEntry: () -> Unit = {},
    onRecentSearch: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val tabs = DiscoverTab.entries
    val tab = tabs[tabIndex]

    Column(modifier.fillMaxSize()) {
        // 搜索入口：发现页吸收搜索之后，这里就是搜索的唯一入口（结果页仍是独立路由）。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .background(Color(0xFF212225), RoundedCornerShape(10.dp))
                .clickable(onClick = onSearchEntry)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = "搜索番号、女优、关键词",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (recentSearches.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(recentSearches) { keyword ->
                    SuggestionChip(
                        onClick = { onRecentSearch(keyword) },
                        label = { Text(keyword) },
                    )
                }
            }
        }

        TabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, item ->
                Tab(
                    selected = index == tabIndex,
                    onClick = { tabIndex = index },
                    text = { Text(item.label) },
                )
            }
        }

        // 切到某个目录时才去拉它，省一次请求。
        LaunchedEffect(tab.kind) {
            if (states[tab.kind] == null) onLoad(tab.kind)
        }

        // 排序与筛选只有女优目录有（站点对类型目录不提供 sort / 筛选参数）。
        if (tab == DiscoverTab.ACTRESSES) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActressFilterOptions.sorts.forEach { option ->
                    FilterChip(
                        selected = sort == option.value,
                        onClick = { onSortChange(option.value) },
                        label = { Text(option.label) },
                    )
                }

                Spacer(Modifier.weight(1f))

                if (filters.isNotEmpty()) {
                    TextButton(onClick = onResetFilters) { Text("清除") }
                }
                FilterChip(
                    selected = filters.isNotEmpty(),
                    onClick = { filterSheetOpen = true },
                    label = { Text(if (filters.isEmpty()) "筛选" else "筛选 (${filters.size})") },
                )
            }
        }

        when (val state = states[tab.kind]) {
            null, DirectoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            DirectoryUiState.Empty -> CenteredHint("这里还没有内容")

            is DirectoryUiState.Error -> CenteredHint(state.message) { onLoad(tab.kind) }

            is DirectoryUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(items = state.items, key = { it.path }) { item ->
                    DirectoryRow(item = item, onClick = { onOpen(item.path, item.name) })
                }

                if (state.nextPage != null) {
                    item(key = "more-${tab.kind}") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.loadingMore) {
                                CircularProgressIndicator()
                            } else {
                                Button(onClick = { onLoadMore(tab.kind) }) { Text("加载更多") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            FilterSheet(
                filters = filters,
                onFilterChange = onFilterChange,
                onDismiss = { filterSheetOpen = false },
            )
        }
    }
}

/** 四组筛选：身高 / 罩杯 / 年龄 / 出道年份。选项来自 [ActressFilterOptions]（对照 legacy 常量表）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    filters: Map<String, String>,
    onFilterChange: (key: String, value: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("筛选女优", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("完成") }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().height(520.dp)) {
            ActressFilterOptions.sections.forEach { section ->
                item(key = "title-${section.key}") {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                item(key = "options-${section.key}") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        section.options.forEach { option ->
                            FilterChip(
                                selected = filters[section.key] == option.value,
                                // 再点一次同一项 = 取消这一组筛选。
                                onClick = {
                                    val next = if (filters[section.key] == option.value) null else option.value
                                    onFilterChange(section.key, next)
                                },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(item: DirectoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF212225)),
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
    }
}
