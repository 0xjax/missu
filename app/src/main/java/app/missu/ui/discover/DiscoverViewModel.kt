package app.missu.ui.discover

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.R
import app.missu.data.model.DirectoryItem
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 发现页的页签。`kind` 只对**目录**有意义（它同时是路径段与解析时用来筛链接的关键词）；
 * 「热门」不是目录，它只是三个列表页入口（见 `DiscoverScreen` 的 `HotPeriod`），所以不取数。
 */
enum class DiscoverTab(val kind: String, @param:StringRes val labelRes: Int) {
    ACTRESSES("actresses", R.string.discover_section_actresses),
    GENRES("genres", R.string.discover_section_genres),
    MAKERS("makers", R.string.discover_section_makers),
    HOT("hot", R.string.discover_section_hot),
    ;

    /** 需要向后端取数的页签。「热门」的条目是本地常量，不占一次请求。 */
    val isDirectory: Boolean get() = this != HOT
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

/** 两个目录各自独立加载，用一个 Map 存状态，避免写两份几乎一样的代码。 */
class DiscoverViewModel(private val api: MissavApi) : ViewModel() {

    private val _states = MutableStateFlow<Map<String, DirectoryUiState>>(emptyMap())
    val states: StateFlow<Map<String, DirectoryUiState>> = _states.asStateFlow()

    /** 女优排序：`videos`（影片数，默认）/ `debut`（出道年）。 */
    private val _sort = MutableStateFlow(ACTRESS_SORT_VIDEOS)
    val sort: StateFlow<String> = _sort.asStateFlow()

    /** 女优筛选：`height / cup / age / debut` → 值。空 Map 表示不筛。 */
    private val _filters = MutableStateFlow<Map<String, String>>(emptyMap())
    val filters: StateFlow<Map<String, String>> = _filters.asStateFlow()

    /** 正在下拉刷新的目录（同一时间只有一个页签可见）。 */
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    fun load(kind: String) {
        if (_states.value[kind] is DirectoryUiState.Loading) return
        update(kind, DirectoryUiState.Loading)
        viewModelScope.launch {
            update(
                kind,
                try {
                    val page = if (kind == DiscoverTab.ACTRESSES.kind) {
                        api.getActresses(sort = _sort.value, filters = _filters.value)
                    } else {
                        api.getDirectory(kind)
                    }
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
     * 下拉刷新某个目录：从第一页重拉，但**不**把状态打回 Loading——
     * 列表留在原地，只有顶部的 M3E 指示器在转。失败时保留旧列表。
     */
    fun refresh(kind: String) {
        if (kind in _refreshing.value) return
        _refreshing.value = _refreshing.value + kind
        viewModelScope.launch {
            try {
                val page = if (kind == DiscoverTab.ACTRESSES.kind) {
                    api.getActresses(sort = _sort.value, filters = _filters.value)
                } else {
                    api.getDirectory(kind)
                }
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
                    val page = if (kind == DiscoverTab.ACTRESSES.kind) {
                        api.getActresses(page = next, sort = _sort.value, filters = _filters.value)
                    } else {
                        api.getDirectory(kind, next)
                    }
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

    private companion object {
        const val ACTRESS_SORT_VIDEOS = "videos"
    }
}
