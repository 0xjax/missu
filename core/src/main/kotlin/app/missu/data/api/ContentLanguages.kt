package app.missu.data.api

/** 一个可选的内容语言。`value` 是站点路径段，`tw` 比较特殊：没有前缀，是站点默认的繁體中文。 */
data class ContentLanguageOption(val value: String, val label: String)

/** 与源站语言菜单一致（legacy `src/constants/languages.ts` 同一份清单）。 */
val CONTENT_LANGUAGES = listOf(
    ContentLanguageOption("tw", "繁體中文"),
    ContentLanguageOption("cn", "简体中文"),
    ContentLanguageOption("en", "English"),
    ContentLanguageOption("ja", "日本語"),
    ContentLanguageOption("ko", "한국어"),
    ContentLanguageOption("ms", "Melayu"),
    ContentLanguageOption("th", "ไทย"),
)

fun languageLabel(value: String): String =
    CONTENT_LANGUAGES.firstOrNull { it.value == value }?.label ?: value

fun isSupportedLanguage(value: String): Boolean =
    CONTENT_LANGUAGES.any { it.value == value }
