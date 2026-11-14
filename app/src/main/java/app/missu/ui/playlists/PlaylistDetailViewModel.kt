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
        /** 正在移除的影片 id 集合；非空时不再接受新的移除。 */
        val removingIds: Set<String> = emptySet(),
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
     * 从当前片单里批量移除影片（整理模式选中的那几条）。
     * NOTE: 站点只有单条 `DELETE /api/playlists/{片单id}/{影片id}`，没有批量接口，所以这里串行发；
     * 单条失败不中断，成功的照样从列表里去掉，最后把失败条数报出来。
     * NOTE: 成功后只删本地这几条，不整页重拉：站点自己的实现也是直接隐藏卡片。
     */
    fun removeAll(videoIds: List<String>) {
        val content = _state.value as? PlaylistDetailUiState.Content ?: return
        if (content.removingIds.isNotEmpty() || videoIds.isEmpty()) return

        _message.value = null
        _state.value = content.copy(removingIds = videoIds.toSet())
        viewModelScope.launch {
            val failed = mutableListOf<String>()
            for (videoId in videoIds) {
                try {
                    api.removePlaylistItem(id, videoId)
                } catch (error: Exception) {
                    failed += videoId
                    if (failed.size == 1) _message.value = describe(error)
                }
            }

            val removed = videoIds.toSet() - failed.toSet()
            val rest = content.videos.filterNot { it.id in removed }
            _state.value = if (rest.isEmpty()) {
                PlaylistDetailUiState.Empty
            } else {
                content.copy(videos = rest, removingIds = emptySet())
            }
            if (failed.isNotEmpty()) {
                _message.value = "有 ${failed.size} 部没移除成功，可以再试一次"
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
