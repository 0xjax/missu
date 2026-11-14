@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.settings

import android.os.Build
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.missu.data.api.CONTENT_LANGUAGES
import app.missu.data.store.ThemeMode
import app.missu.ui.components.PageTopBar

/**
 * 设置页：外观（明暗模式 / 壁纸取色）+ 内容语言。
 *
 * NOTE: 选中表达一律用 M3E 的列表项 + 单选控件：整行 `selectable(role = RadioButton)`，控件本身
 * 不再接收点击（onClick = null），否则读屏会看到两个可点节点。
 * NOTE: 之前用硬编码的 `Color(0x14D85A76)` 给整行染色，换主题色就不同步了；现在选中态由控件自己表达。
 */
@Composable
fun SettingsScreen(
    language: String,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
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
            SectionLabel("外观")

            SettingsGroup {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) RowDivider()
                    val selected = mode == themeMode
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onThemeModeChange(mode) },
                        ),
                        // NOTE: 列表项默认容器色取主题的 surface，会盖掉分组容器的底色。
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                    ) {
                        Text(text = mode.label(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            SettingsGroup {
                ListItem(
                    modifier = Modifier.then(
                        if (dynamicSupported) {
                            Modifier.selectable(
                                selected = dynamicColor,
                                role = Role.Switch,
                                onClick = { onDynamicColorChange(!dynamicColor) },
                            )
                        } else {
                            Modifier
                        },
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    supportingContent = {
                        Text(
                            text = if (dynamicSupported) {
                                "用系统壁纸取色；关掉时始终使用 Missu 的品牌粉"
                            } else {
                                "需要 Android 12 及以上"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = dynamicColor && dynamicSupported,
                            onCheckedChange = if (dynamicSupported) onDynamicColorChange else null,
                            enabled = dynamicSupported,
                        )
                    },
                ) {
                    Text(text = "动态取色", style = MaterialTheme.typography.bodyLarge)
                }
                RowDivider()
                // NOTE: 纯黑是省电偏好，不是默认值——M3 深色规范用 tone 6 的近黑底，容器才有台阶可踩。
                ListItem(
                    modifier = Modifier.selectable(
                        selected = pureBlack,
                        role = Role.Switch,
                        onClick = { onPureBlackChange(!pureBlack) },
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    supportingContent = {
                        Text(
                            text = "仅深色模式生效。纯黑更省电，但分组容器的层级会被压平",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = pureBlack, onCheckedChange = onPureBlackChange)
                    },
                ) {
                    Text(text = "纯黑背景", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Missu 的色板由品牌粉推导：深色下主色就是品牌色本身，浅色下用深一档保证白字可读。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            SectionLabel("内容语言")

            // NOTE: 语言写进站点路径（/cn、/en…），切换后首页/搜索/详情的请求全部跟着变。
            SettingsGroup {
                CONTENT_LANGUAGES.forEachIndexed { index, option ->
                    if (index > 0) RowDivider()
                    val selected = option.value == language
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onLanguageChange(option.value) },
                        ),
                        // NOTE: 列表项默认容器色取主题的 surface，会盖掉分组容器的底色。
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = {
                            RadioButton(selected = selected, onClick = null)
                        },
                    ) {
                        Text(text = option.label, style = MaterialTheme.typography.bodyLarge)
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

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        // NOTE: surfaceContainerLow 在纯黑底上会画成纯黑（实测），分组容器会看不见；用能看见的最低一档。
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
