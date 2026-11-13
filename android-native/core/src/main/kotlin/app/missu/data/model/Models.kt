package app.missu.data.model

/** 一张视频卡片的摘要信息；字段与 legacy 的 VideoSummary 一一对应。 */
data class VideoSummary(
    val id: String,
    val title: String,
    val pageUrl: String,
    val thumbnailUrl: String? = null,
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

/** 详情页信息行的一个值；`path` 非空表示可点击跳列表。 */
data class DetailInfoValue(val text: String, val path: String? = null)

/** 详情页信息行：标签 + 若干个值（发行日期 / 番号 / 女优…）。 */
data class DetailInfoRow(val label: String, val values: List<DetailInfoValue>)

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
    val internalId: String? = null,
    val info: List<DetailInfoRow> = emptyList(),
)
