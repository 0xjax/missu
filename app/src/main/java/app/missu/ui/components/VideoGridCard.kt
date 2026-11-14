package app.missu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import app.missu.data.model.VideoSummary
import coil3.compose.AsyncImage

private val CardSurface = Color(0xFF212225)
private val SubtitleBadge = Color(0xD9991B1B)
private val UncensoredBadge = Color(0xD91E40AF)
private val OtherBadge = Color(0xD91F2937)

/** 首页与搜索页共用的视频卡片：16:9 封面 + 时长 + 语义角标 + 两行标题。 */
@Composable
fun VideoGridCard(video: VideoSummary, onClick: () -> Unit) {
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
