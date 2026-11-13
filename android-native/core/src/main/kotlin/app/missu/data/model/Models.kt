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
