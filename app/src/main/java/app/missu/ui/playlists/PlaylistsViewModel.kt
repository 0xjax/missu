package app.missu.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.PlaylistSummary
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()
    private var loadJob: Job? = null
    private var createJob: Job? = null

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
                    // CRITICAL: 列表 key 是 path，重复会让 LazyVerticalGrid 抛重复 key 崩溃（站点可能重复列出同一个片单）。
                    PlaylistsUiState.Content(playlists.distinctBy { it.path })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PlaylistsUiState.Error(error.toUiMessage())
            }
        }
    }

    /**
     * 新建片单。
     * WARNING: 站点只有表单（`/playlists/create`），没有 JSON 接口——内部会先 GET 表单页读 `_token`。
     * 成功后重新加载列表并关闭对话框，失败保留输入与错误提示。
     */
    fun create(name: String, isPublic: Boolean, onSuccess: () -> Unit) {
        val normalized = name.trim()
        if (_creating.value) return
        if (normalized.isEmpty()) {
            _createMessage.value = UiMessage.PlaylistNameEmpty
            return
        }
        _creating.value = true
        _createMessage.value = null
        createJob?.cancel()
        createJob = viewModelScope.launch {
            try {
                api.createPlaylist(normalized, isPublic)
                onSuccess()
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _createMessage.value = error.toUiMessage()
            } finally {
                _creating.value = false
            }
        }
    }

    fun clearCreateMessage() {
        _createMessage.value = null
    }

}
