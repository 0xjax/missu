package app.missu.data.api

import app.missu.data.model.AccountUser
import app.missu.data.model.DirectoryList
import app.missu.data.model.HomeSection
import app.missu.data.model.PlaylistDetail
import app.missu.data.model.PlaylistSummary
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoList
import app.missu.data.model.VideoPlaylist
import app.missu.data.parse.ApiJson
import app.missu.data.parse.DetailParser
import app.missu.data.parse.DirectoryParser
import app.missu.data.parse.PlaylistCreateParser
import app.missu.data.parse.PlaylistParser
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
     * 新建片单。
     *
     * WARNING: 站点只有表单（`POST /playlists/create`），没有 JSON 接口。流程：
     * GET 表单页 → 读 `_token` 与字段名 → urlencoded POST。
     * NOTE: `_token` 只用于本次提交，绝不写日志。
     */
    suspend fun createPlaylist(name: String, isPublic: Boolean = false, description: String? = null) {
        val form = withAuthRetry {
            withChallengeRetry {
                val html = client.fetchHtml(CREATE_PLAYLIST_PATH)
                PlaylistCreateParser.parse(html) ?: throw IllegalStateException("新建片单页没有找到表单")
            }
        }

        val fields = form.fields.toMutableMap()
        fields[form.nameField] = name
        fields["is_public"] = if (isPublic) "1" else "0"
        if (description != null) fields["description"] = description

        withAuthRetry {
            withChallengeRetry {
                val path = form.action.takeIf { it.isNotBlank() } ?: CREATE_PLAYLIST_PATH
                client.postForm(path, fields)
            }
        }
    }
    /**
     * 我的片单列表（`/playlists`）。
     * WARNING: 站点没有片单列表的 JSON 接口，只能解析 HTML（见 [PlaylistParser]）。
     * WARNING: 该页对游客返回 200 + 空列表，调用方要用 `reloginWithRemembered()` 确认会话。
     */
    suspend fun getPlaylists(): List<PlaylistSummary> = withAuthRetry {
        withChallengeRetry {
            PlaylistParser.parsePlaylists(client.fetchHtml("/playlists"))
        }
    }

    /** 片单详情（`/playlists/{id}`）：标题 + 影片列表。同样只能解析 HTML。 */
    suspend fun getPlaylistDetail(id: String): PlaylistDetail = withAuthRetry {
        withChallengeRetry {
            PlaylistParser.parsePlaylistDetail(client.fetchHtml("/playlists/$id"))
        }
    }
    /**
     * 目录页（`/actresses`、`/genres`、`/makers`），分页 `?page=N`，可选排序 `?sort=`。
     * NOTE: `kind` 必须与路径段一致，解析时用它筛链接。
     * NOTE: 站点当前**只提供** `sort=videos`（作品数）与 `sort=debut`（出道年）；
     * legacy 里那个 height/cup/age/debut 筛选表单在现行页面已不存在（实测页面只有这两种排序链接），
     * 所以这里不做筛选参数，避免拼出站点不认识的 query。
     */
    suspend fun getDirectory(kind: String, page: Int = 1, sort: String? = null): DirectoryList = withChallengeRetry {
        val params = buildList {
            if (sort != null) add("sort=$sort")
            if (page > 1) add("page=$page")
        }
        val path = if (params.isEmpty()) "/$kind" else "/$kind?" + params.joinToString("&")
        DirectoryParser.parse(client.fetchHtml(path), kind, page)
    }

    /**
     * 女优目录（带筛选与排序）。参数名与 legacy `getActresses` 的请求构造一致：
     * `?sort=videos|debut&height=&cup=&age=&debut=&page=N`。
     * NOTE: 空值不发；但 `0-20` 这类要原样发，不能当假值丢掉。
     */
    suspend fun getActresses(
        page: Int = 1,
        sort: String? = null,
        filters: Map<String, String> = emptyMap(),
    ): DirectoryList = withChallengeRetry {
        val params = buildList {
            if (sort != null) add("sort=$sort")
            filters.forEach { (key, value) -> if (value.isNotEmpty()) add("$key=$value") }
            if (page > 1) add("page=$page")
        }
        val path = if (params.isEmpty()) "/actresses" else "/actresses?" + params.joinToString("&")
        DirectoryParser.parse(client.fetchHtml(path), "actresses", page)
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

    private companion object {
        const val CREATE_PLAYLIST_PATH = "/playlists/create"
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
