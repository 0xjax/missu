package app.missu.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.missu.data.model.DetailInfoRow
import app.missu.data.model.VideoDetail
import app.missu.ui.player.ExoVideoPlayer

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
        }

        when (state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is DetailUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

            is DetailUiState.Content -> DetailContent(state.detail)
        }
    }
}

@Composable
private fun DetailContent(detail: VideoDetail) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "player") {
            val playbackUrl = detail.playbackUrl
            if (playbackUrl != null) {
                ExoVideoPlayer(
                    url = playbackUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂时无法提取播放地址", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item(key = "title") {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item(key = "meta") {
            Text(
                text = listOfNotNull(detail.duration, detail.releaseDate).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        items(detail.info, key = { row -> row.label }) { row ->
            InfoRow(row)
        }

        detail.description?.let { description ->
            item(key = "description") {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(row: DetailInfoRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            // NOTE: 标签在页面里自带冒号（如「发行日期:」），这里只补一个空格。
            text = row.label + " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            // NOTE: 可点击的值（女优/类型/厂商…）暂时按纯文本显示，跳列表等做 /latest 时再接。
            text = row.values.joinToString("，") { it.text },
            style = MaterialTheme.typography.bodySmall,
            color = Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
    }
}
