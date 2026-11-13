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
}
