package app.missu.data.store

import android.content.Context
import app.missu.data.api.SourceConfig
import app.missu.data.api.isSupportedLanguage

/**
 * 应用设置。目前只有「内容语言」。
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

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val DEFAULT_LANGUAGE = "cn"
    }
}
