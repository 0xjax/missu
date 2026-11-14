package app.missu.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.api.SessionExpiredException
import app.missu.data.store.HistoryEntry
import app.missu.data.store.HistoryStore
import app.missu.data.model.VideoDetail
import app.missu.data.model.VideoPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 片单面板：隐藏 / 加载 / 出错 / 列表。 */
sealed interface PlaylistUiState {
    data object Hidden : PlaylistUiState
    data object Loading : PlaylistUiState
    data class Error(val message: String) : PlaylistUiState
    data class Content(
        val items: List<VideoPlaylist>,
        val pendingKey: String? = null,
    ) : PlaylistUiState
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
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

    fun openPlaylists() {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        _playlists.value = PlaylistUiState.Loading
        viewModelScope.launch {
            _playlists.value = try {
                PlaylistUiState.Content(api.getVideoPlaylists(detail.id))
            } catch (error: Exception) {
                PlaylistUiState.Error(describe(error))
            }
        }
    }

    fun closePlaylists() {
        _playlists.value = PlaylistUiState.Hidden
    }

    /** 勾选 / 取消勾选某个片单。401 同样由 withAuthRetry 先自动重登一次。 */
    fun togglePlaylist(item: VideoPlaylist) {
        val detail = (_state.value as? DetailUiState.Content)?.detail ?: return
        val current = _playlists.value as? PlaylistUiState.Content ?: return
        if (current.pendingKey != null) return

        _playlists.value = current.copy(pendingKey = item.key)
        viewModelScope.launch {
            try {
                api.setPlaylistMembership(detail.id, item.key, !item.added)
                _playlists.value = current.copy(
                    items = current.items.map {
                        if (it.key == item.key) it.copy(added = !item.added) else it
                    },
                    pendingKey = null,
                )
            } catch (error: Exception) {
                _playlists.value = current.copy(pendingKey = null)
                _message.value = describe(error)
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
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = try {
                DetailUiState.Content(api.getVideoDetail(id))
            } catch (error: Exception) {
                DetailUiState.Error(describe(error))
            }
        }
    }

    /**
     * 收藏 / 取消收藏。
     * NOTE: 401 时 [MissavApi.withAuthRetry] 会先用记住的凭据重登一次再重试；
     * 到这里还抛 [SessionExpiredException]，说明重登也失败，只能提示重新登录。
     */
    fun toggleSaved() {
        val content = _state.value as? DetailUiState.Content ?: return
        val internalId = content.detail.internalId
        if (internalId == null) {
            _message.value = "没有拿到影片 ID，无法收藏"
            return
        }

        val target = !content.detail.saved
        _message.value = null
        Log.w(TAG, "toggleSaved: internalId=$internalId target=$target")
        viewModelScope.launch {
            try {
                api.setSaved(internalId, target)
                Log.w(TAG, "toggleSaved: ok saved=$target")
                _state.value = DetailUiState.Content(content.detail.copy(saved = target))
            } catch (error: Exception) {
                Log.w(TAG, "toggleSaved: failed ${error::class.simpleName}: ${error.message}")
                _message.value = describe(error)
            }
        }
    }

    private fun describe(error: Exception): String = when (error) {
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "请求失败（HTTP ${error.status}）"
        is SessionExpiredException -> "登录已失效，请重新登录后再收藏"
        // NOTE: OkHttp 的连接类异常 message 是英文（"connection closed" 之类），不直接甩给用户。
        is java.io.IOException -> "网络连接中断，请重试"
        else -> error.message ?: error::class.simpleName ?: "未知错误"
    }

    private companion object {
        // NOTE: 只记操作名与目标状态，不打印任何凭据。
        const val TAG = "MissavSaved"
    }
}
