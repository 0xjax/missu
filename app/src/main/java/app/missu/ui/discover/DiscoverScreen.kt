@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.api.ActressFilterOptions
import app.missu.data.api.VideoListOptions
import app.missu.data.model.DirectoryItem
import app.missu.data.model.NavGroup
import app.missu.data.model.NavItem
import app.missu.ui.components.MissuBottomSheet
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadMoreFooter
import app.missu.ui.components.LoadMoreOnScroll
import app.missu.ui.components.LoadingHint
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.text
import app.missu.ui.list.VideoListContent
import app.missu.ui.list.VideoListUiState
import coil3.compose.AsyncImage

/**
 * 无头像占位用的 expressive 形状取 M3E 的 `MaterialShapes`（`Cookie9Sided`）。
 *
 * NOTE: `toShape()` 是 `@Composable`（内部带缓存），所以只能在组合里取，不能提到文件级 val。
 * 形状全局固定、只有颜色随条目变化——比正圆多一点"设计过"的意味，又不至于让整列花掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    states: Map<String, DirectoryUiState>,
    sort: String,
    filters: Map<String, String>,
    refreshingKinds: Set<String>,
    hotSort: VideoListOptions.Sort?,
    hotFilter: VideoListOptions.Filter?,
    hotDetectedSort: VideoListOptions.Sort?,
    hotState: VideoListUiState,
    channels: ChannelsUiState,
    onLoad: (String) -> Unit,
    onLoadMore: (String) -> Unit,
    onRefresh: (String) -> Unit,
    onChannelsLoad: () -> Unit,
    onChannelsRetry: () -> Unit,
    onHotSortChange: (VideoListOptions.Sort?) -> Unit,
    onHotFilterChange: (VideoListOptions.Filter?) -> Unit,
    onHotLoadMore: () -> Unit,
    onHotRetry: () -> Unit,
    onOpen: (path: String, title: String) -> Unit,
    onOpenVideo: (id: String, thumbnail: String?) -> Unit,
    onSortChange: (String) -> Unit,
    onFilterChange: (key: String, value: String?) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val tabs = DiscoverTab.entries
    val tab = tabs[tabIndex]

    Column(modifier.fillMaxSize()) {

        // NOTE: 用 M3E 的 PrimaryTabRow（基线 TabRow 已废弃）；五个页签等宽排布。
        PrimaryTabRow(selectedTabIndex = tabIndex) {
            tabs.forEachIndexed { index, item ->
                Tab(
                    selected = index == tabIndex,
                    onClick = { tabIndex = index },
                    text = { Text(stringResource(item.labelRes)) },
                )
            }
        }

        // 切到某个页签时才去拉它，省请求：目录各自一次；频道是第一次进来时拉一次首页导航。
        LaunchedEffect(tab.kind) {
            when {
                tab == DiscoverTab.CHANNELS -> if (channels is ChannelsUiState.Loading) onChannelsLoad()
                tab.isDirectory -> if (states[tab.kind] == null) onLoad(tab.kind)
            }
        }

        // 排序与筛选只有女优目录有（站点对类型目录不提供 sort / 筛选参数）。
        if (tab == DiscoverTab.ACTRESSES) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // NOTE: 排序是「单选且必选」，按 M3E 用连通 ToggleButton 组（SegmentedButton 已退役），
                // 首尾用 connected*ButtonShapes 咬合，语义标成 RadioButton。
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    ActressFilterOptions.sorts.forEachIndexed { index, option ->
                        ToggleButton(
                            checked = sort == option.value,
                            onCheckedChange = { onSortChange(option.value) },
                            shapes = when {
                                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                index == ActressFilterOptions.sorts.lastIndex ->
                                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        ) {
                            Text(sortLabel(option.value), maxLines = 1, softWrap = false)
                        }
                    }
                }

                // NOTE: 「本月排行」是站点另一张页面，不吃 height/cup/age/debut 这些筛选参数；
                // 留着「筛选」按钮的话，点开会得到一组点完没有任何变化的胶囊。所以那一档藏起来。
                if (sort != ActressFilterOptions.SORT_RANKING) {
                    if (filters.isNotEmpty()) {
                        TextButton(onClick = onResetFilters) { Text(stringResource(R.string.discover_clear)) }
                    }
                    FilterChip(
                        selected = filters.isNotEmpty(),
                        onClick = { filterSheetOpen = true },
                        label = { Text(if (filters.isEmpty()) stringResource(R.string.discover_filter) else stringResource(R.string.discover_filter_count, filters.size)) },
                    )
                }
            }
        }

        if (tab == DiscoverTab.HOT) {
            // NOTE: 热门就是 `/today-hot` 这面列表本身——今日 / 本周 / 本月只是它的三个排序取值，
            // 所以这里不另造一套「周期」结构，直接用列表页那一行口径（排序菜单 + 过滤 chip）。
            VideoListContent(
                state = hotState,
                sort = hotSort,
                filter = hotFilter,
                detectedSort = hotDetectedSort,
                sorts = VideoListOptions.Sort.entries,
                filters = VideoListOptions.Filter.entries,
                onSortChange = onHotSortChange,
                onFilterChange = onHotFilterChange,
                onRetry = onHotRetry,
                onLoadMore = onHotLoadMore,
                onOpen = onOpenVideo,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (tab == DiscoverTab.CHANNELS) {
            ChannelsContent(state = channels, onRetry = onChannelsRetry, onOpen = onOpen)
        } else when (val state = states[tab.kind]) {
            null, DirectoryUiState.Loading -> LoadingHint()

            DirectoryUiState.Empty -> EmptyHint(
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.list_empty_title),
            )

            is DirectoryUiState.Error -> ErrorHint(state.message.text(), onRetry = { onLoad(tab.kind) })

            // NOTE: 下拉刷新只包住有内容的这一支：Loading / Empty / Error 三支本来就有自己的重试入口，
            // 给不可滚动的空态套下手势没有意义。
            is DirectoryUiState.Content -> PullToRefreshBox(
                isRefreshing = tab.kind in refreshingKinds,
                onRefresh = { onRefresh(tab.kind) },
                modifier = Modifier.fillMaxSize(),
            ) {
                // NOTE: 女优带头像走双列卡片，类型/厂商没有头像图，走三列文字块——两者都是一屏多放 60% 的密度。
                val actresses = tab == DiscoverTab.ACTRESSES
                // NOTE: 站点的 `/genres` 索引里没有 VR（实测翻到第 2 页也没有），但 `/genres/VR`
                // 是正常类型页。按「它是类型」补一格，而不是给它另开一个维度。
                val gridItems = if (tab == DiscoverTab.GENRES) {
                    listOf(DiscoverPins.vrGenre) +
                        state.items.filterNot { it.path == DiscoverPins.vrGenre.path }
                } else {
                    state.items
                }
                val gridState = rememberLazyGridState()
                LoadMoreOnScroll(
                    state = gridState,
                    enabled = state.more == LoadMoreState.Idle,
                    onLoadMore = { onLoadMore(tab.kind) },
                )
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    columns = GridCells.Fixed(if (actresses) 2 else 3),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (actresses) 4.dp else 8.dp),
                ) {
                    items(items = gridItems, key = { it.path }) { item ->
                        if (actresses) {
                            ActressCell(item = item, onClick = { onOpen(item.path, item.name) })
                        } else {
                            DirectoryTextCell(item = item, onClick = { onOpen(item.path, item.name) })
                        }
                    }

                    item(key = "more-${tab.kind}", span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreFooter(
                            nextPage = state.nextPage,
                            state = state.more,
                            onRetry = { onLoadMore(tab.kind) },
                        )
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        MissuBottomSheet(onDismissRequest = { filterSheetOpen = false }) {
            FilterSheet(
                filters = filters,
                onFilterChange = onFilterChange,
                onDismiss = { filterSheetOpen = false },
            )
        }
    }
}

/**
 * 「频道」页签：站点导航里的策展列表（素人 / 无码影片 / 亚洲 AV …）。
 *
 * NOTE: 分组是**排版**，不是层级——所有行平铺在同一页、没有可折叠的第二级。所以站点把
 * 「韩国直播」塞进「亚洲 AV」组这件事，不会在我们这里多冒出一层结构；界面上直播单独成组
 * （见 [splitLiveGroup]）。
 */
