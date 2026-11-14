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
 * 主题色源：壁纸取色 + 四套静态色板（品牌粉是默认）。
 *
 * NOTE: 壁纸取色和其它色块是**同一个轴**——它只是「这一套色板由系统按壁纸生成」。以前它是独立的
 * 开关，会和色板选择叠成两个控件（开关开着时点色块没反应），合并后语义只有一条：当前用哪套色板。
 */
enum class ColorSource {
    WALLPAPER,
    BRAND,
    VIOLET,
    BLUE,
    TEAL,
    AMBER,
    ;

    companion object {
        fun fromKey(key: String?): ColorSource? = entries.firstOrNull { it.name == key }
    }
}

/**
 * 应用设置：语言、明暗模式、主题色源。
 *
 * NOTE: 语言必须在**第一次网络请求之前**装进 [SourceConfig]，否则首屏会按默认语言拉一轮再换，
 * legacy 就是先读完偏好再挂载界面（见 `src/stores/language.tsx`）。现在这一步由 `MissuRoot`
 * 在组合期算好（见 [AppLanguage.contentLanguage]），本类只负责持久化。
 * TODO: 还有设置项要加时（列表列数…）统一放这里。
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

    /**
     * 主题色源。
     *
     * NOTE: 旧的 `dynamicColor` 布尔是一次性的迁移入口——原来开关开着的人升级后应该还是壁纸取色，
     * 不该悄悄变回粉色。除此之外不保留任何旧字段的兼容代码。
     */
    fun colorSource(): ColorSource {
        val stored = ColorSource.fromKey(prefs.getString(KEY_COLOR_SOURCE, null))
            ?: return if (prefs.getBoolean(KEY_DYNAMIC_COLOR, false)) ColorSource.WALLPAPER else ColorSource.BRAND
        return stored
    }

    fun setColorSource(value: ColorSource) {
        prefs.edit().putString(KEY_COLOR_SOURCE, value.name).remove(KEY_DYNAMIC_COLOR).apply()
    }

    /**
     * 桌面图标**应该**用哪个色板（实际启停由 `LauncherIcons` 做，且只在应用进后台时应用）。
     *
     * NOTE: 开关关着就永远是品牌粉——图标换色是「贴着边界的美化」，默认不打扰。
     */
    fun desiredIconSource(): ColorSource =
        if (followThemeIcon()) colorSource() else ColorSource.BRAND

    /**
     * 桌面图标是否跟随主题色。
     *
     * NOTE: 默认关。切换图标只能在应用**离开前台之后**生效——禁用当前启动入口会结束它所在的 task。
     * 另外「跟随壁纸」没有静态图标可用（系统色是运行时算的），那一档保持品牌粉。
     */
    fun followThemeIcon(): Boolean = prefs.getBoolean(KEY_FOLLOW_THEME_ICON, false)

    fun setFollowThemeIcon(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOLLOW_THEME_ICON, enabled).apply()
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

    /**
     * 播放倍速（1f = 原速）。全局一个值，对所有影片生效。
     *
     * NOTE: legacy 就把它放在偏好里（`stores/preferences.tsx` 的 `playbackRate`，值域 `PLAYBACK_RATES`），
     * 原生这边一度只存在播放器的组合里——换一部片就静默回到 1.0x，等于丢设置。
     * NOTE: 用 `putFloat` 存取：SharedPreferences 存的是 float 的位模式，`0.75f` 这类值能原样读回。
     * 菜单高亮要跟 `SPEEDS` 里的字面量比较，位模式一致才不会因为差 1 个 ulp 而高亮不上。
     * NOTE: 值域由 `SPEEDS`（`ui/player/PlayerControls.kt`）定，本类不校验。哪天真删掉一档，存着旧值的
     * 人会看到菜单里没有高亮项——点任一档即可恢复，不是卡死状态。
     */
    fun playbackSpeed(): Float = prefs.getFloat(KEY_PLAYBACK_SPEED, 1f)

    fun setPlaybackSpeed(value: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
    }

    /**
     * 打开视频是否自动开始播放。默认**开**。
     *
     * NOTE: 本 App 的核心是「点开就看」，所以默认值与当前行为一致——这个开关只把选择权给出来，
     * 不改变多数人的手感。关掉后播放器仍然 `prepare()`：首帧照画出、控件照出现，等用户点播放键。
     */
    fun autoplay(): Boolean = prefs.getBoolean(KEY_AUTOPLAY, true)

    fun setAutoplay(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }

    private companion object {
        const val KEY_APP_LANGUAGE = "appLanguage"
        const val KEY_THEME_MODE = "themeMode"
        // TODO: `dynamicColor` 是旧字段，只用于一次性迁移，下个版本可以删。
        const val KEY_DYNAMIC_COLOR = "dynamicColor"
        const val KEY_COLOR_SOURCE = "colorSource"
        const val KEY_FOLLOW_THEME_ICON = "followThemeIcon"
        const val KEY_PURE_BLACK = "pureBlack"
        const val KEY_PLAYBACK_SPEED = "playbackSpeed"
        const val KEY_AUTOPLAY = "autoplay"
    }
}
