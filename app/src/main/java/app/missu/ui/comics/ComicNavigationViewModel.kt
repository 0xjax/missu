package app.missu.ui.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.comic.ComicFeed
import app.missu.data.comic.ComicListPage
import app.missu.data.comic.ComicRepository
import app.missu.data.comic.ComicSource
import app.missu.data.store.ComicSourceStore
import app.missu.data.store.SearchHistory
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ComicListUiState {
    data object Loading : ComicListUiState
    data object Empty : ComicListUiState
    data class Error(val message: UiMessage) : ComicListUiState
    data class Content(
        val items: List<app.missu.data.comic.ComicSummary>,
        val nextPage: Int?,
        val more: LoadMoreState = LoadMoreState.Idle,
    ) : ComicListUiState
}

enum class ComicSearchScope {
    CURRENT,
    ALL,
}

class ComicNavigationViewModel(
    private val repository: ComicRepository,
    private val store: ComicSourceStore,
    private val history: SearchHistory,
) : ViewModel() {
    private val _source = MutableStateFlow(store.source())
    val source: StateFlow<ComicSource> = _source.asStateFlow()

    private val _feed = MutableStateFlow(ComicFeed.POPULAR)
    val feed: StateFlow<ComicFeed> = _feed.asStateFlow()

    private val _category = MutableStateFlow(app.missu.data.comic.KalamCategory.ALL)
    val category: StateFlow<app.missu.data.comic.KalamCategory> = _category.asStateFlow()

    private val _listState = MutableStateFlow<ComicListUiState>(ComicListUiState.Loading)
    val listState: StateFlow<ComicListUiState> = _listState.asStateFlow()

    private val _searchState = MutableStateFlow<ComicListUiState>(ComicListUiState.Empty)
    val searchState: StateFlow<ComicListUiState> = _searchState.asStateFlow()

    private val _aggregateSearchState = MutableStateFlow<Map<ComicSource, ComicListUiState>>(emptyMap())
    val aggregateSearchState: StateFlow<Map<ComicSource, ComicListUiState>> = _aggregateSearchState.asStateFlow()

    private val _searchScope = MutableStateFlow(ComicSearchScope.CURRENT)
    val searchScope: StateFlow<ComicSearchScope> = _searchScope.asStateFlow()

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private val _recent = MutableStateFlow(history.recent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private var listJob: Job? = null
    private var searchJob: Job? = null
    private val aggregateJobs = mutableMapOf<ComicSource, Job>()

    fun load() {
        if (_listState.value !is ComicListUiState.Loading) return
        loadList(page = 1, append = false)
    }

    fun refresh() {
        if (_listState.value is ComicListUiState.Loading) return
        loadList(page = 1, append = false)
    }

    fun setSource(value: ComicSource) {
        if (_source.value == value) return
        listJob?.cancel()
        cancelSearch()
        store.setSource(value)
        _source.value = value
        _feed.value = ComicFeed.POPULAR
        _category.value = app.missu.data.comic.KalamCategory.ALL
        _listState.value = ComicListUiState.Loading
        clearSearchState()
        load()
    }

    fun setFeed(value: ComicFeed) {
        if (_feed.value == value || !_source.value.supportsLatest) return
        _feed.value = value
        _listState.value = ComicListUiState.Loading
        load()
    }

    fun setCategory(value: app.missu.data.comic.KalamCategory) {
        if (_category.value == value || _source.value != ComicSource.KALAM) return
        _category.value = value
        _listState.value = ComicListUiState.Loading
        load()
    }

    fun search(query: String) {
        val value = query.trim()
        if (value.isEmpty()) return
        _keyword.value = value
        loadSearch(value, page = 1, append = false)
    }

    fun setSearchScope(value: ComicSearchScope) {
        if (_searchScope.value == value) return
        _searchScope.value = value
        _keyword.value.takeIf { it.isNotBlank() }?.let { loadSearch(it, page = 1, append = false) }
    }

    fun openSourceSearch(value: ComicSource) {
        store.setSource(value)
        _source.value = value
        _feed.value = ComicFeed.POPULAR
        _category.value = app.missu.data.comic.KalamCategory.ALL
        _listState.value = ComicListUiState.Loading
        _searchScope.value = ComicSearchScope.CURRENT
        _keyword.value.takeIf { it.isNotBlank() }?.let { loadSearch(it, page = 1, append = false) }
    }

    fun retrySourceSearch(value: ComicSource) {
        val query = _keyword.value.takeIf { it.isNotBlank() } ?: return
        aggregateJobs.remove(value)?.cancel()
        _aggregateSearchState.value = _aggregateSearchState.value + (value to ComicListUiState.Loading)
        aggregateJobs[value] = viewModelScope.launch { loadAggregateSource(value, query) }
    }

    fun clearSearch() {
        cancelSearch()
        clearSearchState()
    }

    fun clearSearchHistory() {
        history.clear()
        _recent.value = emptyList()
    }

    private fun clearSearchState() {
        _keyword.value = ""
        _searchState.value = ComicListUiState.Empty
        _aggregateSearchState.value = emptyMap()
        _searchScope.value = ComicSearchScope.CURRENT
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        aggregateJobs.values.forEach { it.cancel() }
        aggregateJobs.clear()
    }

    fun loadMore(search: Boolean = false) {
        if (search && _searchScope.value == ComicSearchScope.ALL) return
        val state = if (search) _searchState.value else _listState.value
        val content = state as? ComicListUiState.Content ?: return
        val next = content.nextPage ?: return
        if (content.more == LoadMoreState.Loading) return
        if (search) {
            loadSearch(_keyword.value, next, append = true)
        } else {
            loadList(next, append = true)
        }
    }

    private fun loadList(page: Int, append: Boolean) {
        listJob?.cancel()
        val previous = _listState.value as? ComicListUiState.Content
        if (append && previous != null) _listState.value = previous.copy(more = LoadMoreState.Loading)
        val source = _source.value
        val feed = _feed.value
        val category = _category.value.takeIf { source == ComicSource.KALAM }?.value
        listJob = viewModelScope.launch {
            runCatching {
                repository[source].getComics(feed, page, category)
            }.onSuccess { result ->
                _listState.value = result.toState(previous, append)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _listState.value = if (previous != null) {
                    previous.copy(more = if (append) LoadMoreState.Failed else LoadMoreState.Idle)
                } else {
                    ComicListUiState.Error(error.toUiMessage())
                }
            }
        }
    }

    private fun loadSearch(query: String, page: Int, append: Boolean) {
        cancelSearch()
        if (_searchScope.value == ComicSearchScope.ALL) {
            _aggregateSearchState.value = repository.available.associate { it.source to ComicListUiState.Loading }
            repository.available.forEach { api ->
                aggregateJobs[api.source] = viewModelScope.launch { loadAggregateSource(api.source, query) }
            }
            return
        }
        loadCurrentSearch(query, page, append)
    }

    private fun loadCurrentSearch(query: String, page: Int, append: Boolean) {
        val previous = _searchState.value as? ComicListUiState.Content
        _searchState.value = if (append && previous != null) {
            previous.copy(more = LoadMoreState.Loading)
        } else {
            ComicListUiState.Loading
        }
        val source = _source.value
        searchJob = viewModelScope.launch {
            runCatching {
                repository[source].search(query, page)
            }.onSuccess { result ->
                recordSearch(query)
                _searchState.value = result.toState(previous, append)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _searchState.value = if (append && previous != null) {
                    previous.copy(more = LoadMoreState.Failed)
                } else {
                    ComicListUiState.Error(error.toUiMessage())
                }
            }
        }
    }

    private suspend fun loadAggregateSource(source: ComicSource, query: String) {
        val result = runCatching { repository[source].search(query, 1) }
        result.onSuccess { page ->
            recordSearch(query)
            _aggregateSearchState.value = _aggregateSearchState.value +
                (source to page.toState(previous = null, append = false))
        }.onFailure { error ->
            if (error is CancellationException) throw error
            _aggregateSearchState.value = _aggregateSearchState.value +
                (source to ComicListUiState.Error(error.toUiMessage()))
        }
    }

    private fun recordSearch(query: String) {
        history.record(query)
        _recent.value = history.recent()
    }

    private fun ComicListPage.toState(previous: ComicListUiState.Content?, append: Boolean): ComicListUiState {
        val items = if (append && previous != null) {
            (previous.items + items).distinctBy { it.id }
        } else {
            items
        }
        return if (items.isEmpty()) {
            ComicListUiState.Empty
        } else {
            ComicListUiState.Content(items, nextPage)
        }
    }
}
