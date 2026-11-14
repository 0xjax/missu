package app.missu.ui.discover

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ActressFilterOptions
import app.missu.data.api.MissavApi
import app.missu.R
import app.missu.data.model.DirectoryItem
import app.missu.data.model.DirectoryList
import app.missu.data.model.NavGroup
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 发现页的页签。`kind` 只对**目录**有意义（它同时是路径段与解析时用来筛链接的关键词）；
 * 「热门」与「频道」不取数，数据分别来自视频列表与站点导航。
 *
 * NOTE: 顺序就是页签顺序：**热门在第一位**。三个目录都要求用户先有一个名字（女优 / 题材 / 厂牌）
 * 才来点，只有热门不要求任何意图——它是这个页面唯一能当入口的页签。频道放最后：它是一张
 * 「站点还有哪些策展列表」的索引，不是日常路径。
 */
enum class DiscoverTab(val kind: String, @param:StringRes val labelRes: Int) {
    HOT("hot", R.string.discover_section_hot),
    ACTRESSES("actresses", R.string.discover_section_actresses),
    GENRES("genres", R.string.discover_section_genres),
    MAKERS("makers", R.string.discover_section_makers),
    CHANNELS("channels", R.string.discover_section_channels),
    ;

    /** 需要向后端取目录的页签。热门与频道各有自己的数据源。 */
    val isDirectory: Boolean get() = this == ACTRESSES || this == GENRES || this == MAKERS
}

/**
 * 热门的三个周期。
 *
 * NOTE: 站点把「今日 / 本周 / 本月热门」做成**同一面列表的三个路径**（`/today-hot` 等，
 * 不是 `?period=` 参数）——legacy 的 `getVideos(path)` 也是这么传的。所以换周期 = 换数据源，
 * 页面结构完全不变。
 */
enum class HotPeriod(val path: String, @param:StringRes val labelRes: Int) {
    TODAY("/today-hot", R.string.hot_today),
    WEEKLY("/weekly-hot", R.string.hot_weekly),
    MONTHLY("/monthly-hot", R.string.hot_monthly),
}

/**
 * 「类型」索引漏列、但站点确实当成类型的那个页面。
 *
 * NOTE: 站点 `/genres` 索引分页里没有 VR（实测 36 项/页、翻到第 2 页也没有），但 `/genres/VR`
 * 是正常列表页（有 VR 片、有排序）。所以按「它是类型」补一格，而不是给它开一个新维度。
 * 「VR」是国际通用缩写，四种语言都不翻译。
 */
object DiscoverPins {
    val vrGenre = DirectoryItem(id = "/genres/VR", path = "/genres/VR", name = "VR")
}

/**
 * 「频道」页签的编排规则。
 *
 * WARNING: 这些是**我们的信息架构**，不是站点的。站点的导航是编辑菜单（可折叠分组），
 * 把它 1:1 复刻会得到「亚洲 AV 里混着直播、且比观看日本 AV 多一层」的怪结构。这里的规则是：
 * 分组只当排版标题、不嵌套；站点菜单里已经由页签覆盖的东西不再出现。
 */
internal object ChannelRules {

    /** 已经作为页签/入口暴露的路径：从频道里剔掉，否则同一个目的地在两处出现。 */
    val exposedPaths = setOf(
        "/new", "/release", "/uncensored-leak",
        "/actresses", "/actresses/ranking",
        "/genres", DiscoverPins.vrGenre.path, "/makers",
        HotPeriod.TODAY.path, HotPeriod.WEEKLY.path, HotPeriod.MONTHLY.path,
    )

    /** 属于「我的」或非列表页：它们不是频道（按路径判，不看标题，换语言也不会失效）。 */
    val notChannels = listOf("/saved", "/playlists", "/history", "/vip")

    /** 直播：站点把它们塞在「亚洲 AV」组里，性质上却是另一类列表，界面上单独成组。 */
    val livePaths = setOf("/klive", "/clive")
}

/** 站点导航解析出来的频道分组。[Loading] 表示还没拉过。 */
sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState
    data object Empty : ChannelsUiState
    data class Error(val message: UiMessage) : ChannelsUiState
    data class Content(val groups: List<NavGroup>) : ChannelsUiState
}

sealed interface DirectoryUiState {
    data object Loading : DirectoryUiState
    data object Empty : DirectoryUiState
    data class Error(val message: UiMessage) : DirectoryUiState
    data class Content(
        val items: List<DirectoryItem>,
        val nextPage: Int?,
        val loadingMore: Boolean = false,
    ) : DirectoryUiState
}

/** 三个目录各自独立加载，用一个 Map 存状态，避免写三份几乎一样的代码。 */
class DiscoverViewModel(private val api: MissavApi) : ViewModel() {

    private val _states = MutableStateFlow<Map<String, DirectoryUiState>>(emptyMap())
    val states: StateFlow<Map<String, DirectoryUiState>> = _states.asStateFlow()

    /** 女优排序：`videos`（影片数，默认）/ `debut`（出道年）/ `ranking`（本月排行）。 */
    private val _sort = MutableStateFlow(ActressFilterOptions.SORT_VIDEOS)
    val sort: StateFlow<String> = _sort.asStateFlow()

    /** 女优筛选：`height / cup / age / debut` → 值。空 Map 表示不筛。 */
    private val _filters = MutableStateFlow<Map<String, String>>(emptyMap())
    val filters: StateFlow<Map<String, String>> = _filters.asStateFlow()

