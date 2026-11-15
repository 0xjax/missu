package app.missu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 抽屉卡片的左右边距。 */
private val SheetSideMargin = 16.dp

/** 抽屉卡片的底部边距：和左右一起，让卡片四周都透出页面。 */
private val SheetBottomMargin = 12.dp


/**
 * 全应用统一的抽屉：**一张浮起来的圆角卡片**，四周留边距，不贴着屏幕底边和左右边。
 *
 * NOTE: 为什么不直接用 `ModalBottomSheet` 的默认外观：默认是贴底通栏的一整块白板，左右和底部
 * 都顶到屏幕边。留出边距之后，卡片四周都还能看到底下的页面/画面，抽屉读起来是"浮在上面的一层"，
 * 而不是"切了一页"——沉浸感就是这个差别（2026-11-16 对照 YouTube 定的）。
 * NOTE: 抽屉本体的行为完全不变：仍是 `ModalBottomSheet`，拖动关闭、点外面关闭、返回键关闭都原生；
 * 只是本色透明（`containerColor = Color.Transparent`）、自带把手关掉（`dragHandle = null`），
 * 改由里面的卡片自己画。
 * NOTE: 使用统一的 `rememberBottomSheetState`，初始保持 Hidden，由原生展开/关闭动画接管。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissuBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        sheetMaxWidth = sheetMaxWidth,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SheetSideMargin, vertical = SheetBottomMargin),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // 自己的拖拽提示条：和 M3 默认把手同一个尺寸与颜色档。
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
                content()
            }
        }
    }
}
