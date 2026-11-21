@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.missu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.missu.R

/**
 * 页面统一顶栏（M3E：「标题 + 可选 up 图标 + 动作槽」）。
 *
 * NOTE: 以前每个页面各自拼一行 `TextButton("← 返回") + Text(标题)`，字号、间距、返回语义都不统一；
 * 换成官方 [TopAppBar] 后标题会按 M3E 字号自动处理，返回键也拿到标准的 up 语义与无障碍标签。
 * NOTE: 返回图标是纯图标按钮，按 M3E 规范挂一个 [PlainTooltip] 说明（长按显示）。
 * WARNING: 容器色必须给 [Color.Transparent]（**不能**给 background）。两个理由：
 * 1. M3 的 `TopAppBar` 会把容器色做一次 `animateColorAsState` 渐变（`AppBarKt` 内部的 targetColor），
 *    而页面其余部分是瞬时换色——切深浅色时这一横条会比整页慢半拍，看起来「一前一后」。
 * 2. 给 Transparent 就不画容器，露出的是页面自己的底色（`PageSurface` 铺满整屏、瞬时切换），
 *    顺带也避开 TopAppBar 默认的 surface 色在纯黑背景上露出的那条灰边。
 * 前提：调用方所在页面必须自己铺不透明底色（本工程所有页面都经 `PageSurface`，成立）。
 */
@Composable
fun PageTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            TooltipIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
            )
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
fun TooltipIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    tonal: Boolean = false,
    active: Boolean = false,
    activeClickable: Boolean = false,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState(),
    ) {
        if (tonal) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier.minimumInteractiveComponentSize(),
                enabled = enabled && (!active || activeClickable),
                colors = if (active) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            ) {
                Icon(
                    icon,
                    contentDescription = contentDescription,
                    tint = tint ?: LocalContentColor.current,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
                Icon(icon, contentDescription = contentDescription, tint = tint ?: LocalContentColor.current)
            }
        }
    }
}
