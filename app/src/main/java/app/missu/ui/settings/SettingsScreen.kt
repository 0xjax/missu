package app.missu.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.missu.data.api.CONTENT_LANGUAGES
import app.missu.ui.components.PageTopBar

/**
 * 设置页：目前只有「内容语言」一项。
 *
 * NOTE: 选中表达改成 M3E 的列表项 + 单选控件：整行 `selectable(role = RadioButton)`，行本身可点，
 * 单选按钮不再单独接收点击（onClick = null），否则读屏会看到两个可点节点。
 * NOTE: 之前用硬编码的 `Color(0x14D85A76)` 给整行染色，换主题色就不同步了；现在选中态由控件自己表达。
 */
@Composable
fun SettingsScreen(
    language: String,
    onLanguageChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = "设置", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "内容语言",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
            )

            // NOTE: 语言写进站点路径（/cn、/en…），切换后首页/搜索/详情的请求全部跟着变。
            Surface(
                shape = MaterialTheme.shapes.largeIncreased,
                // NOTE: surfaceContainerLow 在纯黑底上会画成纯黑（实测），分组容器会看不见；用能看见的最低一档。
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Column {
                    CONTENT_LANGUAGES.forEachIndexed { index, option ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        val selected = option.value == language
                        ListItem(
                            modifier = Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onLanguageChange(option.value) },
                            ),
                            // NOTE: 列表项默认容器色取主题的 surface（纯黑），会盖掉分组容器的底色。
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            trailingContent = {
                                RadioButton(selected = selected, onClick = null)
                            },
                        ) {
                            Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "「繁體中文」是站点默认语言，请求不带语言前缀。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
