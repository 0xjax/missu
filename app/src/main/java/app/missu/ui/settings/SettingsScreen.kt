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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.store.AppLanguage
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
    appLanguage: AppLanguage,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTopBar(title = stringResource(R.string.settings_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.settings_appearance))

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
                                stringResource(R.string.settings_dynamic_color_note)
                            } else {
                                stringResource(R.string.settings_dynamic_color_unavailable)
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
                    Text(text = stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.bodyLarge)
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
                            text = stringResource(R.string.settings_pure_black_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = pureBlack, onCheckedChange = onPureBlackChange)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_pure_black), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_palette_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            SectionLabel(stringResource(R.string.settings_language))

            // NOTE: 一个设置项同时决定**界面语言与站点内容语言**。不拆成两项：详情页的信息行标签
            // 是站点按内容语言返回的，两个轴分开就会出现「中文界面 + 英文标签」的混排。
            // NOTE: 列表里只出现有 UI 翻译的语言；「跟随系统」时界面回退英文、内容按系统语言走。
            SettingsGroup {
                AppLanguage.entries.forEachIndexed { index, option ->
                    if (index > 0) RowDivider()
                    val selected = option == appLanguage
                    ListItem(
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onAppLanguageChange(option) },
                        ),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = {
                            RadioButton(selected = selected, onClick = null)
                        },
                    ) {
                        Text(text = option.label(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_language_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

/** 语言名用**自身语言**书写（简体中文 / English / 日本語）——比翻译成当前界面语言更好认。 */
@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ZH_HANS -> "简体中文"
    AppLanguage.EN -> "English"
    AppLanguage.JA -> "日本語"
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