@Composable
private fun ChannelsContent(
    state: ChannelsUiState,
    onRetry: () -> Unit,
    onOpen: (path: String, title: String) -> Unit,
) {
    when (state) {
        ChannelsUiState.Loading -> LoadingHint()

        ChannelsUiState.Empty -> EmptyHint(
            icon = Icons.AutoMirrored.Filled.List,
            title = stringResource(R.string.list_empty_title),
        )

        is ChannelsUiState.Error -> ErrorHint(state.message.text(), onRetry)

        is ChannelsUiState.Content -> {
            val liveTitle = stringResource(R.string.discover_channels_live)
            val groups = remember(state.groups, liveTitle) { splitLiveGroup(state.groups, liveTitle) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    item(key = "group-${group.title}") {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    group.items.forEach { navItem ->
                        item(key = "${group.title}-${navItem.path}") {
                            ChannelRow(
                                name = navItem.name,
                                onClick = { onOpen(navItem.path, navItem.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把直播两项从站点分组里摘出来、单独成组。
 *
 * NOTE: 站点把 `韩国直播 / 中国直播` 放在「亚洲 AV」组里——那是编辑排版。性质上它们是另一类
 * 列表（直播合集，不是片商频道），混在片商堆里读起来像同一类东西。
 */
private fun splitLiveGroup(groups: List<NavGroup>, liveTitle: String): List<NavGroup> {
    val live = mutableListOf<NavItem>()
    val kept = groups.mapNotNull { group ->
        val rest = group.items.filterNot { item ->
            (item.path in ChannelRules.livePaths).also { isLive -> if (isLive) live += item }
        }
        if (rest.isEmpty()) null else group.copy(items = rest)
    }
    return if (live.isEmpty()) kept else kept + NavGroup(title = liveTitle, items = live)
}

/** 频道页签里的一行。NOTE: `Surface(onClick)` 自带容器色、圆角、涟漪与整行可点的无障碍语义。 */
@Composable
private fun ChannelRow(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(stringResource(R.string.discover_filter_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().height(520.dp)) {
            ActressFilterOptions.sections.forEach { section ->
                item(key = "title-${section.key}") {
                    Text(
                        text = sectionTitle(section.key),
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
                                label = { Text(optionLabel(section.key, option.value)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 女优单元格：双列，头像 + 名字 + 条数 + 出道（后两行按数据有无出现）。
 *
 * NOTE: 双列后单元格只有 158dp，头像 44dp + 12dp 间距留给文字 102dp——名字超过 5~6 个汉字会截断，
 * 这是换取「一屏多 60% 条目」的代价，已确认可接受。
 */
@Composable
private fun ActressCell(item: DirectoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // NOTE: 没有头像时不做"灰色空圈 + 中性首字"（那看起来像加载失败），改成 M3E 的 expressive 形状
        // + 三种色调容器里的首字：形状与配色都由条目 id 决定，同一女优每次进来都一样（不会闪），
        // 但不同条目之间有细微差异——这就是那点"小惊喜"，成本只有十几行、不引任何资源。
        val tone = remember(item.id) { Math.floorMod(item.id.hashCode(), 3) }
        val containerColor = when (tone) {
            0 -> MaterialTheme.colorScheme.primaryContainer
            1 -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.tertiaryContainer
        }
        val contentColor = when (tone) {
            0 -> MaterialTheme.colorScheme.onPrimaryContainer
            1 -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }
        val placeholderShape = if (item.imageUrl == null) MaterialShapes.Cookie9Sided.toShape() else null
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(placeholderShape ?: CircleShape)
                .background(if (item.imageUrl != null) MaterialTheme.colorScheme.surfaceContainerHigh else containerColor),
            // NOTE: 居中必须写在这一层。之前里面又套了一个 `Box(contentAlignment = Center)`，
            // 但内层没给尺寸、只包住文字，结果整块被摆在圆的左上角，还被圆形裁掉一角。
            contentAlignment = Alignment.Center,
        ) {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.countText?.let { count ->
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.debutText?.let { debut ->
                Text(
                    text = debut,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 类型 / 厂商单元格：三列卡片。
 *
 * NOTE: 这两类没有头像图，纯文字铺满三列会变成一片「没有样式的名单」，和其余页面的容器语言脱节；
 * 所以给每个格子一张 `surfaceContainer` 圆角面（与「设置」「我的」分组同一档），名称固定两行、
 * 条数压底——这样一屏仍是三列十几行，但每一格都有明确的边界与层级。
 */
@Composable
private fun DirectoryTextCell(item: DirectoryItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // NOTE: 固定卡片高度 + 名称与条数分居两端：厂商名一行两行都有（Moody's / Wanz Factory），
        // 让高度随内容变会让同一行的卡片高低不齐；固定高度后条数永远压在同一基线上，看着是刻意的统计卡。
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).height(56.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.countText?.let { count ->
                Text(
                    text = count,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// NOTE: 筛选选项的**值**在 `:core`（纯 JVM 没有资源系统），文案在这里拼：
// 罩杯和出道年份要带单位，身高/年龄本身是中性格式，不需要翻译。
@Composable
private fun sortLabel(value: String): String = when (value) {
    "debut" -> stringResource(R.string.sort_debut)
    "ranking" -> stringResource(R.string.sort_ranking)
    else -> stringResource(R.string.sort_videos)
}

@Composable
private fun sectionTitle(key: String): String = when (key) {
    "height" -> stringResource(R.string.filter_height)
    "cup" -> stringResource(R.string.filter_cup)
    "age" -> stringResource(R.string.filter_age)
    else -> stringResource(R.string.filter_debut)
}

@Composable
private fun optionLabel(sectionKey: String, value: String): String = when (sectionKey) {
    "cup" -> stringResource(R.string.filter_cup_value, value)
    "debut" -> stringResource(R.string.filter_debut_value, value.toIntOrNull() ?: 0)
    "height" -> value.replace("-", " - ") + "cm"
    // 年龄：站点的区间语义（首个 "< 20"、末个 "> 60"），其余原样展示。
    else -> when (value) {
        "0-20" -> "< 20"
        "60-99" -> "> 60"
        else -> value.replace("-", " - ")
    }
}