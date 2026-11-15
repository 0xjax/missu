package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import app.missu.data.api.VideoListOptions
import app.missu.data.api.videoListSortGroup

/**
 * 「这一类列表在站点上默认用哪一档排序」的**学习缓存**。
 *
 * 站点把当前排序直出在列表页头部（`排序: 发行日期`），所以只有拿到响应才知道是哪一档；而排序控件
 * 在响应之前就要画出来——首帧只能先写「默认」、等数据回来再跳成实际项。同一路径的默认序是稳定的
 * （实测 `/siro` 恒为「发行日期」、`/today-hot` 恒为「今日浏览数」），**同类的实体列表之间也一致**
 * （女优 / 类型 / 厂商整类同值），所以把第一次学到的值按「类」存下来：下次进入、以及换过滤时新开的
 * 那份口径，都直接用缓存渲染，只有真正还没学过的类才回落到「默认」。
 *
 * NOTE: 键由 [videoListSortGroup] 给——`/actresses/{名}` 这类合成一个组，女优成千上万个不必逐个学；
 * 频道 / 板块 / 热门这些单段路径按完整路径各自一份。
 * NOTE: 只在**不传 `?sort=`** 的请求里写入：带排序的响应头部声明的是那一档，不是该页的默认序。
 * NOTE: 只写不删。解析失败（站点改结构）时保持原值——组是共享的，一次解析失败若清掉整组，会让同类
 * 列表全部退回「默认」。缓存只负责首帧，响应一到仍以站点声明为准。
 */
class ListSortStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:list-sort", Context.MODE_PRIVATE)

    fun get(path: String): VideoListOptions.Sort? =
        VideoListOptions.Sort.entries.firstOrNull { it.name == prefs.getString(videoListSortGroup(path), null) }

    fun put(path: String, sort: VideoListOptions.Sort) {
        prefs.edit { putString(videoListSortGroup(path), sort.name) }
    }
}
