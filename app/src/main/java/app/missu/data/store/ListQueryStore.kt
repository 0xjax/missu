package app.missu.data.store

import android.content.Context
import androidx.core.content.edit
import app.missu.data.api.VideoListOptions
import app.missu.data.api.videoListSortGroup
import app.missu.data.model.VideoListQuery

/** 同类视频列表共享用户口径；与 [ListSortStore] 的站点默认序学习缓存互不相干。 */
class ListQueryStore(context: Context) {

    private val prefs = context.getSharedPreferences("missu:list-query", Context.MODE_PRIVATE)

    fun get(path: String): VideoListQuery {
        val key = key(path)
        return VideoListQuery(
            path = path,
            sort = VideoListOptions.Sort.fromKey(prefs.getString("$key|sort", null)),
            filter = VideoListOptions.Filter.fromKey(prefs.getString("$key|filter", null)),
        )
    }

    fun put(query: VideoListQuery) {
        val key = key(query.path)
        prefs.edit {
            putString("$key|sort", query.sort?.name)
            putString("$key|filter", query.filter?.name)
        }
    }

    private fun key(path: String) = videoListSortGroup(path)
}
