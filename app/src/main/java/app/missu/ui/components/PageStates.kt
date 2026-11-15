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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.ui.common.LoadMoreState
import kotlinx.coroutines.flow.distinctUntilChanged

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

/** 脚注的固定高度：三态之间切换时列表底部不跟着抖。 */
private val LoadMoreFooterHeight = 56.dp

/**
 * 自动加载的提前量，单位是**格**。
 *
 * NOTE: 6 格 ≈ 两到三行（列表两列、目录三列），约半屏，对应 legacy 的
 * `onEndReachedThreshold={0.5}`。再大就会一进列表连着取好几页。
 */
private const val LoadMorePrefetchItems = 6

/**
 * 分页列表的脚注：加载中 / 没取到可重试 / 已经到底。
 *
 * NOTE: 三个状态共用一个固定高度——以前加载时换成转圈、失败时冒出个按钮，高度不一样，
 * 列表底部会跳一下；固定以后换的只是中间那点内容。
 * NOTE: 重试用 `TextButton` 而不是 `FilledTonalButton`：脚注里的重试是低强调动作，
 * 一个实心按钮挂在内容末尾比内容本身还显眼。
 */
@Composable
fun LoadMoreFooter(
    nextPage: Int?,
    state: LoadMoreState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().height(LoadMoreFooterHeight),
        contentAlignment = Alignment.Center,
    ) {
        when {
            nextPage == null -> Text(
                text = stringResource(R.string.common_load_end),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state == LoadMoreState.Loading -> LoadingIndicator()

            state == LoadMoreState.Failed -> TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_load_failed))
            }

            // LoadMoreState.Idle：什么都不放，等滚动触发。高度已经占住，所以不会跳。
        }
    }
}

/**
 * 滑到「只剩最后几格」时自动取下一页。
 *
 * NOTE: 以前是底部一个「加载更多」按钮。浏览类列表上它把连续的扫视切成一段段「申请下一屏」，
 * 每次都要求用户先注意到接缝、停手、瞄准；legacy 实现本来就是自动加载（`onEndReached` +
 * 0.5 阈值），这里只是把它恢复回来。
 * NOTE: 内容不满一屏时同一个条件天然成立，不用另写分支——旧按钮时代那会儿，列表只有两条时
 * 按钮就孤零零地摊在屏幕上等着被点。
 * WARNING: `enabled` 必须在 Loading / Failed 时为 false，否则失败之后用户每滚一下就是一次
 * 偷偷的重试，而界面还停在「失败了」的样子。
 */
@Composable
fun LoadMoreOnScroll(
    state: LazyGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val canLoad by rememberUpdatedState(enabled)
    val load by rememberUpdatedState(onLoadMore)
    LaunchedEffect(state) {
        snapshotFlow {
            val layout = state.layoutInfo
            layout.totalItemsCount to (layout.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }
            .distinctUntilChanged()
            .collect { (total, last) ->
                // NOTE: `canLoad` 只在**发信号的那一刻**读一次，不参与信号本身。某一页没加进任何
                // 内容时 total 不变、信号不重放，就不会在原地循环请求；Loading / Failed 期间它
                // 自然是 false，所以既不会重入也不会失败后偷偷重试。
                if (canLoad && total > 0 && total - 1 - last <= LoadMorePrefetchItems) load()
            }
    }
}
