package app.missu.data.api

/**
 * 站点常量与路径规则。
 *
 * NOTE: 站点把内容语言放在路径段里（`/cn/new`、`/en/search/x`），没有前缀即繁體中文；
 * 解析出的链接还会混入站点自己的 `/dmNNN` 前缀，这里统一剥掉再按当前语言重建，
 * 调用点不需要关心。规则直接照搬 legacy 的 `src/api/missav.ts`。
 */
object SourceConfig {
    const val BASE_URL = "https://missav.ai"

    /** Android Chrome 的 UA。站点会对非常规 UA 走更严的人机验证。 */
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 16; V2507A) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"

    private val LANGUAGE_SEGMENTS = setOf("cn", "en", "ja", "ko", "ms", "th")

    @Volatile
    var contentLanguage: String = "cn"

    /**
     * 发给站点的 `Accept-Language`，由 [contentLanguage] 派生。
     *
     * 实测（2026-11-15 06:56，真机 + httpbin）：WebView 自己发的头是 `en-US,en`（系统/引擎决定），
     * 而 OkHttp 这边写死 `zh-CN`——**两个客户端对同一个站点报不同语言**，界面中文时尤其自相矛盾。
     * 站点与 Cloudflare 都按这个头决定给哪种语言：同样出口下 `zh-CN` 拿到中文挑战页（`请稍候…`）、
     * `en-US` 拿到英文（`Just a moment...`）。所以中文挑战页不需要我们维护，跟着这个头来。
     */
    fun acceptLanguage(): String = when (contentLanguage) {
        "cn" -> "zh-CN,zh;q=0.9,en;q=0.8"
        "tw" -> "zh-TW,zh;q=0.9,en;q=0.8"
        "ja" -> "ja-JP,ja;q=0.9,en;q=0.8"
        "ko" -> "ko-KR,ko;q=0.9,en;q=0.8"
        "ms" -> "ms-MY,ms;q=0.9,en;q=0.8"
        "th" -> "th-TH,th;q=0.9,en;q=0.8"
        else -> "en-US,en;q=0.9"
    }

    fun localizedPath(path: String): String {
        val withoutQuery = path.substringBefore('?')
        val query = path.substringAfter('?', "")
        val segments = withoutQuery.split('/').filter { it.isNotEmpty() }.toMutableList()
        if (segments.firstOrNull()?.matches(Regex("dm\\d+")) == true) segments.removeAt(0)
        if (segments.firstOrNull() in LANGUAGE_SEGMENTS) segments.removeAt(0)

        val clean = "/" + segments.joinToString("/")
        val suffix = if (query.isEmpty()) "" else "?$query"
        return when {
            contentLanguage == "tw" -> "$clean$suffix"
            clean == "/" -> "/$contentLanguage$suffix"
            else -> "/$contentLanguage$clean$suffix"
        }
    }

    fun localizedUrl(path: String): String = BASE_URL + localizedPath(path)

    /**
     * 去掉站点的 `/dmNNN` 前缀与语言段，得到稳定的内容路径（如 `/cn/playlists/1` → `/playlists/1`）。
     * NOTE: 详情页里的链接带这两种前缀，归一化后才能交给列表页复用。
     */
    fun contentPath(pathname: String): String {
        val segments = pathname.split('/').filter { it.isNotEmpty() }.toMutableList()
        if (segments.firstOrNull()?.matches(Regex("dm\\d+")) == true) segments.removeAt(0)
        if (segments.firstOrNull() in LANGUAGE_SEGMENTS) segments.removeAt(0)
        return "/" + segments.joinToString("/")
    }
}
