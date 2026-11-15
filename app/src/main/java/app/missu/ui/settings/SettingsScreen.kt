@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.missu.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.missu.R
import app.missu.data.store.AppLanguage
import app.missu.data.store.ColorSource
import app.missu.data.store.ThemeMode
import app.missu.ui.components.GroupDivider
import app.missu.ui.components.GroupSurface
import app.missu.ui.components.PageTopBar
import app.missu.ui.theme.resolveColorScheme
import app.missu.ui.theme.staticColorScheme

/**
 * 设置页：外观（明暗模式 / 主题色）+ 内容语言。
 *
 * NOTE: 选中表达一律用 M3E 的列表项 + 单选控件：整行 `selectable(role = RadioButton)`，控件本身
 * 不再接收点击（onClick = null），否则读屏会看到两个可点节点。
 * NOTE: 之前用硬编码的 `Color(0x14D85A76)` 给整行染色，换主题色就不同步了；现在选中态由控件自己表达。
 */
@Composable
fun SettingsScreen(
    appLanguage: AppLanguage,
    themeMode: ThemeMode,
    colorSource: ColorSource,
    followThemeIcon: Boolean,
    pureBlack: Boolean,
    autoplay: Boolean,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorSourceChange: (ColorSource) -> Unit,
    onFollowThemeIconChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
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

            GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) GroupDivider()
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

            GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_color_source),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    ColorSourcePicker(selected = colorSource, onSelect = onColorSourceChange)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_color_source_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GroupDivider()
                // NOTE: 图标换色只能在应用进后台之后生效（前台禁用启动入口会结束当前 task），
                // 支持文案里要写明这一点，否则用户会以为「点了没反应」。
                ListItem(
                    modifier = Modifier.toggleable(
                        value = followThemeIcon,
                        role = Role.Switch,
                        onValueChange = onFollowThemeIconChange,
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.settings_follow_icon_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = followThemeIcon, onCheckedChange = null)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_follow_icon), style = MaterialTheme.typography.bodyLarge)
                }
                GroupDivider()
                // NOTE: 纯黑是省电偏好，不是默认值——M3 深色规范用 tone 6 的近黑底，容器才有台阶可踩。
                ListItem(
                    modifier = Modifier.toggleable(
                        value = pureBlack,
                        role = Role.Switch,
                        onValueChange = onPureBlackChange,
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
                        Switch(checked = pureBlack, onCheckedChange = null)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_pure_black), style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionLabel(stringResource(R.string.settings_playback))

            // NOTE: 播放相关只有这一项。默认开——本 App 的核心是「点开就看」，关掉是给
            // 「不想在公共场合突然出声」的人一个开关，不是默认体验。
            GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                ListItem(
                    modifier = Modifier.toggleable(
                        value = autoplay,
                        role = Role.Switch,
                        onValueChange = onAutoplayChange,
                    ),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.settings_autoplay_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Switch(checked = autoplay, onCheckedChange = null)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_autoplay), style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionLabel(stringResource(R.string.settings_language))

            // NOTE: 一个设置项同时决定**界面语言与站点内容语言**。不拆成两项：详情页的信息行标签
            // 是站点按内容语言返回的，两个轴分开就会出现「中文界面 + 英文标签」的混排。
            // NOTE: 列表里只出现有 UI 翻译的语言；「跟随系统」时界面回退英文、内容按系统语言走。
            GroupSurface(modifier = Modifier.padding(horizontal = 16.dp)) {
                AppLanguage.entries.forEachIndexed { index, option ->
                    if (index > 0) GroupDivider()
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

@Composable
private fun ColorSource.label(): String = when (this) {
    ColorSource.WALLPAPER -> stringResource(R.string.color_source_wallpaper)
    ColorSource.BRAND -> stringResource(R.string.color_source_brand)
    ColorSource.VIOLET -> stringResource(R.string.color_source_violet)
    ColorSource.BLUE -> stringResource(R.string.color_source_blue)
    ColorSource.TEAL -> stringResource(R.string.color_source_teal)
    ColorSource.AMBER -> stringResource(R.string.color_source_amber)
}

/**
 * 主题色块行。
 *
 * NOTE: 色块边长按可用宽度均分（上限 48dp），不是固定尺寸——固定 48dp 在 360dp 宽的屏幕上会把
 * 最后一块挤出去，而给六块东西加横向滚动，会让人以为后面还有更多。
 * NOTE: 壁纸取色就是这条轴上的一个选项，不是独立开关：它的意思是「整套色板由系统按壁纸生成」。
 * 低版本（< Android 12）没有系统色板，那一块直接不出现。
 */
@Composable
private fun ColorSourcePicker(selected: ColorSource, onSelect: (ColorSource) -> Unit) {
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val options = remember(dynamicSupported) {
        ColorSource.entries.filter { it != ColorSource.WALLPAPER || dynamicSupported }
    }
    BoxWithConstraints {
        val gap = 8.dp
        val size = ((maxWidth - gap * (options.size - 1)) / options.size).coerceAtMost(48.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            options.forEach { source ->
                ColorSourceSwatch(
                    source = source,
                    selected = source == selected,
                    size = size,
                    onClick = { onSelect(source) },
                )
            }
        }
    }
}

/**
 * 单个色块：选中的那块缩进 8dp，并在外圈描一道 `onSurface`，形成「环 + 间隙 + 色面」。
 *
 * NOTE: 色块一律取该色板**深色**下的 primary，不随当前明暗切换：它是最鲜的那一档，放在浅色页面上
 * 也还是同一个颜色的标识；跟着当前模式取色的话，同一块在两种模式下看起来会像两个色板。
 * NOTE: 壁纸那块用系统色板的 primary/secondary/tertiary 做扫掠渐变——单色填不出「跟随系统」。
 */
@Composable
private fun ColorSourceSwatch(
    source: ColorSource,
    selected: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    val label = source.label()
    val brush = source.swatchBrush()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) size - 8.dp else size)
                .clip(CircleShape)
                .background(brush),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        }
    }
}

@Composable
private fun ColorSource.swatchBrush(): Brush = when (this) {
    ColorSource.WALLPAPER -> {
        val scheme = resolveColorScheme(LocalContext.current, darkTheme = true, this)
        Brush.sweepGradient(listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primary))
    }
    else -> SolidColor(staticColorScheme(darkTheme = true).primary)
}

/** 语言名用**自身语言**书写（简体中文 / 繁體中文 / English / 日本語）——比翻译成当前界面语言更好认。 */@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_system)
    AppLanguage.ZH_HANS -> "简体中文"
    AppLanguage.ZH_HANT -> "繁體中文"
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
