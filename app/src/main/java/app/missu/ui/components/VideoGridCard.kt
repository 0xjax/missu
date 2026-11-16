package app.missu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.model.VideoSummary
import app.missu.ui.preview.LocalVideoPreview
import coil3.compose.AsyncImage

private val SubtitleBadge = Color(0xD9991B1B)
private val UncensoredBadge = Color(0xD91E40AF)
private val OtherBadge = Color(0xD91F2937)

/** 全局视频卡片列数；设置只改这个值，各列表保留自己的数据与滚动状态。 */
val LocalVideoListColumns = compositionLocalOf { 2 }

/**
 * 首页与各视频列表共用的卡片：16:9 封面 + 时长 + 语义角标 + 两行标题。
 * 长按出预览浮层——只有卡片自带 `previewUrl` 时才有，整理模式下由调用方关掉。
 *
 * NOTE: 封面圆角与占位色改取 M3E 的形状/颜色 token（`shapes.medium` + `surfaceContainerHigh`），
 * 不再写死 10dp 与 `#212225`——这两处以前在换主题时不会跟着变，和四周的官方组件对不齐。
 * NOTE: 角标是压在封面上的语义色（字幕红 / 无码蓝），不属于主题色板，保持写死。
 */
@Composable
fun VideoGridCard(
    video: VideoSummary,
    onClick: () -> Unit,
    // NOTE: 整理模式下卡片是「选中/取消」，长按再弹预览会和选择手势打架，由调用方关掉。
    previewOnLongPress: Boolean = true,
    // NOTE: 同一页里可能重复出现同一部影片时（首页多板块）带上来源标签，见 [heroKeyFor]。
    heroTag: String = "",
) {
    val preview = LocalVideoPreview.current

    // NOTE: 长按的触觉由 `combinedClickable` 自己发（Android 16 上实测一次长按 = 一次
    // `HapticFeedbackConstants.LONG_PRESS`，`dumpsys vibrator_manager` 里一条 HEAVY_CLICK），
    // 这里不要再补一次——补了就是两下。
    val openPreview: (() -> Unit)? = if (previewOnLongPress && preview != null && video.previewUrl != null) {
        { preview.open(video) }
    } else {
        null
    }
    val longPressLabel = if (openPreview != null) stringResource(R.string.preview_open) else null

    Column(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClickLabel = longPressLabel,
            onLongClick = openPreview,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                // NOTE: 封面就是详情页播放器框的共享容器——点卡片时它自己长成播放器，
                // 所以共享 bounds 挂在这一层，而不是整张卡片上（文字不参与这次转场）。
                .videoHero(heroKeyFor(heroTag, video.id), MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            // NOTE: 没有封面时给一个占位图标，而不是留一块空灰面——空面在列表里读起来像"加载失败"，
            // 而解析层已经会把没有 <img> 的伪卡片滤掉，走到这里的都是真卡片（封面缺失属于站点数据问题）。
            if (video.thumbnailUrl != null) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // NOTE: 角标来自卡片 HTML（中文字幕 / 無碼影片…），按语义分色，不做标签白名单。
            video.badges.firstOrNull()?.let { badge ->
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(badgeColor(badge), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            video.duration?.let { duration ->
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            style = if (LocalVideoListColumns.current == 1) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodySmall
            },
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
