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

    private fun describe(error: Exception): String = when (error) {
        is ChallengeException -> "站点要求人机验证，请重试"
        is HttpStatusException -> "请求失败（HTTP ${error.status}）"
        else -> error.message ?: error::class.simpleName ?: "未知错误"
    }
}
