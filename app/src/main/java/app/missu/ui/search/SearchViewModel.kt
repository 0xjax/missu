package app.missu.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.VideoSummary
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

class SearchViewModel(
    private val api: MissavApi,
    private val history: SearchHistoryStore,
) : ViewModel() {
    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

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
                val page = api.searchVideos(normalized)
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

    fun clearHistory() {
        history.clear()
        _recent.value = emptyList()
    }

    /** 退出搜索、回到首页内容。 */
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
                val page = api.searchVideos(current.keyword, next)
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
}
