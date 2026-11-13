package app.missu.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.api.SessionExpiredException
import app.missu.data.model.VideoSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data object Empty : PlaylistDetailUiState
    data class Error(val message: String) : PlaylistDetailUiState
    data class Content(val name: String, val videos: List<VideoSummary>) : PlaylistDetailUiState
}

class PlaylistDetailViewModel(
    private val api: MissavApi,
    private val id: String,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = PlaylistDetailUiState.Loading
            _state.value = try {
                val detail = api.getPlaylistDetail(id)
                if (detail.videos.isEmpty()) {
                    PlaylistDetailUiState.Empty
                } else {
                    PlaylistDetailUiState.Content(detail.name.ifEmpty { id }, detail.videos)
                }
            } catch (error: Exception) {
                PlaylistDetailUiState.Error(describe(error))
            }
        }
    }

    private fun describe(error: Exception): String = when (error) {
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "请求失败（HTTP ${error.status}）"
        is SessionExpiredException -> "登录已失效，请重新登录"
        is java.io.IOException -> "网络连接中断，请重试"
        else -> error.message ?: error::class.simpleName ?: "未知错误"
    }
}
