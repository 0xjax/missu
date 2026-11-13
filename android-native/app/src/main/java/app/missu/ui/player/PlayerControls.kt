package app.missu.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.PlayerDefaults

/**
 * 播放器控件层：中心「快退 / 播放暂停 / 快进」+ 底部进度条，来自官方 Material3 组件
 * [PlayerDefaults]（按钮尺寸、渐变底、无障碍语义都由它提供）。
 *
 * 我们只在右侧槽位补两件事：倍速、全屏。
 *
 * NOTE: 只用官方槽位，不复制它内部的 internal 尺寸常量——那些不是公开 API，抄了就会随版本漂移。
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerOverlay(
    player: Player,
    visible: Boolean,
    speed: Float,
    isFullscreen: Boolean,
    onSpeedClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.Center)) {
            PlayerDefaults.CenterControls(
                player = player,
                visible = visible,
                // 单集播放用不到「上一集 / 下一集」，把外侧两个槽位留空。
                backSecondary = {},
                forwardSecondary = {},
            )
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            PlayerDefaults.BottomControls(
                player = player,
                visible = visible,
                above = {},
                // NOTE: 倍速/全屏放在进度条下方的 below 槽位——放 above 会浮到画面中间挡住内容。
                below = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // NOTE: 菜单本体不放在这里——它挂在会被自动隐藏的控件树里会跟着一起被销毁
                        // （实测：菜单一闪就关）。这里只报告点击，菜单由 [ExoVideoPlayer] 持有。
                        OverlayChip(text = formatSpeed(speed), onClick = onSpeedClick)
                        Spacer(Modifier.width(4.dp))
                        OverlayChip(
                            text = if (isFullscreen) "退出全屏" else "全屏",
                            onClick = onToggleFullscreen,
                        )
                    }
                },
            )
        }
    }
}


/** 倍速菜单本体：由播放器持有，避免跟随控件的自动隐藏一起被销毁。 */
@Composable
fun SpeedMenu(
    expanded: Boolean,
    speed: Float,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SPEEDS.forEach { option ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = formatSpeed(option),
                        color = if (option == speed) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                },
                onClick = { onSpeedChange(option) },
            )
        }
    }
}
@Composable
private fun OverlayChip(text: String, onClick: () -> Unit) {
    // NOTE: 视觉上做成小胶囊，但仍用 minimumInteractiveComponentSize 保证 48dp 触摸目标（MD3 无障碍要求）。
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x66000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
