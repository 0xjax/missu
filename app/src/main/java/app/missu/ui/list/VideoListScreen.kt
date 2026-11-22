@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.missu.R
import kotlinx.coroutines.flow.StateFlow
import app.missu.data.api.VideoListOptions
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.VideoGridCard
import app.missu.ui.search.LocalSearchAnchorCapture
import app.missu.ui.search.LocalSearchBarInset
import app.missu.ui.search.SearchTriggerHeight

@Composable
internal fun AutoLoadVideoList(
    key: Any,
    state: StateFlow<VideoListUiState>,
    onLoad: () -> Unit,
) {
    LaunchedEffect(key) {
        // NOTE: 切换 key 时 Compose 状态可能仍是上一个 ViewModel 的 Content；必须读当前 StateFlow，否则会漏掉首次加载。
        if (state.value is VideoListUiState.Loading) onLoad()
    }
}

/**
 * 视频列表页：顶栏 + **一行口径**（排序 | 过滤）+ 列表本体。
 *
 * NOTE: 口径是「完整面」才有的东西——首页板块是摘要面，没有口径（见 `DESIGN.md` 的「列表口径」）。
 * NOTE: 换排序/过滤不重建页面：调用方按口径换一个 ViewModel 实例，这里只是同一套结构换数据。
 */
@Composable
fun VideoListScreen(
    title: String,
    state: VideoListUiState,
    sort: VideoListOptions.Sort?,
    filter: VideoListOptions.Filter?,
    detectedSort: VideoListOptions.Sort?,
    sorts: List<VideoListOptions.Sort>,
    filters: List<VideoListOptions.Filter>,
    onSortChange: (VideoListOptions.Sort?) -> Unit,
    onFilterChange: (VideoListOptions.Filter?) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = title, onBack = onBack)

        VideoListContent(
            state = state,
            sort = sort,
            filter = filter,
            detectedSort = detectedSort,
            sorts = sorts,
            filters = filters,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 完整面的内容：一行口径 + 列表本体，**不带顶栏**。
 *
 * NOTE: 列表页把它放在 `PageTopBar` 下面；影片页的「热门」栏目直接把它当栏目内容（栏目本身就是
 * 一面列表，没有自己的顶栏）。共用一处定义，口径行的档位、文案与「换口径不换结构」才能永远一致。
 */
@Composable
fun VideoListContent(
    state: VideoListUiState,
    sort: VideoListOptions.Sort?,
    filter: VideoListOptions.Filter?,
    detectedSort: VideoListOptions.Sort?,
    sorts: List<VideoListOptions.Sort>,
    filters: List<VideoListOptions.Filter>,
    onSortChange: (VideoListOptions.Sort?) -> Unit,
    onFilterChange: (VideoListOptions.Filter?) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    modifier: Modifier = Modifier,
    controlsLeading: (@Composable RowScope.() -> Unit)? = null,
    controlsTrailing: (@Composable () -> Unit)? = null,
    showControls: Boolean = true,
) {
    Column(modifier) {
        if (showControls) {
            VideoListControls(
                sort = sort,
                filter = filter,
                detectedSort = detectedSort,
                sorts = sorts,
                filters = filters,
                onSortChange = onSortChange,
                onFilterChange = onFilterChange,
                controlsLeading = controlsLeading,
                controlsTrailing = controlsTrailing,
            )
        }

        VideoListBody(
            state = state,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 口径行：栏目（仅热门页）、排序与过滤都使用「当前值 + 下拉菜单」。
 *
 * NOTE: 排序与过滤都收进菜单，触发器只显示当前值，不加「排序 / 过滤」前缀。
 * NOTE: 过滤是互斥单选，菜单同样包含「全部」；当前项只靠文字颜色表达，不改变触发器宽度。
 */
@Composable
internal fun VideoListControls(
    sort: VideoListOptions.Sort?,
    filter: VideoListOptions.Filter?,
    detectedSort: VideoListOptions.Sort?,
    sorts: List<VideoListOptions.Sort>,
    filters: List<VideoListOptions.Filter>,
    onSortChange: (VideoListOptions.Sort?) -> Unit,
    onFilterChange: (VideoListOptions.Filter?) -> Unit,
    controlsLeading: (@Composable RowScope.() -> Unit)?,
    controlsTrailing: (@Composable () -> Unit)?,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    // 站点点明了它自己当前的排序（页面头部「排序: X」），所以「默认」不再是一档：直接显示那一项。
    val current = sort ?: detectedSort

    VideoToolbarContainer {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            controlsLeading?.invoke(this)

            Box {
            VideoControlButton(
                text = sortLabel(current),
                expanded = sortMenuOpen,
                onClick = { sortMenuOpen = true },
            )
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                sorts.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option), color = optionColor(selected = option == current)) },
                        onClick = {
                            // 选中已是当前项时不重发请求：站点默认序与显式 `?sort=` 未必同序，
                            // 白白换一次数据只会让用户觉得"点了反而乱了"。
                            if (option != current) onSortChange(option)
                            sortMenuOpen = false
                        },
                    )
                }
            }
        }

        Box {
            VideoControlButton(
                text = filter?.let { filterLabel(it) } ?: stringResource(R.string.list_filter_all),
                expanded = filterMenuOpen,
                onClick = { filterMenuOpen = true },
            )
            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.list_filter_all),
                            color = optionColor(selected = filter == null),
                        )
                    },
                    onClick = {
                        if (filter != null) onFilterChange(null)
                        filterMenuOpen = false
                    },
                )
                    filters.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(filterLabel(option), color = optionColor(selected = option == filter)) },
                            onClick = {
                                if (option != filter) onFilterChange(option)
                                filterMenuOpen = false
                            },
                        )
                    }
                }
            }
        }
        if (controlsTrailing != null) {
            Spacer(Modifier.width(12.dp))
            controlsTrailing()
        }
        }
    }
}