    /** 正在下拉刷新的目录（同一时间只有一个页签可见）。 */
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val _channels = MutableStateFlow<ChannelsUiState>(ChannelsUiState.Loading)
    val channels: StateFlow<ChannelsUiState> = _channels.asStateFlow()
    private var channelsRequested = false

    /**
     * 取一页目录。三个调用点共用，避免把「女优有两条取数路径」这件事抄三遍。
     *
     * NOTE: 女优的「本月排行」不是 `?sort=` 参数——站点把它做成独立页面 `/actresses/ranking`，
     * 所以这里要分流。排行页不吃套筛选，所以那一档不带 filters。
     */
    private suspend fun fetch(kind: String, page: Int): DirectoryList =
        if (kind == DiscoverTab.ACTRESSES.kind) {
            if (_sort.value == ActressFilterOptions.SORT_RANKING) {
                api.getActressRanking(page)
            } else {
                api.getActresses(page = page, sort = _sort.value, filters = _filters.value)
            }
        } else {
            api.getDirectory(kind, page)
        }

    fun load(kind: String) {
        if (_states.value[kind] is DirectoryUiState.Loading) return
        update(kind, DirectoryUiState.Loading)
        viewModelScope.launch {
            update(
                kind,
                try {
                    val page = fetch(kind, page = 1)
                    if (page.items.isEmpty()) {
                        DirectoryUiState.Empty
                    } else {
                        DirectoryUiState.Content(page.items, page.nextPage)
                    }
                } catch (error: Exception) {
                    DirectoryUiState.Error(error.toUiMessage())
                },
            )
        }
    }

    /**
     * 拉「频道」页签。只在第一次进这个页签时拉一次首页 HTML，之后靠内存里的结果。
     *
     * NOTE: 站点没有导航接口，导航就在首页 HTML 里；与首页板块共用同一个 URL，但不共用请求
     * （两个页签的生命周期不同，缓存一份 HTML 只会让两边都要失效逻辑）。
     */
    fun loadChannels(force: Boolean = false) {
        if (channelsRequested && !force) return
        channelsRequested = true
        _channels.value = ChannelsUiState.Loading
        viewModelScope.launch {
            _channels.value = try {
                val groups = toChannels(api.getNavGroups())
                if (groups.isEmpty()) ChannelsUiState.Empty else ChannelsUiState.Content(groups)
            } catch (error: Exception) {
                ChannelsUiState.Error(error.toUiMessage())
            }
        }
    }

    /** 站点导航 → 频道分组：剔掉已有入口与「我的」，丢掉空分组。 */
    private fun toChannels(groups: List<NavGroup>): List<NavGroup> =
        groups.mapNotNull { group ->
            val items = group.items.filterNot { item ->
                item.path in ChannelRules.exposedPaths ||
                    ChannelRules.notChannels.any { item.path == it || item.path.startsWith("$it/") }
            }
            if (items.isEmpty()) null else group.copy(items = items)
        }

    /**
     * 下拉刷新某个目录：从第一页重拉，但**不**把状态打回 Loading——
     * 列表留在原地，只有顶部的 M3E 指示器在转。失败时保留旧列表。
     */
    fun refresh(kind: String) {
        if (kind in _refreshing.value) return
        _refreshing.value = _refreshing.value + kind
        viewModelScope.launch {
            try {
                val page = fetch(kind, page = 1)
                update(
                    kind,
                    if (page.items.isEmpty()) {
                        DirectoryUiState.Empty
                    } else {
                        DirectoryUiState.Content(page.items, page.nextPage)
                    },
                )
            } catch (error: Exception) {
                if (_states.value[kind] !is DirectoryUiState.Content) {
                    update(kind, DirectoryUiState.Error(error.toUiMessage()))
                }
            } finally {
                _refreshing.value = _refreshing.value - kind
            }
        }
    }

    fun loadMore(kind: String) {
        val current = _states.value[kind] as? DirectoryUiState.Content ?: return
        val next = current.nextPage ?: return
        if (current.loadingMore) return

        update(kind, current.copy(loadingMore = true))
        viewModelScope.launch {
            update(
                kind,
                try {
                    val page = fetch(kind, page = next)
                    current.copy(
                        // CRITICAL: 必须按 `path` 去重再拼。站点的分页会重叠（下一页开头又出现上一页的条目），
                    // 而列表的 key 就是 path——重复 key 会让 LazyVerticalGrid 直接抛
                    // `Key "…" was already used` 崩溃（真机踩过：/actresses/柊もみじ）。
                    items = (current.items + page.items).distinctBy { it.path },
                        nextPage = page.nextPage,
                        loadingMore = false,
                    )
                } catch (error: Exception) {
                    current.copy(loadingMore = false)
                },
            )
        }
    }

    fun setSort(value: String) {
        if (_sort.value == value) return
        _sort.value = value
        reloadActresses()
    }

    /** 切换某一组筛选；传 null 表示清除这一组。 */
    fun setFilter(key: String, value: String?) {
        val next = if (value == null) _filters.value - key else _filters.value + (key to value)
        if (next == _filters.value) return
        _filters.value = next
        reloadActresses()
    }

    fun resetFilters() {
        if (_filters.value.isEmpty()) return
        _filters.value = emptyMap()
        reloadActresses()
    }

    private fun reloadActresses() {
        // 条件变了必须从第一页重来，丢掉已累积的列表。
        _states.value = _states.value - DiscoverTab.ACTRESSES.kind
        load(DiscoverTab.ACTRESSES.kind)
    }

    private fun update(kind: String, state: DirectoryUiState) {
        _states.value = _states.value + (kind to state)
    }
}
