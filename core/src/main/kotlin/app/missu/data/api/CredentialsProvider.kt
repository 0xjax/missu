package app.missu.data.api

/**
 * 记住的账号凭据。由 App 侧用 Keystore 加密实现，纯 JVM 侧只认这个契约。
 *
 * NOTE: 只在「受保护请求返回 401」时才读取，用于自动重登一次。
 */
interface CredentialsProvider {
    fun load(): Pair<String, String>?

    /** 凭据失效（重登被拒）时清掉，避免每次启动/每次请求都白试。 */
    fun clear()
}
