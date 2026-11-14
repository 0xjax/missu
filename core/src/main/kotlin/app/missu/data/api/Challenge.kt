package app.missu.data.api

/** WebView 过 Cloudflare 之后拿到的结果。 */
data class ChallengeResult(val passed: Boolean, val html: String? = null)

/** 需要人机验证时的回调；由调用方决定怎么弹（App 里是 ChallengeActivity）。 */
interface ChallengeHandler {
    suspend fun verify(url: String): ChallengeResult
}
