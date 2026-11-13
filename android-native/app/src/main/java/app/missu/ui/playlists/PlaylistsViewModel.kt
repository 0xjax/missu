package app.missu.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.api.SessionExpiredException
import app.missu.data.model.PlaylistSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState
    data object Empty : PlaylistsUiState
    data class Error(val message: String) : PlaylistsUiState
    data class Content(val playlists: List<PlaylistSummary>) : PlaylistsUiState
}

class PlaylistsViewModel(private val api: MissavApi) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = PlaylistsUiState.Loading
            _state.value = try {
                var playlists = api.getPlaylists()
                // WARNING: 站点对游客的 /playlists 返回 200 + 空列表，空结果先用记住的凭据确认会话再重取。
                if (playlists.isEmpty() && api.reloginWithRemembered()) {
                    playlists = api.getPlaylists()
                }
                if (playlists.isEmpty()) {
                    PlaylistsUiState.Empty
                } else {
                    PlaylistsUiState.Content(playlists)
                }
            } catch (error: Exception) {
                PlaylistsUiState.Error(describe(error))
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
