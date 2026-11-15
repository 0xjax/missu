package app.missu.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaylistEditUiState {
    data object Loading : PlaylistEditUiState
    data class Error(val message: UiMessage) : PlaylistEditUiState
    data class Ready(
        val name: String,
        val description: String,
        val isPublic: Boolean,
    ) : PlaylistEditUiState
}

/**
 * 编辑片单：改名称 / 描述 / 是否公开，以及删除。
 *
 * WARNING: 站点这三件事都只有 Blade 表单（更新 POST 编辑页自身、删除是 `_method=delete`），
 * 所以每次操作都要先 GET 表单页拿 `_token`（见 [MissavApi.updatePlaylist] / [MissavApi.deletePlaylist]）。
 */
class PlaylistEditViewModel(
    private val api: MissavApi,
    private val id: String,
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistEditUiState>(PlaylistEditUiState.Loading)
    val state: StateFlow<PlaylistEditUiState> = _state.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private var operationJob: Job? = null

    fun load() {
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            _state.value = PlaylistEditUiState.Loading
            _state.value = try {
                val page = api.getPlaylistEdit(id)
                PlaylistEditUiState.Ready(
                    name = page.name,
                    description = page.description,
                    isPublic = page.isPublic,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PlaylistEditUiState.Error(error.toUiMessage())
            }
        }
    }

    fun save(name: String, description: String, isPublic: Boolean, onDone: () -> Unit) {
        if (_busy.value) return
        if (name.isBlank()) {
            _message.value = UiMessage.PlaylistNameEmpty
            return
        }
        _busy.value = true
        _message.value = null
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            try {
                api.updatePlaylist(id, name.trim(), description.trim(), isPublic)
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = error.toUiMessage()
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        operationJob?.cancel()
        operationJob = viewModelScope.launch {
            try {
                api.deletePlaylist(id)
                onDone()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _message.value = error.toUiMessage()
            } finally {
                _busy.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

}
