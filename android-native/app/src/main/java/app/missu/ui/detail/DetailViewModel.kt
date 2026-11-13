package app.missu.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.api.SessionExpiredException
import app.missu.data.model.VideoDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Error(val message: String) : DetailUiState
    data class Content(val detail: VideoDetail) : DetailUiState
}

class DetailViewModel(
    private val api: MissavApi,
    private val id: String,
) : ViewModel() {
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
