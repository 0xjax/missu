package app.missu.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.store.HistoryEntry
import app.missu.data.store.HistoryStore
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 片单面板：隐藏 / 加载 / 出错 / 列表。 */
sealed interface PlaylistUiState {
    data object Hidden : PlaylistUiState
    data object Loading : PlaylistUiState
    data class Error(val message: UiMessage) : PlaylistUiState
    data class Content(
        val items: List<VideoPlaylist>,
        val pendingKey: String? = null,
    ) : PlaylistUiState
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: UiMessage) : DetailUiState
    data class Content(val detail: VideoDetail) : DetailUiState
}

class DetailViewModel(
    private val api: MissavApi,
    private val id: String,
    private val history: HistoryStore,
) : ViewModel() {

    /** 上次看到哪儿；0 表示没有记录，从头发放。 */
    val startPositionMs: Long = history.get(id)?.positionMs ?: 0L

    private val _playlists = MutableStateFlow<PlaylistUiState>(PlaylistUiState.Hidden)
    val playlists: StateFlow<PlaylistUiState> = _playlists.asStateFlow()
    private var playlistJob: Job? = null
    private var loadJob: Job? = null
    private var savedJob: Job? = null

    fun openPlaylists() {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        playlistJob?.cancel()
        _playlists.value = PlaylistUiState.Loading
        playlistJob = viewModelScope.launch {
            _playlists.value = try {
                PlaylistUiState.Content(api.getVideoPlaylists(detail.id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                PlaylistUiState.Error(error.toUiMessage())
            }
        }
    }

    fun closePlaylists() {
        playlistJob?.cancel()
        _playlists.value = PlaylistUiState.Hidden
    }

    /** 勾选 / 取消勾选某个片单。401 同样由 withAuthRetry 先自动重登一次。 */
    fun togglePlaylist(item: VideoPlaylist) {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        val current = _playlists.value as? PlaylistUiState.Content ?: return
        if (current.pendingKey != null) return

        _playlists.value = current.copy(pendingKey = item.key)
        playlistJob?.cancel()
        playlistJob = viewModelScope.launch {
            try {
                api.setPlaylistMembership(detail.id, item.key, !item.added)
                _playlists.value = current.copy(
                    items = current.items.map {
                        if (it.key == item.key) it.copy(added = !item.added) else it
                    },
                    pendingKey = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _playlists.value = current.copy(pendingKey = null)
                _message.value = error.toUiMessage()
            }
        }
    }

    /** 播放器上报的进度写进历史。 */
    fun reportProgress(positionMs: Long) {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        if (positionMs <= 0) return
        history.record(
            HistoryEntry(
                id = detail.id,
                title = detail.title,
                thumbnailUrl = detail.thumbnailUrl,
                duration = detail.duration,
                positionMs = positionMs,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    /** 收藏等一次性操作失败的提示；成功时保持为空。 */
    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = try {
                DetailUiState.Content(api.getVideoDetail(id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DetailUiState.Error(error.toUiMessage())
            }
        }
    }

    /**
     * 收藏 / 取消收藏。
     * NOTE: 401 时 [MissavApi.withAuthRetry] 会先用记住的凭据重登一次再重试；
     * 到这里还抛 [SessionExpiredException]，说明重登也失败，只能提示重新登录。
     */
    fun toggleSaved() {
        if (savedJob?.isActive == true) return
        val content = _state.value as? DetailUiState.Content ?: return
        val internalId = content.detail.internalId
        if (internalId == null) {
            _message.value = UiMessage.NoVideoId
            return
        }

        val target = !content.detail.saved
        _message.value = null
        Log.w(TAG, "toggleSaved: internalId=$internalId target=$target")
        savedJob = viewModelScope.launch {
            try {
                api.setSaved(internalId, target)
                Log.w(TAG, "toggleSaved: ok saved=$target")
                _state.value = DetailUiState.Content(content.detail.copy(saved = target))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "toggleSaved: failed ${error::class.simpleName}: ${error.message}")
                _message.value = error.toUiMessage()
            }
        }
    }


    private companion object {
        // NOTE: 只记操作名与目标状态，不打印任何凭据。
        const val TAG = "MissavSaved"
    }
}
