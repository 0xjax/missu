package app.missu.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.model.VideoListQuery
import app.missu.data.model.VideoSummary
import app.missu.data.store.ListSortStore
import app.missu.ui.common.LoadMoreState
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface VideoListUiState {
    data object Loading : VideoListUiState
    data object Empty : VideoListUiState
    data class Error(val message: UiMessage) : VideoListUiState
    data class Content(
        val videos: List<VideoSummary>,
        val nextPage: Int?,
        val more: LoadMoreState = LoadMoreState.Idle,
    ) : VideoListUiState
}

/** 通用列表页。`path` 来自详情页信息行 / 频道 / 首页「更多」的归一化结果。 */
class VideoListViewModel(
    private val api: MissavApi,
    private val query: VideoListQuery,
    private val sortStore: ListSortStore,
    initialContent: VideoListUiState.Content? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<VideoListUiState>(initialContent ?: VideoListUiState.Loading)
    val state: StateFlow<VideoListUiState> = _state.asStateFlow()

    /**
     * 站点自己声明的当前排序（页面头部那行「排序: X」）。只用于**显示**成选中项。
     *
     * NOTE: 不拿它去补发 `?sort=`——`/new` 实测「不传参数」的顺序不等于任何取值，补发会换掉数据。
     * 站点也没有「默认」这一档，所以界面用这个值代替原来那个多余的「默认」项。
     * NOTE: 初值取 [ListSortStore] 里学到的该类列表的默认序，**不能留到响应回来才填**——排序控件
     * 在响应之前就画出来了，留空会先写「默认」再跳成实际项（每进一次同类列表都闪一下）。
     */
    private val _detectedSort = MutableStateFlow(sortStore.get(query.path))
    val detectedSort: StateFlow<VideoListOptions.Sort?> = _detectedSort.asStateFlow()
    private var requestJob: Job? = null

    fun load() {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = VideoListUiState.Loading
            _state.value = try {
                val page = fetch(1)
                _detectedSort.value = page.sort
                // 只学「不传 ?sort=」的那一面：带排序的响应声明的是我们点的那一档，不是该页默认序。
                // 解析失败（page.sort == null）不写：缓存组是同类列表共享的，清掉会让整类退回「默认」。
                if (query.sort == null) page.sort?.let { sortStore.put(query.path, it) }
                if (page.videos.isEmpty()) {
                    VideoListUiState.Empty
                } else {
                    VideoListUiState.Content(page.videos, page.nextPage)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                VideoListUiState.Error(error.toUiMessage())
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? VideoListUiState.Content ?: return
        val next = current.nextPage ?: return
        // NOTE: 只挡 Loading —— Failed 必须能被脚注上的「重试」再调一次；自动加载那边
        // 由界面的 enabled 挡住，所以失败之后不会靠滚动偷偷重试。
        if (current.more == LoadMoreState.Loading) return

        requestJob?.cancel()
        _state.value = current.copy(more = LoadMoreState.Loading)
        requestJob = viewModelScope.launch {
            _state.value = try {
                val page = fetch(next)
                current.copy(
                    // CRITICAL: 按界面 key 去重（同 id 同 pageUrl），分页重叠会让 LazyVerticalGrid 抛重复 key 崩溃。
                    videos = (current.videos + page.videos).distinctBy { it.id + it.pageUrl },
                    nextPage = page.nextPage,
                    more = LoadMoreState.Idle,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // WARNING: 不能静默复位成 Idle——那一页没取到，界面必须留一个重试入口，
                // 否则列表就在底部无声卡住，用户分不清「已经到底」和「坏了」。
                current.copy(more = LoadMoreState.Failed)
            }
        }
    }

    private suspend fun fetch(page: Int) =
        api.getVideos(query.path, page = page, sort = query.sort, filter = query.filter)
}
