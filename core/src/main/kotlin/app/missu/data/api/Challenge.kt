package app.missu.data.api

/** WebView 过 Cloudflare 之后拿到的结果。 */
data class ChallengeResult(val passed: Boolean, val html: String? = null)

/**
 * 需要人机验证时的回调；由调用方决定怎么弹（App 里是 [app.missu.ui.challenge.ChallengeState] 的浮层）。
 *
 * [verify] 的 `path` 是**触发挑战的那个请求的站点相对路径**：浮层把它加载进去，
 * 于是浏览器要是根本没被挑战、直接拿到了这一页，就能把 HTML 交还给调用方
 * （见 `MissavApi.fetchPage`）——这比「等一个永远不会出现的 cf_clearance」正确得多。
 */
interface ChallengeHandler {
    suspend fun verify(path: String): ChallengeResult
}
