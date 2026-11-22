package app.missu.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.model.VideoList
import app.missu.data.model.VideoListQuery
import app.missu.data.model.VideoSummary
import app.missu.data.store.ListQueryStore
import app.missu.data.store.SearchHistory
import app.missu.data.store.SearchHistoryStore
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(val keyword: String) : SearchUiState
    data class Empty(val keyword: String) : SearchUiState
    data class Error(val keyword: String, val message: UiMessage) : SearchUiState
    data class Content(
        val keyword: String,
        val videos: List<VideoSummary>,
        val nextPage: Int?,
        val more: LoadMoreState = LoadMoreState.Idle,
    ) : SearchUiState
}

class SearchViewModel internal constructor(
    private val history: SearchHistory,
    private val queryStore: ListQueryStore? = null,
    private val searchPage: suspend (
        keyword: String,
        page: Int,
        sort: VideoListOptions.Sort?,
        filter: VideoListOptions.Filter?,
    ) -> VideoList,
) : ViewModel() {
    constructor(api: MissavApi, history: SearchHistoryStore, queryStore: ListQueryStore) : this(
        history = history,
        queryStore = queryStore,
        searchPage = { keyword, page, sort, filter -> api.searchVideos(keyword, page, sort, filter) },
    )
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _sort = MutableStateFlow(queryStore?.get(SEARCH_QUERY_PATH)?.sort)
    val sort: StateFlow<VideoListOptions.Sort?> = _sort.asStateFlow()
    private val _filter = MutableStateFlow(queryStore?.get(SEARCH_QUERY_PATH)?.filter)
    val filter: StateFlow<VideoListOptions.Filter?> = _filter.asStateFlow()
    private val _detectedSort = MutableStateFlow<VideoListOptions.Sort?>(null)
    val detectedSort: StateFlow<VideoListOptions.Sort?> = _detectedSort.asStateFlow()

    /** 最近搜索关键词，供空状态展示。 */
    private val _recent = MutableStateFlow(history.recent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()
    private var requestJob: Job? = null

    fun search(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) {
            clear()
            return
        }
        requestJob?.cancel()
        _state.value = SearchUiState.Loading(normalized)
        requestJob = viewModelScope.launch {
            _state.value = try {
                val page = searchPage(normalized, 1, _sort.value, _filter.value)
                if (_sort.value == null) _detectedSort.value = page.sort
                // 只在搜索成功后记入历史，避免把打错的词也留下。
                history.record(normalized)
                _recent.value = history.recent()
                if (page.videos.isEmpty()) {
                    SearchUiState.Empty(normalized)
                } else {
                    SearchUiState.Content(normalized, page.videos, page.nextPage)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SearchUiState.Error(normalized, error.toUiMessage())
            }
        }
    }

    fun setSort(value: VideoListOptions.Sort?) {
        if (_sort.value == value) return
        _sort.value = value
        saveQuery()
        reload()
    }

    fun setFilter(value: VideoListOptions.Filter?) {
        if (_filter.value == value) return
        _filter.value = value
        saveQuery()
        reload()
    }

    private fun saveQuery() {
        queryStore?.put(VideoListQuery(SEARCH_QUERY_PATH, sort = _sort.value, filter = _filter.value))
    }

    private fun reload() {
        val keyword = when (val current = _state.value) {
            is SearchUiState.Loading -> current.keyword
            is SearchUiState.Empty -> current.keyword
            is SearchUiState.Error -> current.keyword
            is SearchUiState.Content -> current.keyword
            SearchUiState.Idle -> return
        }
        search(keyword)
    }

    fun clearHistory() {
        history.clear()
        _recent.value = emptyList()
    }

    /** 退出搜索、回到原页面内容。 */
    fun clear() {
        requestJob?.cancel()
        _state.value = SearchUiState.Idle
    }

    /** 追加下一页；失败写进 [LoadMoreState.Failed] 并保留已加载结果，不整页报错。 */
    fun loadMore() {
        val current = _state.value as? SearchUiState.Content ?: return
        val next = current.nextPage ?: return
        // NOTE: 只挡 Loading —— Failed 必须能被脚注上的「重试」再调一次；自动加载那边
        // 由界面的 enabled 挡住，所以失败之后不会靠滚动偷偷重试。
        if (current.more == LoadMoreState.Loading) return

        requestJob?.cancel()
        _state.value = current.copy(more = LoadMoreState.Loading)
        requestJob = viewModelScope.launch {
            _state.value = try {
                val page = searchPage(current.keyword, next, _sort.value, _filter.value)
                current.copy(
                    // CRITICAL: 按界面 key 去重（同 id 同 pageUrl），分页重叠会让 LazyVerticalGrid 抛重复 key 崩溃。
                    videos = (current.videos + page.videos).distinctBy { it.id + it.pageUrl },
                    nextPage = page.nextPage,
                    more = LoadMoreState.Idle,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // WARNING: 不能静默复位成 Idle——那一页没取到，界面必须留一个重试入口，
                // 否则列表就在底部无声卡住，用户分不清「已经到底」和「坏了」。
                current.copy(more = LoadMoreState.Failed)
            }
        }
    }

    private companion object {
        const val SEARCH_QUERY_PATH = "/search"
    }
}
