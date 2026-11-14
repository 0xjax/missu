package app.missu.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 列表请求路径的拼接（离线，不联网）。
 *
 * 这类错误很难在真机上看出来：路径拼错时页面照样打开，只是内容不对/回到默认序。
 */
class VideoListPathTest {

    @Test
    fun keepsPathWhenNoOptions() {
        assertEquals("/new", videoListPath("/new"))
        assertEquals("/genres/VR", videoListPath("/genres/VR", page = 1))
    }

    @Test
    fun buildsSortFilterAndPage() {
        assertEquals(
            "/new?sort=weekly_views&filters=chinese-subtitle&page=3",
            videoListPath(
                "/new",
                page = 3,
                sort = VideoListOptions.Sort.WEEKLY_VIEWS,
                filter = VideoListOptions.Filter.CHINESE_SUBTITLE,
            ),
        )
    }

    @Test
    fun omitsNullOptionsAndFirstPage() {
        assertEquals("/today-hot?sort=today_views", videoListPath("/today-hot", sort = VideoListOptions.Sort.TODAY_VIEWS))
        assertEquals("/today-hot", videoListPath("/today-hot", page = 1, sort = null, filter = null))
    }

    @Test
    fun appendsToExistingQuery() {
        assertEquals("/actresses/foo?x=1&page=2", videoListPath("/actresses/foo?x=1", page = 2))
        assertEquals(
            "/actresses/foo?x=1&sort=saved",
            videoListPath("/actresses/foo?x=1", sort = VideoListOptions.Sort.SAVED),
        )
    }

    @Test
    fun coversEveryDeclaredValue() {
        // 站点给的 7 个 sort / 3 个 filter 都必须能拼出来（防止枚举值与文档走偏）。
        assertEquals(7, VideoListOptions.Sort.entries.size)
        assertEquals(3, VideoListOptions.Filter.entries.size)
        VideoListOptions.Sort.entries.forEach { sort ->
            assertEquals("/x?sort=${sort.value}", videoListPath("/x", sort = sort))
        }
        VideoListOptions.Filter.entries.forEach { filter ->
            assertEquals("/x?filters=${filter.value}", videoListPath("/x", filter = filter))
        }
    }
}
