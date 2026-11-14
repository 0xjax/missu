@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.data.model.HomeSection
import app.missu.data.model.VideoSummary
import app.missu.ui.components.VideoGridCard
import coil3.compose.AsyncImage

@Composable
fun HomeScreen(
    state: HomeUiState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        HomeUiState.Loading -> Centered(modifier) { LoadingIndicator() }

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

        is HomeUiState.Content -> HomeContent(
            sections = state.sections,
            refreshing = refreshing,
            onRefresh = onRefresh,
            onOpen = onOpen,
            onSearch = onSearch,
            modifier = modifier,
        )
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun HomeContent(
    sections: List<HomeSection>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
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
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
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

            sections.forEachIndexed { index, section ->
                item(key = "header-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                // NOTE: 只有第一个板块用 M3E 的 multi-browse 轮播当「推荐位」——其余板块保持网格，
                // 全是轮播会让首页变成一条横向滑动的长龙，找东西反而更慢。
                if (index == 0) {
                    item(key = "carousel-${section.id}", span = { GridItemSpan(maxLineSpan) }) {
                        RecommendedCarousel(videos = section.videos, onOpen = onOpen)
                    }
                } else {
                    items(
                        items = section.videos,
                        key = { video -> "${section.id}-${video.id}" },
                    ) { video ->
                        VideoGridCard(video = video, onClick = { onOpen(video.id) })
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
private fun RecommendedCarousel(videos: List<VideoSummary>, onOpen: (String) -> Unit) {
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
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onOpen(video.id) },
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
