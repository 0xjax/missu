@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.data.model.HomeSection
import app.missu.data.model.VideoSummary
import app.missu.R
import app.missu.ui.common.text
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.ErrorHint
import app.missu.ui.components.LoadingHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageStateTransition
import app.missu.ui.components.VideoGridCard
import app.missu.ui.components.heroKeyFor
import app.missu.ui.components.videoHero
import app.missu.ui.search.LocalSearchBarInset
import coil3.compose.AsyncImage

/**
 * 每个板块在首页只露出三行：双列 6 条，单列 3 条。
 *
 * NOTE: 站点每个板块给 16 条上下，全铺出来首页就是三十多行、要翻约 6000dp 才到最后一个板块。
 * 首页只当「预览」，完整列表走板块标题行的「更多」进 `/list`（复用已有分页页）。
 * NOTE: 首页**不请求** `/new` 之类的列表接口——4 个板块来自同一次 `GET /`（站点把预览块直出在首页
 * HTML 里），三行预览是我们自己的上限；「更多」用的是站点那一行链接的路径。
 * 所以首页与完整页是**导读关系**，不是同一份接口数据的两种截断。
 */
private const val SECTION_PREVIEW_ROWS = 3

@Composable
fun HomeScreen(
    state: HomeUiState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    onOpenList: (path: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // NOTE: 用 fade through 交接状态，避免「转圈一消失、整页内容啪地出现」的硬切。
    PageStateTransition(state = state, modifier = modifier.fillMaxSize()) { current ->
        when (current) {
            HomeUiState.Loading -> LoadingHint()

            HomeUiState.Empty -> EmptyHint(
                icon = Icons.Filled.Home,
                title = stringResource(R.string.home_empty_title),
                subtitle = stringResource(R.string.home_empty_subtitle),
            )

            is HomeUiState.Error -> ErrorHint(current.message.text(), onRetry)

            is HomeUiState.Content -> HomeContent(
                sections = current.sections,
                refreshing = refreshing,
                onRefresh = onRefresh,
                onOpen = onOpen,
                onOpenList = onOpenList,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
    onOpenList: (path: String, title: String) -> Unit,
    modifier: Modifier,
) {
    // NOTE: 搜索胶囊浮在内容之上（见 SearchHost），下拉刷新的指示器得让开它那一条，
    // 否则转圈会被胶囊盖住。
    val barInset = LocalSearchBarInset.current
    val columns = LocalVideoListColumns.current
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = barInset),
            )
        },
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(columns),
            // NOTE: 顶部让出搜索胶囊那一条（见 `LocalSearchBarInset`）：胶囊浮在内容之上、
            // 不占布局，静止时第一行靠这份内边距落在它下面，滚动时内边距跟着内容滚掉、
            // 卡片从胶囊底下穿过去。
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = barInset,
                bottom = 32.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // NOTE: 搜索入口不在这里——它由 SearchHost 在页面顶部渲染成真正的 `SearchBar`，
            // 点一下就地展开。之前这里是个 44dp / 10dp 圆角的假框，跟搜索页的 56dp 胶囊对不上。
            sections.forEachIndexed { index, section ->
                item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        // NOTE: 板块默认只给一段预览，「更多」进已有的 /list 页看完整列表。
                        // 不这么做的话：首页实测 64 张卡片 = 32 行，要翻约 6000dp 才看得到最后一个板块。
                        section.morePath?.let { path ->
                            TextButton(onClick = { onOpenList(path, section.title) }) {
                                Text(stringResource(R.string.home_more))
                            }
                        }
                    }
                }

                // NOTE: 只有第一个板块用 M3E 的 multi-browse 轮播当「推荐位」——其余板块保持网格，
                // 全是轮播会让首页变成一条横向滑动的长龙，找东西反而更慢。
                if (index == 0) {
                    item(key = "carousel-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                        RecommendedCarousel(videos = section.videos, heroTag = section.id, onOpen = onOpen)
                    }
                } else {
                    items(
                        items = section.videos.take(SECTION_PREVIEW_ROWS * columns),
                        key = { video -> "${section.id}-${video.id}" },
                    ) { video ->
                        // NOTE: `heroTag` = 板块 id：同一部影片可能同时在两个板块里，卡片位置不同、
                        // 共享元素却不能混（见 heroKeyFor）。
                        VideoGridCard(
                            video = video,
                            heroTag = section.id,
                            onClick = { onOpen(video.id, video.thumbnailUrl, section.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 推荐位：M3E 的 `HorizontalMultiBrowseCarousel`。
 *
 * NOTE: 滚动时项会自动改变大小并吸附（这是 M3E carousel 的核心行为，别关掉）；
 * 项内容用「封面铺满 + 底部渐变 + 标题」而不是 16:9 卡片——轮播项的尺寸在被组件改写，
 * 固定宽高比的卡片会被拉伸变形。
 */
@Composable
private fun RecommendedCarousel(
    videos: List<VideoSummary>,
    heroTag: String,
    onOpen: (id: String, thumbnail: String?, heroTag: String) -> Unit,
) {
    if (videos.isEmpty()) return
    // NOTE: itemCount 是 lambda（延迟读，避免每帧重组），preferredItemWidth 才是大项宽度上限。
    val state = rememberCarouselState(itemCount = { videos.size })

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = 264.dp,
        modifier = Modifier.fillMaxWidth().height(200.dp),
        flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(state),
    ) { index ->
        val video = videos[index]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .videoHero(heroKeyFor(heroTag, video.id), MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onOpen(video.id, video.thumbnailUrl, heroTag) },
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0x00000000), Color(0xD9000000)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                video.duration?.let { duration ->
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
