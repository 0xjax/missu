package app.missu.data.api

/** WebView 过站点验证之后拿到的结果。 */
data class ChallengeResult(val passed: Boolean, val html: String? = null)

enum class ChallengeKind {
    CLOUDFLARE,
    ACCESS_BUTTON,
}

/** [url] 是触发验证的完整地址；浏览器若直接拿到页面，可以把 HTML 一并交回。 */
data class ChallengeRequest(
    val url: String,
    val kind: ChallengeKind = ChallengeKind.CLOUDFLARE,
)

/** 需要站点验证时的回调；App 在组合根部用 WebView 浮层处理。 */
interface ChallengeHandler {
    suspend fun verify(request: ChallengeRequest): ChallengeResult
}
