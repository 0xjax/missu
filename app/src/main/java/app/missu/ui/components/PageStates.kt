@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.missu.R

/**
 * 页面级三态（加载 / 空 / 错误）的统一表达。
 *
 * NOTE: 以前每个页面各写一份 `CenteredHint`，字号、间距、图标、重试按钮的形状都不一致；
 * 统一到这里后，M3E 的 `LoadingIndicator` 与 `FilledTonalButton` 只在一个地方改。
 * 细节态（详情页正文内的行内错误等）仍在各自页面内处理，不走这里。
 */

/**
 * 页面状态之间的过渡：M3 的 **fade through**。
 *
 * NOTE: 之前「转圈」到「内容」是一次硬切——加载指示一消失，整页内容就"啪"地出现。M3 对
 * 这种**表面级变化**给的动效就是 fade through：旧内容 90ms 淡出，新内容等 90ms 再 210ms 淡入，
 * 并伴随极轻微的放大（0.96 → 1）。两段不重叠，所以看不到两块内容同时存在的重影。
 * NOTE: `contentKey` 用状态的**类型**——分页时 Content→Content 只是数据变了，不该整页重放动画。
 */
@Composable
fun <S : Any> PageStateTransition(
    state: S,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        contentKey = { it::class },
        transitionSpec = {
            (
                fadeIn(animationSpec = tween(durationMillis = 210, delayMillis = 90)) +
                    scaleIn(animationSpec = tween(durationMillis = 210, delayMillis = 90), initialScale = 0.96f)
                ).togetherWith(
                fadeOut(animationSpec = tween(durationMillis = 90)),
            )
        },
        label = "page-state",
        content = content,
    )
}

/** 加载态：居中的 M3E `LoadingIndicator`。 */
@Composable
fun LoadingHint(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/**
 * 空态：图标 + 标题 + 说明，可选一个主行动。
 *
 * NOTE: 空态不是 M3E 的官方组件（官方只有加载与进度），所以按 feedback 规范自绘：
 * 图标降一档对比度（onSurfaceVariant），行动用 `FilledTonalButton`——空态的行动是"建议"而非主操作。
 */
@Composable
fun EmptyHint(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** 错误态：M3E 要求错误必须可重试，所以重试按钮不是可选项。 */
@Composable
fun ErrorHint(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

/**
 * 分页列表的「加载更多」脚注。
 *
 * NOTE: 加载中不再显示按钮，直接换成 `LoadingIndicator`——按钮留在原地再叠一个转圈会让
 * 布局高度跳变（列表底部跟着抖）。
 */
@Composable
fun LoadMoreFooter(loading: Boolean, onLoadMore: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            LoadingIndicator()
        } else {
            FilledTonalButton(onClick = onLoadMore) { Text(stringResource(R.string.common_load_more)) }
        }
    }
}

/** 分组列表之间的细分隔线：左侧留出图标位，右边贴边（M3E 列表项的分隔规则）。 */
@Composable
fun ListRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
