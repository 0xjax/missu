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
    data class Content(
        val name: String,
        val videos: List<VideoSummary>,
        /** 正在移除的影片 id；非空时同一时间不再接受新的移除。 */
        val removingId: String? = null,
    ) : PlaylistDetailUiState
}

class PlaylistDetailViewModel(
    private val api: MissavApi,
    private val id: String,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    /** 移除失败的一次性提示；成功时保持为空。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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

    /**
     * 从当前片单里移除一部影片。
     * NOTE: 走站点片单详情页自己的 `DELETE /api/playlists/{片单id}/{影片id}`（见
     * [MissavApi.removePlaylistItem]），不需要详情页面板那条要 `key` 的入口。
     * 成功后只删本地这一条，不整页重拉：站点自己的实现也是直接隐藏卡片。
     */
    fun remove(videoId: String) {
        val content = _state.value as? PlaylistDetailUiState.Content ?: return
        if (content.removingId != null) return

        _message.value = null
        _state.value = content.copy(removingId = videoId)
        viewModelScope.launch {
            try {
                api.removePlaylistItem(id, videoId)
                val rest = content.videos.filterNot { it.id == videoId }
                _state.value = if (rest.isEmpty()) {
                    PlaylistDetailUiState.Empty
                } else {
                    content.copy(videos = rest, removingId = null)
                }
            } catch (error: Exception) {
                _state.value = content.copy(removingId = null)
                _message.value = describe(error)
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
