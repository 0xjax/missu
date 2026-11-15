package app.missu.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 设置、账号与详情共用的分组容器。 */
@Composable
fun GroupSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
        content = { Column(content = content) },
    )
}

/** 分组内分隔线；图标列表把起点让到图标之后，纯文本列表从内容边缘开始。 */
@Composable
fun GroupDivider(start: Dp = 16.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = start, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
