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

/** 首页四态：加载中 / 空 / 错误 / 内容。 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Error(val message: UiMessage) : HomeUiState
    data class Content(val sections: List<HomeSection>) : HomeUiState
}

class HomeViewModel(private val api: MissavApi) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 下拉刷新中（内容留在原地，只转顶部指示器）。 */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var requestJob: Job? = null

    fun load() {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _state.value = try {
                val sections = api.getHome()
                if (sections.isEmpty()) HomeUiState.Empty else HomeUiState.Content(sections)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                HomeUiState.Error(error.toUiMessage())
            }
        }
    }

    /**
     * 只在「还没有内容」时拉一次首页；切回页签时用它。
     *
     * NOTE: 目的地每次重新进入组合都会重跑 `LaunchedEffect(Unit)`。那里如果直接调 [load]，
     * 已经加载好的首页会被打回 Loading——用户看到的就是「先闪一帧旧内容，再变成转圈」。
     * 已有内容就什么都不做，想要新内容走下拉刷新（[refresh]）。
     */
    fun loadIfNeeded() {
        if (_state.value is HomeUiState.Content || _state.value is HomeUiState.Loading) return
        load()
    }

    /**
     * 下拉刷新：不把页面打回 Loading（否则列表会闪一下变空）。
     * NOTE: 失败时只有在「本来就没有内容」的情况下才切错误页，否则保留旧内容——
     * 刷一下就把已经看到的列表清掉是最烦的。
     */
    fun refresh() {
        if (_refreshing.value) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val sections = api.getHome()
                _state.value = if (sections.isEmpty()) HomeUiState.Empty else HomeUiState.Content(sections)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value !is HomeUiState.Content) _state.value = HomeUiState.Error(error.toUiMessage())
            } finally {
                _refreshing.value = false
            }
        }
    }
}
