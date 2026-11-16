package app.missu.ui.history

import androidx.lifecycle.ViewModel
import app.missu.data.store.HistoryEntry
import app.missu.data.store.HistoryStore
import app.missu.data.store.ListHistoryEntry
import app.missu.data.store.ListHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 本地历史，没有加载态：读 SharedPreferences 是同步的。 */
class HistoryViewModel(
    private val history: HistoryStore,
    private val listHistory: ListHistoryStore,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()
    private val _listEntries = MutableStateFlow<List<ListHistoryEntry>>(emptyList())
    val listEntries: StateFlow<List<ListHistoryEntry>> = _listEntries.asStateFlow()

    fun load() {
        _entries.value = history.recent()
        _listEntries.value = listHistory.recent()
    }

    fun clearVideos() {
        history.clear()
        _entries.value = emptyList()
    }

    fun clearLists() {
        listHistory.clear()
        _listEntries.value = emptyList()
    }
}
