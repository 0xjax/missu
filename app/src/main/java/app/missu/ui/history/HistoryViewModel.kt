package app.missu.ui.history

import androidx.lifecycle.ViewModel
import app.missu.data.store.HistoryEntry
import app.missu.data.store.HistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 本地历史，没有加载态：读 SharedPreferences 是同步的。 */
class HistoryViewModel(private val history: HistoryStore) : ViewModel() {

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun load() {
        _entries.value = history.recent()
    }

    fun clear() {
        history.clear()
        _entries.value = emptyList()
    }
}
