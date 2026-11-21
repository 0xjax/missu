package app.missu.data.model

import app.missu.data.api.SourceConfig
import app.missu.data.api.VideoListOptions

/** 一个视频列表的口径：路径 + 排序 + 过滤；三者共同决定缓存键。 */
data class VideoListQuery(
    val path: String,
    val sort: VideoListOptions.Sort? = null,
    val filter: VideoListOptions.Filter? = null,
) {
    val cacheKey: String get() = "$path|${sort?.value ?: "-"}|${filter?.value ?: "-"}"

    fun sameSiteQuery(other: VideoListQuery): Boolean =
        SourceConfig.contentPath(path) == SourceConfig.contentPath(other.path) &&
            sort == other.sort && filter == other.filter
}

/** 一张视频卡片的摘要信息；字段与 legacy 的 VideoSummary 一一对应。 */
data class VideoSummary(
    val id: String,
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String? = null,
    /**
     * 卡片自带的静音预览片（mp4）。
     *
     * NOTE: 站点在桌面端把鼠标移入卡片时就是拿它换掉封面的，HTML 里直出 `data-src`；我们没有
     * 拼 URL 也没有额外请求。列表页模板渲染出来的卡片没有这个属性，所以允许为空。
     */
    val previewUrl: String? = null,
    val duration: String? = null,
    val badges: List<String> = emptyList(),
)

/** 首页一个板块（新作上市 / 最近更新 / 无码影片 / 随机）。 */
data class HomeSection(
    val id: String,
    val title: String,
    val videos: List<VideoSummary>,
    val morePath: String? = null,
)

/** 登录用户。源站只返回 name，邮箱由本次登录补全。 */
data class AccountUser(
    val email: String,
    val username: String? = null,
)

/** 一页视频列表；`nextPage` 为空表示没有下一页。 */
data class VideoList(
    val videos: List<VideoSummary>,
    val nextPage: Int? = null,
    /**
     * 站点自己认为这一页当前的排序（从页面头部的「排序: X」+ 下拉取值解析出来的）。
     *
     * NOTE: 站点**没有「默认」这一项**，它总把某个具体排序显示为当前项；所以「默认」不是一个
     * 独立档位，而是「没传参数时的顺序」——两者可能在个别页面上并不相同（`/new` 实测其默认序
     * 不等于任何取值），因此这个字段只用于**显示**，不让调用方据此补发 `?sort=`。
     */
    val sort: app.missu.data.api.VideoListOptions.Sort? = null,
)

/** 详情页信息行的一个值；`path` 非空表示可点击跳列表。 */
data class DetailInfoValue(val text: String, val path: String? = null)

/** 详情页信息行：标签 + 若干个值（发行日期 / 番号 / 女优…）。 */
data class DetailInfoRow(val label: String, val values: List<DetailInfoValue>)

/** 原站详情页直出的 seek 雪碧图配置。 */
data class SeekPreviewSpec(
    val frameCount: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val columns: Int,
    val rows: Int,
    val spriteUrls: List<String>,
) {
    fun frameAt(positionMs: Long, durationMs: Long): SeekPreviewFrame? {
        if (durationMs <= 0L || frameCount <= 0 || columns <= 0 || rows <= 0) return null
        val position = positionMs.coerceIn(0L, durationMs)
        val index = (position * frameCount / durationMs).toInt().coerceIn(0, frameCount - 1)
        val framesPerSprite = columns * rows
        val localIndex = index % framesPerSprite
        return SeekPreviewFrame(
            url = spriteUrls.getOrNull(index / framesPerSprite) ?: return null,
            x = (localIndex % columns) * frameWidth,
            y = (localIndex / columns) * frameHeight,
        )
    }
}

data class SeekPreviewFrame(val url: String, val x: Int, val y: Int)

/** 详情页数据。`playbackUrl` 是 HLS（m3u8），来自页面内联打包脚本。 */
data class VideoDetail(
    val id: String,
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String? = null,
    val duration: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val actors: List<String> = emptyList(),
    val playbackUrl: String? = null,
    val seekPreview: SeekPreviewSpec? = null,
    val internalId: String? = null,
    /** 收藏状态；只在拿到 [internalId] 并成功查询时才有意义。 */
    val saved: Boolean = false,
    val info: List<DetailInfoRow> = emptyList(),
)
