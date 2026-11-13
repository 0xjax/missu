package app.missu.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.model.DirectoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 发现页的两个目录。`kind` 同时是路径段与解析时用来筛链接的关键词。 */
enum class DiscoverTab(val kind: String, val label: String) {
    ACTRESSES("actresses", "女优"),
    GENRES("genres", "类型"),
}

sealed interface DirectoryUiState {
    data object Loading : DirectoryUiState
    data object Empty : DirectoryUiState
    data class Error(val message: String) : DirectoryUiState
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
                    DirectoryUiState.Error(describe(error))
                },
            )
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
                        items = current.items + page.items,
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

    private fun describe(error: Exception): String = when (error) {
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "请求失败（HTTP ${error.status}）"
        is java.io.IOException -> "网络连接中断，请重试"
        else -> error.message ?: error::class.simpleName ?: "未知错误"
    }

    private companion object {
        const val ACTRESS_SORT_VIDEOS = "videos"
    }
}
