package app.missu.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.api.SourceConfig
import app.missu.data.model.HomeSection
import app.missu.data.model.PinnedHomeList
import app.missu.data.model.VideoListQuery
import app.missu.data.model.VideoSummary
import app.missu.data.store.SettingsStore
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import app.missu.ui.list.VideoListUiState
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

internal fun homeSectionListPath(section: HomeSection): String? =
    // 随机板块在非中文下可能没有「更多」；/random 是已实测的列表路径，不给其他板块猜映射。
    section.morePath ?: "/random".takeIf { section.id == "random" }

internal fun effectivePinnedQuery(pinned: PinnedHomeList?, state: HomeUiState): VideoListQuery? =
    pinned?.query ?: (state as? HomeUiState.Content)?.sections?.firstOrNull()?.let { section ->
        homeSectionListPath(section)?.let(::VideoListQuery)
    }

internal fun nativePinnedSection(pinned: PinnedHomeList, state: HomeUiState): HomeSection? {
    if (pinned.query.sort != null || pinned.query.filter != null) return null
    val sections = (state as? HomeUiState.Content)?.sections ?: return null
    return sections.firstOrNull { section ->
        homeSectionListPath(section)?.let { pinned.query.sameSiteQuery(VideoListQuery(it)) } == true
    }
}

class HomeViewModel(
    private val api: MissavApi,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _pinned = MutableStateFlow(settings.pinnedHomeList())
    val pinned: StateFlow<PinnedHomeList?> = _pinned.asStateFlow()

    /** 任意列表的置顶预览；命中站点原生首页板块时为 null，直接复用该板块。 */
    private val _pinnedState = MutableStateFlow<VideoListUiState?>(_pinned.value?.let { VideoListUiState.Loading })
    val pinnedState: StateFlow<VideoListUiState?> = _pinnedState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _refreshingRandom = MutableStateFlow(false)
    val refreshingRandom: StateFlow<Boolean> = _refreshingRandom.asStateFlow()

    private val _randomRefreshError = MutableStateFlow<UiMessage?>(null)
    val randomRefreshError: StateFlow<UiMessage?> = _randomRefreshError.asStateFlow()

    private var requestJob: Job? = null
    private var pinnedJob: Job? = null
    private var randomJob: Job? = null
    private var requestedLanguage: String? = null
    private var loadedPinnedKey: String? = null
    private var loadedPinnedLanguage: String? = null

    /** 每种内容语言只自动加载一次；返回详情或切回页签都不重取。 */
    fun loadIfNeeded(contentLanguage: String) {
        if (requestedLanguage == contentLanguage) return
        requestedLanguage = contentLanguage
        load()
    }

    fun load() {
        requestJob?.cancel()
        pinnedJob?.cancel()
        randomJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _pinnedState.value = _pinned.value?.let { VideoListUiState.Loading }
            try {
                val sections = api.getHome()
                _state.value = if (sections.isEmpty()) {
                    HomeUiState.Error(UiMessage.HomeEmpty)
                } else {
                    HomeUiState.Content(sections)
                }
                if (sections.isNotEmpty()) fetchPinned(force = false, preserveContent = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = HomeUiState.Error(error.toUiMessage())
            }
        }
    }

    /** 下拉刷新保留旧内容；站点首页与任意置顶列表依次刷新。 */
    fun refresh() {
        if (_refreshing.value) return
        requestJob?.cancel()
        pinnedJob?.cancel()
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
                    fetchPinned(force = true, preserveContent = true)
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

    /** 当前生效项不提供入口；其他列表直接覆盖旧置顶。 */
    fun pin(pinned: PinnedHomeList, currentVideos: List<VideoSummary>, currentNextPage: Int? = null) {
        if (_pinned.value?.query?.sameSiteQuery(pinned.query) == true) return

        settings.setPinnedHomeList(pinned)
        _pinned.value = pinned
        loadedPinnedKey = null
        loadedPinnedLanguage = null
        if (nativePinnedSection(pinned, _state.value) != null) {
            pinnedJob?.cancel()
            _pinnedState.value = null
        } else if (currentVideos.isNotEmpty()) {
            pinnedJob?.cancel()
            _pinnedState.value = VideoListUiState.Content(currentVideos, nextPage = currentNextPage)
            loadedPinnedKey = pinned.query.cacheKey
            loadedPinnedLanguage = SourceConfig.contentLanguage
        } else {
            loadPinned(force = true, preserveContent = false)
        }
    }

    fun retryPinned() {
        loadPinned(force = true, preserveContent = false)
    }

    private fun loadPinned(force: Boolean, preserveContent: Boolean) {
        val pinned = _pinned.value ?: return
        if (nativePinnedSection(pinned, _state.value) != null) {
            _pinnedState.value = null
            return
        }
        if (!force && loadedPinnedKey == pinned.query.cacheKey &&
            loadedPinnedLanguage == SourceConfig.contentLanguage
        ) {
            return
        }
        pinnedJob?.cancel()
        pinnedJob = viewModelScope.launch { fetchPinned(force = true, preserveContent = preserveContent) }
    }

    private suspend fun fetchPinned(force: Boolean, preserveContent: Boolean) {
        val pinned = _pinned.value ?: return
        if (nativePinnedSection(pinned, _state.value) != null) {
            _pinnedState.value = null
            loadedPinnedKey = pinned.query.cacheKey
            loadedPinnedLanguage = SourceConfig.contentLanguage
            return
        }
        if (!force && loadedPinnedKey == pinned.query.cacheKey &&
            loadedPinnedLanguage == SourceConfig.contentLanguage
        ) {
            return
        }
        if (!preserveContent || _pinnedState.value !is VideoListUiState.Content) {
            _pinnedState.value = VideoListUiState.Loading
        }
        try {
            val query = pinned.query
            val page = api.getVideos(query.path, page = 1, sort = query.sort, filter = query.filter)
            _pinnedState.value = if (page.videos.isEmpty()) {
                VideoListUiState.Empty
            } else {
                VideoListUiState.Content(page.videos, nextPage = page.nextPage)
            }
            loadedPinnedKey = query.cacheKey
            loadedPinnedLanguage = SourceConfig.contentLanguage
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!preserveContent || _pinnedState.value !is VideoListUiState.Content) {
                _pinnedState.value = VideoListUiState.Error(error.toUiMessage())
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
