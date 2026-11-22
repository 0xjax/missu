package app.missu.ui.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.comic.ComicFeed
import app.missu.data.comic.ComicListPage
import app.missu.data.comic.ComicRepository
import app.missu.data.comic.ComicSource
import app.missu.data.comic.ComicSummary
import app.missu.data.store.ComicSourceStore
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
        val items: List<ComicSummary>,
        val nextPage: Int?,
        val more: LoadMoreState = LoadMoreState.Idle,
    ) : ComicListUiState
}

class ComicNavigationViewModel(
    private val repository: ComicRepository,
    private val store: ComicSourceStore,
) : ViewModel() {
    private val _source = MutableStateFlow(store.source())
    val source: StateFlow<ComicSource> = _source.asStateFlow()

    private val _feed = MutableStateFlow(ComicFeed.POPULAR)
    val feed: StateFlow<ComicFeed> = _feed.asStateFlow()

    private val _listState = MutableStateFlow<ComicListUiState>(ComicListUiState.Loading)
    val listState: StateFlow<ComicListUiState> = _listState.asStateFlow()

    private val _searchState = MutableStateFlow<ComicListUiState>(ComicListUiState.Empty)
    val searchState: StateFlow<ComicListUiState> = _searchState.asStateFlow()

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private var listJob: Job? = null
    private var searchJob: Job? = null

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
        searchJob?.cancel()
        store.setSource(value)
        _source.value = value
        _feed.value = ComicFeed.POPULAR
        _listState.value = ComicListUiState.Loading
        _searchState.value = ComicListUiState.Empty
        _keyword.value = ""
        load()
    }

    fun setFeed(value: ComicFeed) {
        if (_feed.value == value || !_source.value.supportsLatest) return
        _feed.value = value
        _listState.value = ComicListUiState.Loading
        load()
    }

    fun search(query: String) {
        val value = query.trim()
        if (value.isEmpty()) return
        _keyword.value = value
        _searchState.value = ComicListUiState.Loading
        loadSearch(value, page = 1, append = false)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _keyword.value = ""
        _searchState.value = ComicListUiState.Empty
    }

    fun loadMore(search: Boolean = false) {
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
        listJob = viewModelScope.launch {
            runCatching {
                repository[source].getComics(feed, page)
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
        searchJob?.cancel()
        val previous = _searchState.value as? ComicListUiState.Content
        if (append && previous != null) _searchState.value = previous.copy(more = LoadMoreState.Loading)
        val source = _source.value
        searchJob = viewModelScope.launch {
            runCatching {
                repository[source].search(query, page)
            }.onSuccess { result ->
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
