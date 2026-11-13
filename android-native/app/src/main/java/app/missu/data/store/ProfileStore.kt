package app.missu.data.store

import android.content.Context
import app.missu.data.model.AccountUser

/**
 * 登录资料的本地存储。
 *
 * TODO: 这里是 SharedPreferences，属于临时方案——等存储层统一（DataStore / SQLite）时迁过去，
 * 到那时「历史/偏好/搜索词」会一起搬。
 * NOTE: 只存邮箱与昵称，**不存密码**；会话本身由 CookieManager 持久化。
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("missu:profile", Context.MODE_PRIVATE)

    fun read(): AccountUser? {
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return AccountUser(email = email, username = prefs.getString(KEY_USERNAME, null))
    }

    fun write(user: AccountUser) {
        prefs.edit()
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_USERNAME, user.username)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_USERNAME = "username"
    }
}
