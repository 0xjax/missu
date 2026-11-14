package app.missu.data.store

import android.content.res.Configuration
import java.util.Locale

/**
 * 应用语言：**界面语言和内容语言是同一个选择**。
 *
 * 为什么不分成两个设置项：详情页的信息行标签（发行日期 / Release date / 発売日…）是站点按内容语言
 * 返回的，我们只透传。两个轴一旦分开，同一页就会出现"中文界面 + 英文标签"的混排。
 *
 * NOTE: 站点支持 7 种内容语言，但界面文案需要**翻译**。列表里只出现有翻译的语言，
 * 否则选完会得到"日文内容 + 英文界面"的错配。加一种语言 = 加 `values-xx/` + 这里一行。
 */
enum class AppLanguage {
    SYSTEM,
    ZH_HANS,
    ZH_HANT,
    EN,
    JA,
    ;

    companion object {
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/** 界面 locale。「跟随系统」时用系统语言，没有翻译就回退英文。 */
fun AppLanguage.uiLocale(config: Configuration): Locale {
    val system = config.locales[0]
    return when (this) {
        AppLanguage.ZH_HANS -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.ZH_HANT -> Locale.TRADITIONAL_CHINESE
        AppLanguage.EN -> Locale.ENGLISH
        AppLanguage.JA -> Locale.JAPANESE
        AppLanguage.SYSTEM -> when (system?.language) {
            "zh" -> if (system.isTraditionalChinese) Locale.TRADITIONAL_CHINESE else Locale.SIMPLIFIED_CHINESE
            "ja" -> Locale.JAPANESE
            else -> Locale.ENGLISH
        }
    }
}

/**
 * 站点内容语言（路径段）。
 *
 * NOTE: 界面语言回退英文，内容语言却**按系统语言走**（站点支持就跟）：界面英文还能忍，
 * 标题和标签全变英文就没法用了。这条只影响"系统语言我们没有翻译"的情况。
 * 站点默认语言是繁體（`tw`，请求不带前缀），所以 zh 要按地区/文字分叉。
 */
fun AppLanguage.contentLanguage(config: Configuration): String {
    val system = config.locales[0]
    return when (this) {
        AppLanguage.ZH_HANS -> "cn"
        AppLanguage.ZH_HANT -> "tw"
        AppLanguage.EN -> "en"
        AppLanguage.JA -> "ja"
        AppLanguage.SYSTEM -> when (system?.language) {
            "zh" -> if (system.isTraditionalChinese) "tw" else "cn"
            "ja" -> "ja"
            "ko" -> "ko"
            "ms" -> "ms"
            "th" -> "th"
            else -> "en"
        }
    }
}

/** zh-Hant 的判定：显式文字标签，或台湾/香港/澳门的地区码。 */
private val Locale.isTraditionalChinese: Boolean
    get() = script == "Hant" || country in setOf("TW", "HK", "MO")
