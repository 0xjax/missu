package app.missu.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 「站点默认排序」缓存组的归一化（离线，不联网）。
 *
 * 归错组的后果很安静：某个女优页会先显示另一类的排序名、等响应回来才跳，也就是把刚修掉的闪烁
 * 换个方向又请回来。所以分组的边界要锁住。
 */
class VideoListSortGroupTest {

    @Test
    fun groupsEachEntityKind() {
        assertEquals("/actresses/…", videoListSortGroup("/actresses/波多野結衣"))
        assertEquals("/actresses/…", videoListSortGroup("/actresses/大槻ひびき"))
        assertEquals("/genres/…", videoListSortGroup("/genres/VR"))
        assertEquals("/genres/…", videoListSortGroup("/genres/高清"))
        assertEquals("/makers/…", videoListSortGroup("/makers/S1"))
    }

    @Test
    fun keepsDifferentKindsApart() {
        assertNotEquals(videoListSortGroup("/genres/VR"), videoListSortGroup("/makers/S1"))
        assertNotEquals(videoListSortGroup("/actresses/x"), videoListSortGroup("/genres/VR"))
    }

    @Test
    fun normalizesLanguageAndDmPrefixes() {
        assertEquals("/new", videoListSortGroup("/cn/new"))
        assertEquals("/genres/…", videoListSortGroup("/dm122/genres/單體作品"))
        assertEquals("/today-hot", videoListSortGroup("/cn/today-hot"))
    }

    @Test
    fun keepsSingleSegmentPathsSeparate() {
        // 频道 / 板块 / 热门这些没验过整类一致，各自一份，别合并。
        assertEquals("/today-hot", videoListSortGroup("/today-hot"))
        assertEquals("/siro", videoListSortGroup("/siro"))
        assertNotEquals(videoListSortGroup("/siro"), videoListSortGroup("/fc2"))
    }

    @Test
    fun leavesUnverifiedKindsUngrouped() {
        assertEquals("/series/foo", videoListSortGroup("/series/foo"))
        assertEquals("/playlists/3", videoListSortGroup("/playlists/3"))
    }

    @Test
    fun ignoresQueryString() {
        assertEquals("/genres/…", videoListSortGroup("/genres/VR?page=2"))
        assertEquals("/new", videoListSortGroup("/new?page=3"))
    }
}
