package app.missu.ui.history

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.store.HistoryEntry
import coil3.compose.AsyncImage
import app.missu.ui.components.EmptyHint
import app.missu.ui.components.LocalVideoListColumns
import app.missu.ui.components.PageTopBar
import app.missu.ui.components.videoHero

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onOpen: (id: String, thumbnail: String?) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(
            title = stringResource(R.string.history_title),
            onBack = onBack,
            actions = {
                // NOTE: 把操作放进 actions，而不是在标题后面塞 Spacer(weight) —— 那是手搓顶栏的写法。
                if (entries.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
                }
            },
        )

        if (entries.isEmpty()) {
            EmptyHint(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.history_empty_title),
                subtitle = stringResource(R.string.history_empty_subtitle),
            )
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(LocalVideoListColumns.current),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items = entries, key = { it.id }) { entry ->
                    HistoryCard(entry = entry, onClick = { onOpen(entry.id, entry.thumbnailUrl) })
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(entry: HistoryEntry, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .videoHero(entry.id, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 进度条压在封面底部：一眼看出看到哪儿了。
            val total = parseDurationMs(entry.duration)
            val progress = if (total > 0) (entry.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0x66000000),
                    )
                }
                Text(
                    text = "${formatTime(entry.positionMs)} / ${entry.duration ?: "--:--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.title,
            style = if (LocalVideoListColumns.current == 1) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodySmall
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 把 `1:59:09` 这种时长文案转成毫秒，用于算进度比例。 */
private fun parseDurationMs(duration: String?): Long {
    val parts = duration?.split(':')?.mapNotNull { it.trim().toLongOrNull() } ?: return 0L
    return when (parts.size) {
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        2 -> (parts[0] * 60 + parts[1]) * 1000
        else -> 0L
    }
}
