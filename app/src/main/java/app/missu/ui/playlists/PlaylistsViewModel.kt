package app.missu.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.PlaylistSummary
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState
    data object Empty : PlaylistsUiState
    data class Error(val message: UiMessage) : PlaylistsUiState
    data class Content(val playlists: List<PlaylistSummary>) : PlaylistsUiState
}

class PlaylistsViewModel(private val api: MissavApi) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    /** 新建片单的一次性状态：null=空闲，否则是错误提示。 */
    private val _createMessage = MutableStateFlow<UiMessage?>(null)
    val createMessage: StateFlow<UiMessage?> = _createMessage.asStateFlow()

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
                PlaylistsUiState.Error(error.toUiMessage())
            }
        }
    }

    /**
     * 新建片单。
     * WARNING: 站点只有表单（`/playlists/create`），没有 JSON 接口——内部会先 GET 表单页读 `_token`。
     * 成功后重新加载列表，失败把错误交给界面显示（不假装成功）。
     */
    fun create(name: String, isPublic: Boolean) {
        val normalized = name.trim()
        if (normalized.isEmpty()) {
            _createMessage.value = UiMessage.PlaylistNameEmpty
            return
        }
        _createMessage.value = null
        viewModelScope.launch {
            try {
                api.createPlaylist(normalized, isPublic)
                load()
            } catch (error: Exception) {
                _createMessage.value = error.toUiMessage()
            }
        }
    }

    fun clearCreateMessage() {
        _createMessage.value = null
    }

}
