@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.missu.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.missu.R

/**
 * 页面统一顶栏（M3E：「标题 + 可选 up 图标 + 动作槽」）。
 *
 * NOTE: 以前每个页面各自拼一行 `TextButton("← 返回") + Text(标题)`，字号、间距、返回语义都不统一；
 * 换成官方 [TopAppBar] 后标题会按 M3E 字号自动处理，返回键也拿到标准的 up 语义与无障碍标签。
 * NOTE: 返回图标是纯图标按钮，按 M3E 规范挂一个 [PlainTooltip] 说明（长按显示）。
 * NOTE: [onBack] 可空——「我的」这类一级页签没有上一页，只借用同一条栏放标题，不要伪造返回键。
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
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val back = onBack
    TopAppBar(
        modifier = modifier,
        title = {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            if (back != null) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.common_back)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            }
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
