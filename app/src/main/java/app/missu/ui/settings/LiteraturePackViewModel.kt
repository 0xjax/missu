package app.missu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.literature.LiteraturePackManager
import app.missu.data.literature.LiteraturePackStatus
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiteraturePackUiState(
    val fullActive: Boolean,
    val workCount: Int,
    val fullWorkCount: Int,
    val fullBytes: Long,
    val downloading: Boolean,
    val downloadedBytes: Long,
    val error: UiMessage?,
)

class LiteraturePackViewModel(private val manager: LiteraturePackManager) : ViewModel() {
    val state: StateFlow<LiteraturePackUiState> = manager.state.map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, manager.state.value.toUiState())
    private var downloadJob: Job? = null

    fun download() {
        if (state.value.downloading) return
        downloadJob = viewModelScope.launch { manager.downloadFull() }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    fun useCore() {
        viewModelScope.launch { manager.useCore() }
    }

    private fun LiteraturePackStatus.toUiState() = LiteraturePackUiState(
        fullActive = fullActive,
        workCount = workCount,
        fullWorkCount = fullWorkCount,
        fullBytes = fullBytes,
        downloading = downloading,
        downloadedBytes = downloadedBytes,
        error = error?.toUiMessage(),
    )
}
