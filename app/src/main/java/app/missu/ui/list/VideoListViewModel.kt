package app.missu.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.model.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoListUiState {
    data object Loading : VideoListUiState
    data object Empty : VideoListUiState
    data class Error(val message: String) : VideoListUiState
    data class Content(
        val videos: List<VideoSummary>,
        val nextPage: Int?,
        val loadingMore: Boolean = false,
    ) : VideoListUiState
}

/**
 * 通用列表页。`path` 来自详情页信息行（如 `/actresses/xxxx`、`/genres/yyyy`）。
 */
class VideoListViewModel(
    private val api: MissavApi,
    private val path: String,
) : ViewModel() {

    private val _state = MutableStateFlow<VideoListUiState>(VideoListUiState.Loading)
    val state: StateFlow<VideoListUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = VideoListUiState.Loading
            _state.value = try {
                val page = api.getVideos(path)
                if (page.videos.isEmpty()) {
                    VideoListUiState.Empty
                } else {
                    VideoListUiState.Content(page.videos, page.nextPage)
                }
            } catch (error: Exception) {
                VideoListUiState.Error(describe(error))
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? VideoListUiState.Content ?: return
        val next = current.nextPage ?: return
        if (current.loadingMore) return

        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            _state.value = try {
                val page = api.getVideos(path, next)
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

    private fun describe(error: Exception): String = when (error) {
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "请求失败（HTTP ${error.status}）"
        is java.io.IOException -> "网络连接中断，请重试"
        else -> error.message ?: error::class.simpleName ?: "未知错误"
    }
}
