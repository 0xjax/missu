package app.missu.data.api

import android.content.Context

/**
 * App 侧的依赖装配：OkHttp 与 WebView 共享系统 Cookie 存储，
 * 遇到人机验证时弹 [app.missu.ui.challenge.ChallengeActivity]。
 */
fun createMissavApi(context: Context): MissavApi =
    MissavApi(
        client = MissavClient(cookieJar = AndroidCookieJar()),
        challenge = object : ChallengeHandler {
            override suspend fun verify(url: String): ChallengeResult {
                val deferred = ChallengeHost.prepare(url)
                ChallengeHost.start(context)
                return deferred.await()
            }
        },
    )
