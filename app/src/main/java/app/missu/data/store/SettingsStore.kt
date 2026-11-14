package app.missu.data.store

import android.content.Context
import app.missu.data.api.SourceConfig
import app.missu.data.api.isSupportedLanguage

/** 明暗模式：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * 应用设置：内容语言、明暗模式、是否用系统壁纸取色。
 *
 * NOTE: 语言必须在**第一次网络请求之前**装进 [SourceConfig]，否则首屏会按默认语言拉一轮再换，
 * legacy 就是先读完偏好再挂载界面（见 `src/stores/language.tsx`）。
 * TODO: 设置项增加后（播放器默认倍速、列表列数…）统一放这里。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:settings", Context.MODE_PRIVATE)

    fun language(): String {
        val stored = prefs.getString(KEY_LANGUAGE, null)
        return if (stored != null && isSupportedLanguage(stored)) stored else DEFAULT_LANGUAGE
    }

    fun setLanguage(value: String) {
        if (!isSupportedLanguage(value)) return
        prefs.edit().putString(KEY_LANGUAGE, value).apply()
        SourceConfig.contentLanguage = value
    }

    /** 把已保存的语言装进 [SourceConfig]，在创建界面之前调用一次。 */
    fun applyStoredLanguage() {
        SourceConfig.contentLanguage = language()
    }

    fun themeMode(): ThemeMode = ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))

    fun setThemeMode(value: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
    }

    /** 壁纸取色。默认关：品牌粉是本 App 的识别度，不能让壁纸随手替掉。 */
    fun dynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    /**
     * 深色模式下是否用纯黑背景。
     *
     * NOTE: 默认关。M3 的深色规范底色是 tone 6（接近深灰）而不是纯黑：OLED 上黑底与亮像素相邻会拖影，
     * 纯黑也留不出「更亮的容器」这一档层级。纯黑只作为省电偏好保留。
     */
    fun pureBlack(): Boolean = prefs.getBoolean(KEY_PURE_BLACK, false)

    fun setPureBlack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_PURE_BLACK = "pureBlack"
        const val DEFAULT_LANGUAGE = "cn"
    }
}
