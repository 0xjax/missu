package app.missu.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.VideoSummary
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SavedUiState {
    data object Loading : SavedUiState
    data object Empty : SavedUiState
    data class Error(val message: UiMessage) : SavedUiState
    data class Content(
        val videos: List<VideoSummary>,
        val nextPage: Int?,
        val loadingMore: Boolean = false,
    ) : SavedUiState
}

class SavedViewModel(private val api: MissavApi) : ViewModel() {
    private val _state = MutableStateFlow<SavedUiState>(SavedUiState.Loading)
    val state: StateFlow<SavedUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = SavedUiState.Loading
            _state.value = try {
                var page = api.getSaved()
                // WARNING: 站点对游客的 /saved 返回 200 + 空列表（既不 302 也不 401），
                // 直接显示「还没有收藏」会把「会话丢了」错报成「收藏没了」。
                // 空列表时用记住的凭据确认一次会话，再重取。
                if (page.videos.isEmpty() && api.reloginWithRemembered()) {
                    page = api.getSaved()
                }
                if (page.videos.isEmpty()) {
                    SavedUiState.Empty
                } else {
                    SavedUiState.Content(page.videos, page.nextPage)
                }
            } catch (error: Exception) {
                SavedUiState.Error(error.toUiMessage())
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? SavedUiState.Content ?: return
        val next = current.nextPage ?: return
        if (current.loadingMore) return

        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            _state.value = try {
                val page = api.getSaved(next)
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
