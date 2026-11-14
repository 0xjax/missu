package app.missu.ui.playlists

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

sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    data object Empty : PlaylistDetailUiState
    data class Error(val message: UiMessage) : PlaylistDetailUiState
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
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = PlaylistDetailUiState.Loading
            _state.value = try {
                val detail = api.getPlaylistDetail(id)
                if (detail.videos.isEmpty()) {
                    PlaylistDetailUiState.Empty
                } else {
                    // CRITICAL: 列表 key 是 `id + pageUrl`，重复会让 LazyVerticalGrid 抛重复 key 崩溃
                    // （站点可能把同一部影片列两次）。
                    PlaylistDetailUiState.Content(detail.name.ifEmpty { id }, detail.videos.distinctBy { it.id + it.pageUrl })
                }
            } catch (error: Exception) {
                PlaylistDetailUiState.Error(error.toUiMessage())
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
                    if (failed.size == 1) _message.value = error.toUiMessage()
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
                _message.value = UiMessage.PartialRemoveFailed(failed.size)
            }
        }
    }

}
