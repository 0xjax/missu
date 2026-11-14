package app.missu.data.api

import android.content.Context
import app.missu.data.store.CredentialStore

/**
 * App 侧的依赖装配：OkHttp 与 WebView 共享系统 Cookie 存储，
 * 遇到人机验证时弹 [app.missu.ui.challenge.ChallengeActivity]。
 */
fun createMissavApi(context: Context): MissavApi {
    val credentials = CredentialStore(context.applicationContext)
    return MissavApi(
        client = MissavClient(cookieJar = AndroidCookieJar()),
        challenge = object : ChallengeHandler {
            override suspend fun verify(url: String): ChallengeResult {
                val deferred = ChallengeHost.prepare(url)
                ChallengeHost.start(context)
                return deferred.await()
            }
        },
        // NOTE: 只在受保护请求返回 401 时被读取——启动时不去猜会话是否有效（实测判据不成立）。
        credentials = object : CredentialsProvider {
            override fun load(): Pair<String, String>? = credentials.load()

            override fun clear() = credentials.clear()
        },
    )
}
