package app.missu.data.comic

import app.missu.data.api.ChallengeException
import app.missu.data.api.ChallengeHandler
import app.missu.data.api.ChallengeKind
import app.missu.data.api.ChallengeRequest
import app.missu.data.api.HttpStatusException
import app.missu.data.api.SourceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ComicHttpClient(
    baseClient: OkHttpClient,
    cookieJar: CookieJar,
    private val challenge: ChallengeHandler? = null,
    private val userAgent: String = SourceConfig.DEFAULT_USER_AGENT,
    private val challengeKind: ChallengeKind = ChallengeKind.CLOUDFLARE,
) {
    private val client = baseClient.newBuilder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun get(
        url: String,
        referer: String? = null,
        accept: String = "text/html,application/xhtml+xml,application/json",
    ): String = withContext(Dispatchers.IO) {
        repeat(CHALLENGE_ROUNDS) { round ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", accept)
                .header("Accept-Language", SourceConfig.acceptLanguage())
                .apply { referer?.let { header("Referer", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (response.isChallenge(body)) {
                    val handler = challenge ?: throw ChallengeException()
                    val result = handler.verify(ChallengeRequest(url, challengeKind))
                    if (!result.passed) throw ChallengeException()
                    result.html?.takeIf { it.isNotBlank() }?.let { return@withContext it }
                    if (round == CHALLENGE_ROUNDS - 1) throw ChallengeException()
                } else {
                    if (!response.isSuccessful) throw HttpStatusException(response.code)
                    return@withContext body
                }
            }
        }
        throw ChallengeException()
    }

    private fun okhttp3.Response.isChallenge(body: String): Boolean =
        header("cf-mitigated") == "challenge" ||
            CHALLENGE_TITLE.containsMatchIn(body) ||
            body.contains("window._cf_chl_opt") ||
            (challengeKind == ChallengeKind.ACCESS_BUTTON && ACCESS_TITLE.containsMatchIn(body))

    private companion object {
        const val CHALLENGE_ROUNDS = 2
        val CHALLENGE_TITLE = Regex("<title>\\s*Just a moment\\.\\.\\.\\s*</title>", RegexOption.IGNORE_CASE)
        val ACCESS_TITLE = Regex("<title>\\s*安全验证\\s*</title>")
    }
}
