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
import androidx.compose.ui.text.style.TextOverflow

/**
 * 页面统一顶栏（M3E：「标题 + 可选 up 图标 + 动作槽」）。
 *
 * NOTE: 以前每个页面各自拼一行 `TextButton("← 返回") + Text(标题)`，字号、间距、返回语义都不统一；
 * 换成官方 [TopAppBar] 后标题会按 M3E 字号自动处理，返回键也拿到标准的 up 语义与无障碍标签。
 * NOTE: 返回图标是纯图标按钮，按 M3E 规范挂一个 [PlainTooltip] 说明（长按显示）。
 * NOTE: [onBack] 可空——「我的」这类一级页签没有上一页，只借用同一条栏放标题，不要伪造返回键。
 * WARNING: 容器色显式取 [MaterialTheme.colorScheme] 的 background，否则 TopAppBar 默认用 surface 色，
 * 在纯黑背景上会露出一条灰边。
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
                    tooltip = { PlainTooltip { Text("返回") } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}
