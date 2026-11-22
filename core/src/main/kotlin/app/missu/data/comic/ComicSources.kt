package app.missu.data.comic

import app.missu.data.api.ChallengeHandler
import app.missu.data.api.ChallengeKind
import okhttp3.CookieJar
import okhttp3.OkHttpClient

fun createComicRepository(
    baseClient: OkHttpClient,
    cookieJar: CookieJar,
    challenge: ChallengeHandler,
): ComicRepository {
    fun client(
        userAgent: String = app.missu.data.api.SourceConfig.DEFAULT_USER_AGENT,
        kind: ChallengeKind = ChallengeKind.CLOUDFLARE,
    ) = ComicHttpClient(baseClient, cookieJar, challenge, userAgent, kind)

    return ComicRepository(
        listOf(
            CopyComicApi(client()),
            RoumanComicApi(client()),
            JmicComicApi(client()),
            KalamComicApi(
                client(
                    userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                    kind = ChallengeKind.ACCESS_BUTTON,
                ),
            ),
            MyComicApi(client()),
        ),
    )
}
