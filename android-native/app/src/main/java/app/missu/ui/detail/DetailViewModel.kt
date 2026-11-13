package app.missu.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
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

    private fun describe(error: Exception): String = when (error) {
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "请求失败（HTTP ${error.status}）"
        // NOTE: OkHttp 的连接类异常 message 是英文（"connection closed" 之类），不直接甩给用户。
        is java.io.IOException -> "网络连接中断，请重试"
        else -> error.message ?: error::class.simpleName ?: "未知错误"
    }
}
