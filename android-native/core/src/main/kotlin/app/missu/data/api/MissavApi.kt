package app.missu.data.api

import app.missu.data.model.AccountUser
import app.missu.data.model.DirectoryList
import app.missu.data.model.HomeSection
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoList
import app.missu.data.model.VideoPlaylist
import app.missu.data.parse.ApiJson
import app.missu.data.parse.DetailParser
import app.missu.data.parse.DirectoryParser
import app.missu.data.parse.HomeParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 站点接口。函数命名与 legacy 的 `src/api/missav.ts` 保持一致，便于逐个对照迁移。
 *
 * 人机验证：站点用 Cloudflare 挡非浏览器客户端（实测 403 + `cf-mitigated: challenge`）。
 * 命中时交给 [challenge] 让用户过一遍，`cf_clearance` 写进共享 Cookie 后重试一次。
 *
 * TODO: 目前只有 `getHome`，后续按迁移顺序补 getVideos / getVideoDetail / searchVideos 等。
 */
class MissavApi(
    private val client: MissavClient = MissavClient(),
    private val challenge: ChallengeHandler? = null,
    private val credentials: CredentialsProvider? = null,
) {

    /** 首页板块。站点直出 HTML，直接 Jsoup 解析，不需要 WebView。 */
    suspend fun getHome(): List<HomeSection> = withChallengeRetry { HomeParser.parseHome(client.fetchHtml("/")) }

    /**
     * 详情页。播放地址从内联打包脚本里解出来（见 [app.missu.data.parse.DetailParser.extractPlaybackUrl]）。
     * NOTE: 顺带查收藏状态，查不到就当「未收藏」——详情页不该因为收藏接口抖动而整体失败。
     */
    suspend fun getVideoDetail(id: String): VideoDetail = withChallengeRetry {
        val detail = DetailParser.parse(client.fetchHtml("/$id"), id)
        val internalId = detail.internalId ?: return@withChallengeRetry detail
        val saved = runCatching { isSaved(internalId) }.getOrDefault(false)
        detail.copy(saved = saved)
    }

    /** 收藏状态。`rawPath`：收藏接口只挂在根路径。 */
    suspend fun isSaved(internalId: String): Boolean = withAuthRetry {
        withChallengeRetry {
            val json = client.requestJson("GET", "/api/items/$internalId/view", rawPath = true)
            ApiJson.parseSavedState(json)
        }
    }

    /** 收藏 / 取消收藏。401 会自动重登一次再重试（见 [withAuthRetry]）。 */
    suspend fun setSaved(internalId: String, saved: Boolean) {
        withAuthRetry {
            withChallengeRetry {
                client.requestJson(
                    method = if (saved) "POST" else "DELETE",
                    path = "/api/items/$internalId/save",
                    rawPath = true,
                )
            }
        }
    }

    suspend fun html(path: String): String = withChallengeRetry { client.fetchHtml(path) }

    /**
     * 登录。步骤与 legacy 一致：
     * 1. 先清空 Cookie 罐——带旧会话或 Cloudflare Cookie 时站点直接 401；
     * 2. POST `/{lang}/api/login`，body 带 `remember: true`；
     * 3. 响应只有 `{"user":{"name":...}}`，邮箱用本次输入补全。
     *
     * NOTE: 清空后仍 401 表示账号或密码错，必须转成 [InvalidCredentialsException]，
     * 不能沿用「会话失效」的提示（legacy 踩过）。
     */
    suspend fun login(email: String, password: String): AccountUser {
        val payload = buildJsonObject {
            put("email", email)
            put("password", password)
            put("remember", true)
        }.toString()

        val body = withChallengeRetry {
            client.clearCookies()
            try {
                client.postJson("/api/login", payload)
            } catch (error: SessionExpiredException) {
                throw InvalidCredentialsException()
            }
        }

        val username = runCatching {
            Json.parseToJsonElement(body).jsonObject["user"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return AccountUser(email = email, username = username)
    }

    /**
     * 校验会话：只有「受保护页面被 302 回首页」才判失效。
     *
     * WARNING: 实测在当前站点**对游客访问 `/playlists` 返回 200 而不是 302**（应用日志里
     * 「restore: session valid」正是这个原因），所以这个函数不能用来判断「要不要自动重登」，
     * 只保留给「确实拿到 302 信号」的少数场景。真正的失效信号请用 [withAuthRetry]。
     */
    suspend fun checkSession(): Boolean = try {
        client.fetchHtml("/playlists")
        true
    } catch (error: SessionExpiredException) {
        false
    } catch (error: Exception) {
        true
    }

    /**
     * 登出：站点是普通 GET /logout 并跳回首页。
     * NOTE: 跳回首页会被判定成「会话失效」，这里按预期忽略异常——登出本来就要成功。
     */
    suspend fun logout() {
        runCatching { client.fetchHtml("/logout") }
        client.clearCookies()
    }

    /**
     * 当前影片可加入的片单及是否已在其中。
     * NOTE: 片单接口只挂根路径（`/api/playlists/…`），带语言前缀会 404。
     */
    suspend fun getVideoPlaylists(dvdId: String): List<VideoPlaylist> = withAuthRetry {
        withChallengeRetry {
            val json = client.requestJson("GET", "/api/playlists/$dvdId", rawPath = true)
            ApiJson.parseVideoPlaylists(json)
        }
    }

    /** 把影片加入 / 移出片单。 */
    suspend fun setPlaylistMembership(dvdId: String, key: String, added: Boolean) {
        val body = buildJsonObject {
            put("dvdId", dvdId)
            put("key", key)
        }.toString()

        withAuthRetry {
            withChallengeRetry {
                client.requestJson(
                    method = "POST",
                    path = if (added) "/api/playlists/add" else "/api/playlists/remove",
                    body = body,
                    rawPath = true,
                )
            }
        }
    }
    /**
     * 用记住的凭据主动重登一次。返回是否成功。
     *
     * NOTE: 给「游客也会拿到 200 空页」的接口用——`/saved` 实测对游客返回空列表而不是 302/401，
     * 单靠 withAuthRetry 抓不到失效，只能主动确认会话。
     */
    suspend fun reloginWithRemembered(): Boolean {
        val remembered = credentials?.load() ?: return false
        return try {
            login(remembered.first, remembered.second)
            true
        } catch (error: Exception) {
            if (error is InvalidCredentialsException) credentials.clear()
            false
        }
    }

    /**
     * 目录页（`/actresses`、`/genres`、`/makers`），分页 `?page=N`。
     * NOTE: `kind` 必须与路径段一致，解析时用它筛链接。
     */
    suspend fun getDirectory(kind: String, page: Int = 1): DirectoryList = withChallengeRetry {
        val path = if (page > 1) "/$kind?page=$page" else "/$kind"
        DirectoryParser.parse(client.fetchHtml(path), kind, page)
    }
    /**
     * 通用视频列表页（`/latest`、`/actresses/{名}`、`/genres/{名}`…），分页用 `?page=N`。
     * NOTE: 路径来自详情页信息行的归一化结果（已剥掉 `/dmNNN` 与语言段），直接交给这里即可。
     * NOTE: 过滤/排序参数（filters/sort）暂不支持——等目录页需要时再加。
     */
    suspend fun getVideos(path: String, page: Int = 1): VideoList = withChallengeRetry {
        val separator = if (path.contains('?')) '&' else '?'
        val fullPath = if (page > 1) "$path${separator}page=$page" else path
        HomeParser.parseVideoList(client.fetchHtml(fullPath), page)
    }
    /** 收藏列表：`/saved`，分页 `?page=N`。未登录会 302 回首页，交给 [withAuthRetry] 自动重登。 */
    suspend fun getSaved(page: Int = 1): VideoList = withAuthRetry {
        withChallengeRetry {
            val path = if (page > 1) "/saved?page=$page" else "/saved"
            HomeParser.parseVideoList(client.fetchHtml(path), page)
        }
    }

    /**
     * 搜索：路径 `/search/{keyword}`，分页用 `?page=N`（从 1 开始，第 1 页不带参数）。
     * NOTE: 关键词按 legacy 用 `encodeURIComponent` 语义（空格编码成 %20 而不是 +）。
     */
    suspend fun searchVideos(keyword: String, page: Int = 1): VideoList = withChallengeRetry {
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val path = if (page > 1) "/search/$encoded?page=$page" else "/search/$encoded"
        HomeParser.parseVideoList(client.fetchHtml(path), page)
    }

    /**
     * 受保护请求的包装：拿到 [SessionExpiredException] 时用记住的凭据自动重登一次再重试。
     * NOTE: 重登被拒（改过密码）就清掉凭据并抛原始错误，避免死循环。
     * TODO: 收藏/片单这些接口迁移过来时统一走这里。
     */
    suspend fun <T> withAuthRetry(block: suspend () -> T): T =
        try {
            block()
        } catch (error: SessionExpiredException) {
            val remembered = credentials?.load() ?: throw error
            try {
                login(remembered.first, remembered.second)
            } catch (relogin: Exception) {
                if (relogin is InvalidCredentialsException) credentials.clear()
                throw error
            }
            block()
        }

    private suspend fun <T> withChallengeRetry(block: suspend () -> T): T =
        try {
            block()
        } catch (error: ChallengeException) {
            val handler = challenge ?: throw error
            val result = handler.verify(SourceConfig.localizedUrl("/"))
            if (!result.passed) throw error
            // NOTE: 验证结果里的 HTML 属于某个具体路径，不能拿来当任意请求的响应；统一重试原请求。
            block()
        }
}
