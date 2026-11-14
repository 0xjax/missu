package app.missu.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.VideoSummary
import app.missu.data.store.SearchHistoryStore
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data object Empty : SearchUiState
    data class Error(val message: UiMessage) : SearchUiState
    data class Content(
        val keyword: String,
        val videos: List<VideoSummary>,
        val nextPage: Int?,
        val loadingMore: Boolean = false,
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

    fun search(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = try {
                val page = api.searchVideos(normalized)
                // 只在搜索成功后记入历史，避免把打错的词也留下。
                history.record(normalized)
                _recent.value = history.recent()
                if (page.videos.isEmpty()) {
                    SearchUiState.Empty
                } else {
                    SearchUiState.Content(normalized, page.videos, page.nextPage)
                }
            } catch (error: Exception) {
                SearchUiState.Error(error.toUiMessage())
            }
        }
    }

    fun clearHistory() {
        history.clear()
        _recent.value = emptyList()
    }

    /** 退出搜索、回到首页内容。 */
    fun clear() {
        _state.value = SearchUiState.Idle
    }

    /** 追加下一页；失败只把 loadingMore 复位并保留已加载结果，不整页报错。 */
    fun loadMore() {
        val current = _state.value as? SearchUiState.Content ?: return
        val next = current.nextPage ?: return
        if (current.loadingMore) return

        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            _state.value = try {
                val page = api.searchVideos(current.keyword, next)
                current.copy(
                    videos = current.videos + page.videos,
                    nextPage = page.nextPage,
                    loadingMore = false,
                )
            } catch (error: Exception) {
                current.copy(loadingMore = false)
            }
        }
    }
}
