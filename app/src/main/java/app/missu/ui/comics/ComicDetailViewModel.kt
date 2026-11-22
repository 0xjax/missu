package app.missu.ui.comics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.comic.ComicDetail
import app.missu.data.comic.ComicImage
import app.missu.data.comic.ComicRepository
import app.missu.data.comic.ComicSource
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ComicDetailUiState {
    data object Loading : ComicDetailUiState
    data class Error(val message: UiMessage) : ComicDetailUiState
    data class Content(val detail: ComicDetail) : ComicDetailUiState
}

class ComicDetailViewModel(
    private val repository: ComicRepository,
    private val source: ComicSource,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow<ComicDetailUiState>(ComicDetailUiState.Loading)
    val state: StateFlow<ComicDetailUiState> = _state.asStateFlow()

    fun loadIfNeeded() {
        if (_state.value !is ComicDetailUiState.Loading) return
        load()
    }

    fun load() {
        _state.value = ComicDetailUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                ComicDetailUiState.Content(repository[source].getDetail(id))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ComicDetailUiState.Error(error.toUiMessage())
            }
        }
    }
}

sealed interface ComicReaderUiState {
    data object Loading : ComicReaderUiState
    data object Empty : ComicReaderUiState
    data class Error(val message: UiMessage) : ComicReaderUiState
    data class Content(val pages: List<ComicImage>) : ComicReaderUiState
}

class ComicReaderViewModel(
    private val repository: ComicRepository,
    private val source: ComicSource,
    private val mangaId: String,
    private val chapterId: String,
) : ViewModel() {
    private val _state = MutableStateFlow<ComicReaderUiState>(ComicReaderUiState.Loading)
    val state: StateFlow<ComicReaderUiState> = _state.asStateFlow()

    fun loadIfNeeded() {
        if (_state.value !is ComicReaderUiState.Loading) return
        load()
    }

    fun load() {
        _state.value = ComicReaderUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val pages = repository[source].getChapterPages(mangaId, chapterId)
                if (pages.isEmpty()) ComicReaderUiState.Empty else ComicReaderUiState.Content(pages)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ComicReaderUiState.Error(error.toUiMessage())
            }
        }
    }
}
