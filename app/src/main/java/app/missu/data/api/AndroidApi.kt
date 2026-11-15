package app.missu.data.api

import android.content.Context
import app.missu.data.store.CredentialStore
import okhttp3.OkHttpClient

/**
 * App 侧的依赖装配：OkHttp 与 WebView 共享系统 Cookie 存储；
 * 人机验证交给调用方传入的 [ChallengeHandler]（App 里是 `ChallengeState`，浮层挂在组合根部）。
 */
fun createMissavApi(context: Context, challenge: ChallengeHandler, httpClient: OkHttpClient): MissavApi {
    val credentials = CredentialStore(context.applicationContext)
    return MissavApi(
        client = MissavClient(baseClient = httpClient, cookieJar = AndroidCookieJar()),
        challenge = challenge,
        // NOTE: 只在受保护请求返回 401 时被读取——启动时不去猜会话是否有效（实测判据不成立）。
        credentials = object : CredentialsProvider {
            override fun load(): Pair<String, String>? = credentials.load()

            override fun clear() = credentials.clear()
        },
    )
}
