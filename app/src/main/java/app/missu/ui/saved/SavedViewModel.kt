package app.missu.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.VideoSummary
import app.missu.ui.common.LoadMoreState
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
        val more: LoadMoreState = LoadMoreState.Idle,
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
        // NOTE: 只挡 Loading —— Failed 必须能被脚注上的「重试」再调一次；自动加载那边
        // 由界面的 enabled 挡住，所以失败之后不会靠滚动偷偷重试。
        if (current.more == LoadMoreState.Loading) return

        _state.value = current.copy(more = LoadMoreState.Loading)
        viewModelScope.launch {
            _state.value = try {
                val page = api.getSaved(next)
                current.copy(
                    // CRITICAL: 按界面 key 去重（同 id 同 pageUrl），分页重叠会让 LazyVerticalGrid 抛重复 key 崩溃。
                    videos = (current.videos + page.videos).distinctBy { it.id + it.pageUrl },
                    nextPage = page.nextPage,
                    more = LoadMoreState.Idle,
                )
            } catch (error: Exception) {
                // WARNING: 不能静默复位成 Idle——那一页没取到，界面必须留一个重试入口，
                // 否则列表就在底部无声卡住，用户分不清「已经到底」和「坏了」。
                current.copy(more = LoadMoreState.Failed)
            }
        }
    }
}
