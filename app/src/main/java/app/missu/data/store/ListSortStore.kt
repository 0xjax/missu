package app.missu.data.store

import android.content.Context
import app.missu.data.api.VideoListOptions

/**
 * 「这个列表在站点上默认用哪一档排序」的**学习缓存**。
 *
 * 站点把当前排序直出在列表页头部（`排序: 发行日期`），所以只有拿到响应才知道是哪一档；而排序控件
 * 在响应之前就要画出来——首帧只能先写「默认」、等数据回来再跳成实际项。同一路径的默认序是稳定的
 * （实测 `/siro` 恒为「发行日期」、`/today-hot` 恒为「今日浏览数」），所以把第一次学到的值按路径
 * 存下来：下次进入、以及换过滤时新开的那份口径，都直接用缓存渲染，只有真正还没学过的路径才回落到
 * 「默认」。
 *
 * NOTE: 键是**路径**而不是完整口径——换过滤不改变站点默认排序，同一个列表的几个过滤共用一项。
 * NOTE: 只在**不传 `?sort=`** 的请求里写入：带排序的响应头部声明的是那一档，不是该页的默认序。
 * NOTE: 不需要「失效」逻辑。缓存只负责首帧，响应一到就以站点声明为准并覆盖；站点哪天真改了默认序，
 * 最坏也只是那一次首帧显示旧值。
 */
class ListSortStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:list-sort", Context.MODE_PRIVATE)

    fun get(path: String): VideoListOptions.Sort? =
        VideoListOptions.Sort.entries.firstOrNull { it.name == prefs.getString(path, null) }

    /** 记住该路径的默认序；`null`（响应里解析不出来）就删掉，别让旧值冒充当前事实。 */
    fun put(path: String, sort: VideoListOptions.Sort?) {
        val edit = prefs.edit()
        if (sort == null) edit.remove(path) else edit.putString(path, sort.name)
        edit.apply()
    }
}
