package app.missu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.ChallengeException
import app.missu.data.api.HttpStatusException
import app.missu.data.api.MissavApi
import app.missu.data.model.HomeSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 首页四态：加载中 / 空 / 错误 / 内容。 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
    data class Content(val sections: List<HomeSection>) : HomeUiState
}

class HomeViewModel(private val api: MissavApi) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 下拉刷新中（内容留在原地，只转顶部指示器）。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _state.value = try {
                val sections = api.getHome()
                if (sections.isEmpty()) HomeUiState.Empty else HomeUiState.Content(sections)
            } catch (error: Exception) {
                HomeUiState.Error(describe(error))
            }
        }
    }

    /**
     * 下拉刷新：不把页面打回 Loading（否则列表会闪一下变空）。
     * NOTE: 失败时只有在「本来就没有内容」的情况下才切错误页，否则保留旧内容——
     * 刷一下就把已经看到的列表清掉是最烦的。
     */
    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val sections = api.getHome()
                _state.value = if (sections.isEmpty()) HomeUiState.Empty else HomeUiState.Content(sections)
            } catch (error: Exception) {
                if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Error(describe(error))
            } finally {
                _refreshing.value = false
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
