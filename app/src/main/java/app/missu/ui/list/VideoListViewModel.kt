package app.missu.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.missu.data.api.MissavApi
import app.missu.data.api.VideoListOptions
import app.missu.data.model.VideoSummary
import app.missu.ui.common.UiMessage
import app.missu.ui.common.toUiMessage
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
        val loadingMore: Boolean = false,
    ) : VideoListUiState
}

/**
 * 一个视频列表的**口径**：路径 + 排序 + 过滤。
 *
 * NOTE: 三样合起来才是「哪一个列表」——所以它同时是缓存键（`cacheKey`）。换排序/过滤只是换一份
 * 数据，页面结构一点不动（同一屏、同一个列表、同一套卡片）。
 * NOTE: `null` 有确切含义：排序 `null` = **站点默认序**（实测不等于任何一个 sort 取值），
 * 过滤 `null` = 不过滤（「全部」）。别拿某个值去「还原默认」。
 */
data class VideoListQuery(
    val path: String,
    val sort: VideoListOptions.Sort? = null,
    val filter: VideoListOptions.Filter? = null,
) {
    val cacheKey: String get() = "$path|${sort?.value ?: "-"}|${filter?.value ?: "-"}"
}

/** 通用列表页。`path` 来自详情页信息行 / 频道 / 首页「更多」的归一化结果。 */
class VideoListViewModel(
    private val api: MissavApi,
    private val query: VideoListQuery,
) : ViewModel() {

    private val _state = MutableStateFlow<VideoListUiState>(VideoListUiState.Loading)
    val state: StateFlow<VideoListUiState> = _state.asStateFlow()

    /**
     * 站点自己声明的当前排序（页面头部那行「排序: X」）。只用于**显示**成选中项。
     *
     * NOTE: 不拿它去补发 `?sort=`——`/new` 实测「不传参数」的顺序不等于任何取值，补发会换掉数据。
     * 站点也没有「默认」这一档，所以界面用这个值代替原来那个多余的「默认」项。
     */
    private val _detectedSort = MutableStateFlow<VideoListOptions.Sort?>(null)
    val detectedSort: StateFlow<VideoListOptions.Sort?> = _detectedSort.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = VideoListUiState.Loading
            _state.value = try {
                val page = fetch(1)
                _detectedSort.value = page.sort
                if (page.videos.isEmpty()) {
                    VideoListUiState.Empty
                } else {
                    VideoListUiState.Content(page.videos, page.nextPage)
                }
            } catch (error: Exception) {
                VideoListUiState.Error(error.toUiMessage())
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? VideoListUiState.Content ?: return
        val next = current.nextPage ?: return
        if (current.loadingMore) return

        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            _state.value = try {
                val page = fetch(next)
                current.copy(
                    // CRITICAL: 按界面 key 去重（同 id 同 pageUrl），分页重叠会让 LazyVerticalGrid 抛重复 key 崩溃。
                    videos = (current.videos + page.videos).distinctBy { it.id + it.pageUrl },
                    nextPage = page.nextPage,
                    loadingMore = false,
                )
            } catch (error: Exception) {
                current.copy(loadingMore = false)
            }
        }
    }

    private suspend fun fetch(page: Int) =
        api.getVideos(query.path, page = page, sort = query.sort, filter = query.filter)
}
