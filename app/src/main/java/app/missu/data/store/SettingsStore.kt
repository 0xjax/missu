package app.missu.data.store

import android.content.Context
import app.missu.data.api.SourceConfig

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
 * 应用设置：语言、明暗模式、是否用系统壁纸取色。
 *
 * NOTE: 语言必须在**第一次网络请求之前**装进 [SourceConfig]，否则首屏会按默认语言拉一轮再换，
 * legacy 就是先读完偏好再挂载界面（见 `src/stores/language.tsx`）。现在这一步由 `MissuRoot`
 * 在组合期算好（见 [AppLanguage.contentLanguage]），本类只负责持久化。
 * TODO: 设置项增加后（播放器默认倍速、列表列数…）统一放这里。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:settings", Context.MODE_PRIVATE)

    /**
     * 应用语言（界面 + 内容）。
     *
     * NOTE: 默认「跟随系统」。纯开发阶段，不保留旧版本 `language` 字段的兼容代码。
     */
    fun appLanguage(): AppLanguage = AppLanguage.fromKey(prefs.getString(KEY_APP_LANGUAGE, null))

    fun setAppLanguage(value: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, value.name).apply()
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
        const val KEY_APP_LANGUAGE = "appLanguage"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_PURE_BLACK = "pureBlack"
    }
}
