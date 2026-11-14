package app.missu.data.api

import app.missu.data.model.AccountUser
import app.missu.data.model.DirectoryList
import app.missu.data.model.HomeSection
import app.missu.data.model.NavGroup
import app.missu.data.model.PlaylistDetail
import app.missu.data.model.PlaylistSummary
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoList
import app.missu.data.model.VideoPlaylist
import app.missu.data.parse.ApiJson
import app.missu.data.parse.DetailParser
import app.missu.data.parse.DirectoryParser
import app.missu.data.parse.PlaylistEditPage
import app.missu.data.parse.PlaylistFormParser
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
    suspend fun getHome(): List<HomeSection> = HomeParser.parseHome(fetchPage("/"))

    /**
     * 站点导航里的分组入口（「频道」页签的数据源）。
     *
     * NOTE: 与 [getHome] 是同一份首页 HTML，只是解析另一段；站点没有独立的导航接口。
     * 分组与条目都由站点给，我们不维护频道清单——它的编辑菜单变了就自动跟上。
     */
    suspend fun getNavGroups(): List<NavGroup> = HomeParser.parseNav(fetchPage("/"))

    /**
     * 详情页。播放地址从内联打包脚本里解出来（见 [app.missu.data.parse.DetailParser.extractPlaybackUrl]）。
     * NOTE: 顺带查收藏状态，查不到就当「未收藏」——详情页不该因为收藏接口抖动而整体失败。
     */
    suspend fun getVideoDetail(id: String): VideoDetail {
        val detail = DetailParser.parse(fetchPage("/$id"), id)
        val internalId = detail.internalId ?: return detail
        val saved = runCatching { isSaved(internalId) }.getOrDefault(false)
        return detail.copy(saved = saved)
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

    suspend fun html(path: String): String = fetchPage(path)

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
     * 把某部影片从指定片单里移除（片单详情页的「移除」）。
     *
     * NOTE: 站点有两条不同的移除入口，别混用：
     * - 详情页片单面板：`POST /api/playlists/remove`，body 要 `{dvdId, key}`；
     * - 片单详情页：`DELETE /api/playlists/{片单id}/{影片id}`，不要 key。
     * 依据是片单详情页的内联脚本（`deletePlaylistItem()` 里
     * `window.axios.delete('https://missav.ai/api/playlists/z85tfntj/sdmf-018')`，实测抓包）。
     * 片单页只拿得到片单 id，所以这里走第二条；两条入口都不能猜。
     */
    suspend fun removePlaylistItem(playlistId: String, dvdId: String) {
        withAuthRetry {
            withChallengeRetry {
                client.requestJson("DELETE", "/api/playlists/$playlistId/$dvdId", rawPath = true)
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
            val html = fetchPage(CREATE_PLAYLIST_PATH)
            PlaylistFormParser.parseCreate(html) ?: throw IllegalStateException("新建片单页没有找到表单")
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
    /** 读取「修改片单」页：当前值 + 更新表单 + 删除表单（站点没有对应 JSON 接口）。 */
    suspend fun getPlaylistEdit(id: String): PlaylistEditPage = withAuthRetry {
        val html = fetchPage("/playlists/$id/edit")
        PlaylistFormParser.parseEdit(html) ?: throw IllegalStateException("修改片单页没有找到表单")
    }

    /** 更新片单（名称 / 描述 / 是否公开）。表单 action 为空表示提交回编辑页自身。 */
    suspend fun updatePlaylist(id: String, name: String, description: String, isPublic: Boolean) {
        val page = getPlaylistEdit(id)
        val fields = page.update.fields.toMutableMap()
        fields[page.update.nameField] = name
        fields["description"] = description
        fields["is_public"] = if (isPublic) "1" else "0"
        withAuthRetry {
            withChallengeRetry {
                val path = page.update.action.takeIf { it.isNotBlank() } ?: "/playlists/$id/edit"
                client.postForm(path, fields)
            }
        }
    }

    /** 删除片单：提交修改页里的删除表单（`_method=delete` + `_token` 到片单自身 URL）。 */
    suspend fun deletePlaylist(id: String) {
        val page = getPlaylistEdit(id)
        withAuthRetry {
            withChallengeRetry {
                val path = page.deleteAction.takeIf { it.isNotBlank() } ?: "/playlists/$id"
                client.postForm(path, page.deleteFields)
            }
        }
    }
    /**
     * 我的片单列表（`/playlists`）。
     * WARNING: 站点没有片单列表的 JSON 接口，只能解析 HTML（见 [PlaylistParser]）。
     * WARNING: 该页对游客返回 200 + 空列表，调用方要用 `reloginWithRemembered()` 确认会话。
     */
    suspend fun getPlaylists(): List<PlaylistSummary> = withAuthRetry {
        PlaylistParser.parsePlaylists(fetchPage("/playlists"))
    }

    /** 片单详情（`/playlists/{id}`）：标题 + 影片列表。同样只能解析 HTML。 */
    suspend fun getPlaylistDetail(id: String): PlaylistDetail = withAuthRetry {
        PlaylistParser.parsePlaylistDetail(fetchPage("/playlists/$id"))
    }
    /**
     * 目录页（`/actresses`、`/genres`、`/makers`），分页 `?page=N`，可选排序 `?sort=`。
     * NOTE: `kind` 必须与路径段一致，解析时用它筛链接。
     * NOTE: 排序只有 `sort=videos`（作品数）与 `sort=debut`（出道年）两档——女优目录的
     * height/cup/age/debut 筛选是**表单**（`/actresses` 页面上仍有 4 个 `<select>`，服务端也真的
     * 生效），走 [getActresses] 那条路径；这里只发排序，避免给类型/厂商拼出站点不认识的参数。
     */
    suspend fun getDirectory(kind: String, page: Int = 1, sort: String? = null): DirectoryList {
        val params = buildList {
            if (sort != null) add("sort=$sort")
            if (page > 1) add("page=$page")
        }
        val path = if (params.isEmpty()) "/$kind" else "/$kind?" + params.joinToString("&")
        return DirectoryParser.parse(fetchPage(path), kind, page)
    }

    /**
     * 女优月榜：站点把它做成**独立页面** `/actresses/ranking`（现行标题「女优排行 NOV 2026」），
     * 不接受 `sort` 参数——所以它不是一个 query 取值，而是另一条路径。
     *
     * 条目结构与女优目录相同，复用 [DirectoryParser]（legacy 也是拿同一个目录解析器读它的，
     * 只是在解析目录页时把 `/ranking` 链接排除了，见 `legacy/expo/src/api/parse.ts`）。
     *
     * NOTE: 这个榜单没有 JSON 接口，只能解析 HTML——与 `/actresses` / `/genres` / `/makers` 同一批页面。
     */
    suspend fun getActressRanking(page: Int = 1): DirectoryList {
        val path = if (page > 1) "/actresses/ranking?page=$page" else "/actresses/ranking"
        return DirectoryParser.parse(fetchPage(path), "actresses", page)
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
    ): DirectoryList {
        val params = buildList {
            if (sort != null) add("sort=$sort")
            filters.forEach { (key, value) -> if (value.isNotEmpty()) add("$key=$value") }
            if (page > 1) add("page=$page")
        }
        val path = if (params.isEmpty()) "/actresses" else "/actresses?" + params.joinToString("&")
        return DirectoryParser.parse(fetchPage(path), "actresses", page)
    }
    /**
     * 通用视频列表页（`/new`、`/today-hot`、`/genres/{名}`、频道页…），分页用 `?page=N`。
     *
     * NOTE: 路径来自详情页信息行 / 频道 / 首页「更多」的归一化结果（已剥掉 `/dmNNN` 与语言段）。
     * NOTE: `sort` / `filter` 为 null 表示**不传**，也就是站点默认序 / 不过滤。默认序不等于任何
     * 一个 sort 取值（[VideoListOptions] 里有实测记录），所以别用某个值去「还原默认」。
     */
    suspend fun getVideos(
        path: String,
        page: Int = 1,
        sort: VideoListOptions.Sort? = null,
        filter: VideoListOptions.Filter? = null,
    ): VideoList = HomeParser.parseVideoList(
        fetchPage(videoListPath(path, page, sort, filter)),
        page,
    )
    /** 收藏列表：`/saved`，分页 `?page=N`。未登录会 302 回首页，交给 [withAuthRetry] 自动重登。 */
    suspend fun getSaved(page: Int = 1): VideoList = withAuthRetry {
        val path = if (page > 1) "/saved?page=$page" else "/saved"
        HomeParser.parseVideoList(fetchPage(path), page)
    }

    /**
     * 搜索：路径 `/search/{keyword}`，分页用 `?page=N`（从 1 开始，第 1 页不带参数）。
     * NOTE: 关键词按 legacy 用 `encodeURIComponent` 语义（空格编码成 %20 而不是 +）。
     */
    suspend fun searchVideos(keyword: String, page: Int = 1): VideoList {
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val path = if (page > 1) "/search/$encoded?page=$page" else "/search/$encoded"
        return HomeParser.parseVideoList(fetchPage(path), page)
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

    /**
     * 取页面 HTML，并处理人机验证。
     *
     * NOTE: 与 [withChallengeRetry] 的区别在这儿：验证浮层加载的**就是这个路径**，
     * 所以浏览器要是没被挑战、直接渲染出了这一页，[ChallengeHandler.verify] 会把 HTML 一起带回来——
     * 直接用，省掉一次注定再被 403 的请求。这正是「OkHttp 被挡、浏览器没被挡」那种卡死的解法。
     */
    private suspend fun fetchPage(path: String): String =
        try {
            client.fetchHtml(path)
        } catch (error: ChallengeException) {
            val handler = challenge ?: throw error
            val result = handler.verify(path)
            if (!result.passed) throw error
            result.html ?: client.fetchHtml(path)
        }

    /**
     * 非页面请求（JSON / 表单）的人机验证包装：只能让浮层去拿 `cf_clearance`，然后重试。
     * NOTE: 这类响应不是可渲染的页面，浏览器代取没有意义，所以 [ChallengeHandler.verify] 只给它首页。
     */
    private suspend fun <T> withChallengeRetry(block: suspend () -> T): T =
        try {
            block()
        } catch (error: ChallengeException) {
            val handler = challenge ?: throw error
            val result = handler.verify("/")
            if (!result.passed) throw error
            block()
        }
}
