package app.missu.ui.literature

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.literature.LiteraryWork
import app.missu.data.literature.LiteraryWorkSummary
import app.missu.data.literature.LiteratureFilter
import app.missu.data.literature.LiteratureRepository
import app.missu.data.store.LiteratureStore
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface LiteratureUiState {
    data object Loading : LiteratureUiState
    data class Error(val message: UiMessage) : LiteratureUiState
    data class Content(
        val works: List<LiteraryWorkSummary>,
        val nextOffset: Int?,
        val more: LoadMoreState = LoadMoreState.Idle,
    ) : LiteratureUiState
}

class LiteratureViewModel(
    private val repository: LiteratureRepository,
    private val store: LiteratureStore,
    initialFilter: LiteratureFilter = LiteratureFilter.ALL,
) : ViewModel() {
    private val _state = MutableStateFlow<LiteratureUiState>(LiteratureUiState.Loading)
    val state: StateFlow<LiteratureUiState> = _state.asStateFlow()
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow(initialFilter)
    val filter: StateFlow<LiteratureFilter> = _filter.asStateFlow()
    private var requestJob: Job? = null
    private var moreJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            store.favorites.drop(1).collect {
                if (_filter.value == LiteratureFilter.FAVORITES) refresh()
            }
        }
        viewModelScope.launch {
            repository.generation.drop(1).collect { refresh() }
        }
    }

    fun setQuery(value: String) {
        if (_query.value == value) return
        _query.value = value
        refresh()
    }

    fun setFilter(value: LiteratureFilter) {
        if (_filter.value == value) return
        _filter.value = value
        refresh()
    }

    fun refresh() {
        requestJob?.cancel()
        moreJob?.cancel()
        requestJob = viewModelScope.launch {
            if (_state.value !is LiteratureUiState.Content) _state.value = LiteratureUiState.Loading
            try {
                val page = repository.getPage(
                    filter = _filter.value,
                    query = _query.value.trim(),
                    favorites = store.favorites.value,
                    offset = 0,
                )
                _state.value = LiteratureUiState.Content(page.works, page.nextOffset)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("Literature", "List query failed", error)
                if (_state.value !is LiteratureUiState.Content) {
                    _state.value = LiteratureUiState.Error(error.toUiMessage())
                }
            }
        }
    }

    fun loadMore() {
        val content = _state.value as? LiteratureUiState.Content ?: return
        val offset = content.nextOffset ?: return
        if (content.more != LoadMoreState.Idle) return
        moreJob = viewModelScope.launch {
            _state.value = content.copy(more = LoadMoreState.Loading)
            try {
                val page = repository.getPage(
                    filter = _filter.value,
                    query = _query.value.trim(),
                    favorites = store.favorites.value,
                    offset = offset,
                )
                val current = _state.value as? LiteratureUiState.Content ?: return@launch
                _state.value = current.copy(
                    works = (current.works + page.works).distinctBy { it.id },
                    nextOffset = page.nextOffset,
                    more = LoadMoreState.Idle,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("Literature", "List pagination failed", error)
                val current = _state.value as? LiteratureUiState.Content ?: return@launch
                _state.value = current.copy(more = LoadMoreState.Failed)
            }
        }
    }
}

sealed interface LiteratureDetailUiState {
    data object Loading : LiteratureDetailUiState
    data class Error(val message: UiMessage) : LiteratureDetailUiState
    data class Content(val work: LiteraryWork) : LiteratureDetailUiState
}

class LiteratureDetailViewModel(
    private val repository: LiteratureRepository,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow<LiteratureDetailUiState>(LiteratureDetailUiState.Loading)
    val state: StateFlow<LiteratureDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.generation.drop(1).collect { load() }
        }
    }

    fun loadIfNeeded() {
        if (_state.value is LiteratureDetailUiState.Content) return
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = LiteratureDetailUiState.Loading
            try {
                val work = repository.findById(id) ?: throw NoSuchElementException(id)
                _state.value = LiteratureDetailUiState.Content(work)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("Literature", "Detail query failed: $id", error)
                _state.value = LiteratureDetailUiState.Error(error.toUiMessage())
            }
        }
    }
}