/**
 * 列表内容本体：三态 + 网格 + 分页，**不带顶栏**。
 *
 * NOTE: 抽出来是因为「影片页 · 热门」要把同一面内容嵌进栏目里，而列表页要在它上面加一条
 * 顶栏。两边必须共用，否则分页去重、空态文案这些细节会各写一份、慢慢走偏。
 */
@Composable
fun VideoListBody(
    state: VideoListUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    PageStateTransition(state = state, modifier = modifier) { current ->
        when (current) {
            VideoListUiState.Loading -> LoadingHint()

            VideoListUiState.Empty -> EmptyHint(
                icon = Icons.AutoMirrored.Outlined.List,
                title = stringResource(R.string.list_empty_title),
            )

            is VideoListUiState.Error -> ErrorHint(current.message.text(), onRetry)

            is VideoListUiState.Content -> {
                val gridState = rememberLazyGridState()
                LoadMoreOnScroll(
                    state = gridState,
                    enabled = current.more == LoadMoreState.Idle,
                    onLoadMore = onLoadMore,
                )
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(LocalVideoListColumns.current),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = LocalSearchBarInset.current + 12.dp,
                        bottom = 32.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items = current.videos, key = { it.id + it.pageUrl }) { video ->
                        VideoGridCard(video = video, onClick = { onOpen(video.id, video.thumbnailUrl) })
                    }

                    // NOTE: 脚注无条件存在（到底时它自己变成「没有更多了」），所以最后一个 item 的
                    // index 永远是 total-1，自动加载的判定不用为「有没有脚注」分两种情形。
                    item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreFooter(
                            nextPage = current.nextPage,
                            state = current.more,
                            onRetry = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun VideoToolbarContainer(content: @Composable () -> Unit) {
    val captureBounds = LocalSearchAnchorCapture.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { captureBounds(it.boundsInWindow()) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        content()
    }
}

@Composable
internal fun VideoControlButton(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(SearchTriggerHeight),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun optionColor(selected: Boolean): Color =
    if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified

/**
 * 排序的文案。`null` = 站点默认序。
 *
 * NOTE: 站点自己的列表页有这 7 档的显示名（发行日期 / 最近更新 / 收藏数 / 今日浏览数 / 本週浏览数 /
 * 本月浏览数 / 总浏览数）。我们只做**两处有意偏离**：
 * - `published_at` 叫「上架日期」而不是站点的「最近更新」——「最近更新」已经是首页板块名，同一个词
 *   放进排序菜单会被读成「跳到那个板块」；上架日期信息量更强，也不撞词。
 * - 浏览四档去掉末尾的「数」：菜单里 3–4 个字长度更齐、读起来有节奏，语义不变。
 * 其余照站点：发行日期 / 收藏数 / 今日浏览 / 本周浏览 / 本月浏览 / 总浏览。
 * NOTE: 站点没有「默认」这一档（它总把某个具体排序显示为选中），我们保留「默认」是因为 `/new`
 * 不带参数时的顺序不等于任何取值；默认是否真实存在随页面类型而异，保留独立状态更安全。
 */
@Composable
internal fun sortLabel(sort: VideoListOptions.Sort?): String = when (sort) {
    null -> stringResource(R.string.list_sort_default)
    VideoListOptions.Sort.RELEASED_AT -> stringResource(R.string.list_sort_released_at)
    VideoListOptions.Sort.PUBLISHED_AT -> stringResource(R.string.list_sort_published_at)
    VideoListOptions.Sort.SAVED -> stringResource(R.string.list_sort_saved)
    VideoListOptions.Sort.TODAY_VIEWS -> stringResource(R.string.list_sort_today_views)
    VideoListOptions.Sort.WEEKLY_VIEWS -> stringResource(R.string.list_sort_weekly_views)
    VideoListOptions.Sort.MONTHLY_VIEWS -> stringResource(R.string.list_sort_monthly_views)
    VideoListOptions.Sort.VIEWS -> stringResource(R.string.list_sort_views)
}

@Composable
internal fun filterLabel(filter: VideoListOptions.Filter): String = when (filter) {
    VideoListOptions.Filter.INDIVIDUAL -> stringResource(R.string.list_filter_individual)
    VideoListOptions.Filter.MULTIPLE -> stringResource(R.string.list_filter_multiple)
    VideoListOptions.Filter.CHINESE_SUBTITLE -> stringResource(R.string.list_filter_chinese_subtitle)
}
