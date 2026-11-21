package app.missu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.model.HomeSection
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 首页状态：加载中 / 错误 / 内容。固定板块解析为空属于加载失败，不是合法空页。 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Error(val message: UiMessage) : HomeUiState
    data class Content(val sections: List<HomeSection>) : HomeUiState
}

class HomeViewModel(private val api: MissavApi) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshingRandom = MutableStateFlow(false)
    val refreshingRandom: StateFlow<Boolean> = _refreshingRandom.asStateFlow()

    private val _randomRefreshError = MutableStateFlow<UiMessage?>(null)
    val randomRefreshError: StateFlow<UiMessage?> = _randomRefreshError.asStateFlow()

    private var requestJob: Job? = null
    private var randomJob: Job? = null
    private var requestedLanguage: String? = null

    /** 每种内容语言只自动加载一次；返回详情或切回页签都不重取。 */
    fun loadIfNeeded(contentLanguage: String) {
        if (requestedLanguage == contentLanguage) return
        requestedLanguage = contentLanguage
        load()
    }

    fun load() {
        requestJob?.cancel()
        randomJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = HomeUiState.Loading
            try {
                val sections = api.getHome()
                _state.value = if (sections.isEmpty()) {
                    HomeUiState.Error(UiMessage.HomeEmpty)
                } else {
                    HomeUiState.Content(sections)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = HomeUiState.Error(error.toUiMessage())
            }
        }
    }

    /** 下拉刷新保留旧内容。 */
    fun refresh() {
        if (_refreshing.value) return
        requestJob?.cancel()
        randomJob?.cancel()
        requestJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val sections = api.getHome()
                if (sections.isEmpty()) {
                    if (_state.value !is HomeUiState.Content) {
                        _state.value = HomeUiState.Error(UiMessage.HomeEmpty)
                    }
                } else {
                    _state.value = HomeUiState.Content(sections)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Error(error.toUiMessage())
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** 随机板块的「刷新」只替换这一块；仍复用首页已有 GET /，不猜独立接口。 */
    fun refreshRandom() {
        if (_refreshingRandom.value || _refreshing.value) return
        randomJob?.cancel()
        randomJob = viewModelScope.launch {
            _refreshingRandom.value = true
            _randomRefreshError.value = null
            try {
                val refreshed = api.getHome().firstOrNull { it.id == "random" } ?: return@launch
                val current = _state.value as? HomeUiState.Content ?: return@launch
                _state.value = current.copy(
                    sections = current.sections.map { if (it.id == "random") refreshed else it },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _randomRefreshError.value = error.toUiMessage()
            } finally {
                _refreshingRandom.value = false
            }
        }
    }

    fun clearRandomRefreshError() {
        _randomRefreshError.value = null
    }
}
