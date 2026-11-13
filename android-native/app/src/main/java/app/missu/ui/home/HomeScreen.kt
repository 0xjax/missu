package app.missu.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.missu.data.model.HomeSection
import app.missu.data.model.VideoSummary
import coil3.compose.AsyncImage

private val CardSurface = Color(0xFF212225)
private val SubtitleBadge = Color(0xD9991B1B)
private val UncensoredBadge = Color(0xD91E40AF)
private val OtherBadge = Color(0xD91F2937)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
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

        is HomeUiState.Content -> HomeGrid(state.sections, onOpen, modifier)
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun HomeGrid(sections: List<HomeSection>, onOpen: (String) -> Unit, modifier: Modifier) {
    LazyVerticalGrid(
        // NOTE: 现在没有 Scaffold 提供 inset，状态栏/导航栏要自己避开，否则标题会压在状态栏下面。
        modifier = modifier.statusBarsPadding().navigationBarsPadding(),
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
                VideoCard(video = video, onClick = { onOpen(video.id) })
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoSummary, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(CardSurface),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // NOTE: 角标来自卡片 HTML（中文字幕 / 無碼影片…），按语义分色，不做标签白名单。
            video.badges.firstOrNull()?.let { badge ->
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(badgeColor(badge), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            video.duration?.let { duration ->
                Text(
                    text = duration,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun badgeColor(label: String): Color = when {
    Regex("字幕|subtitle", RegexOption.IGNORE_CASE).containsMatchIn(label) -> SubtitleBadge
    Regex("碼|码|uncensored", RegexOption.IGNORE_CASE).containsMatchIn(label) -> UncensoredBadge
    else -> OtherBadge
}
