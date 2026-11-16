@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import app.missu.ui.components.PushPinIcon
import app.missu.ui.components.TooltipIconButton
import app.missu.ui.components.VideoGridCard

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
    showPin: Boolean,
    onPin: () -> Unit,
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
            showPin = showPin,
            onPin = onPin,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 完整面的内容：一行口径 + 列表本体，**不带顶栏**。
 *
 * NOTE: 列表页把它放在 `PageTopBar` 下面；发现页的「热门」页签直接把它当页签内容（页签本身就是
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
    showPin: Boolean,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ListControls(
            sort = sort,
            filter = filter,
            detectedSort = detectedSort,
            sorts = sorts,
            filters = filters,
            onSortChange = onSortChange,
            onFilterChange = onFilterChange,
            showPin = showPin,
            onPin = onPin,
        )

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
 * 口径行：左边是排序（当前值 + 下拉菜单），中间是可横向滚动的过滤，末尾固定首页置顶。
 *
 * NOTE: 排序档位有 7 档 + 「默认」，平铺会把页头撑死，所以用菜单；≤3 档的平铺形态见发现页
 * 「女优」页签的三档排序组（同一个 ToggleButton 家族）。触发器只显示当前值，不加「排序」前缀。
 * NOTE: 过滤是**互斥单选**（站点的多值过滤实测不可靠，见 [VideoListOptions]），所以用
 * `FilterChip` 的选中态表达，而不是多选勾选。
 */
@Composable
private fun ListControls(
    sort: VideoListOptions.Sort?,
    filter: VideoListOptions.Filter?,
    detectedSort: VideoListOptions.Sort?,
    sorts: List<VideoListOptions.Sort>,
    filters: List<VideoListOptions.Filter>,
    onSortChange: (VideoListOptions.Sort?) -> Unit,
    onFilterChange: (VideoListOptions.Filter?) -> Unit,
    showPin: Boolean,
    onPin: () -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    // 站点点明了它自己当前的排序（页面头部「排序: X」），所以「默认」不再是一档：直接显示那一项。
    val current = sort ?: detectedSort

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssistChip(
                onClick = { sortMenuOpen = true },
                label = { Text(sortLabel(current), maxLines = 1) },
                trailingIcon = {
                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                },
            )
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                sorts.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option), color = sortColor(selected = option == current)) },
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

        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { onFilterChange(null) },
                label = { Text(stringResource(R.string.list_filter_all), maxLines = 1) },
            )
            filters.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { onFilterChange(option) },
                    label = { Text(filterLabel(option), maxLines = 1) },
                )
            }
        }

        if (showPin) {
            TooltipIconButton(
                icon = PushPinIcon,
                contentDescription = stringResource(R.string.list_pin_home),
                onClick = onPin,
            )
        }
    }
}

/**
 * 列表内容本体：三态 + 网格 + 分页，**不带顶栏**。
 *
 * NOTE: 抽出来是因为「发现页 · 热门」要把同一面内容嵌进页签里，而列表页要在它上面加一条
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
                icon = Icons.AutoMirrored.Filled.List,
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
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
private fun sortColor(selected: Boolean): Color =
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
private fun sortLabel(sort: VideoListOptions.Sort?): String = when (sort) {
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
private fun filterLabel(filter: VideoListOptions.Filter): String = when (filter) {
    VideoListOptions.Filter.INDIVIDUAL -> stringResource(R.string.list_filter_individual)
    VideoListOptions.Filter.MULTIPLE -> stringResource(R.string.list_filter_multiple)
    VideoListOptions.Filter.CHINESE_SUBTITLE -> stringResource(R.string.list_filter_chinese_subtitle)
}
