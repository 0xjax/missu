package app.missu.data.model

/** 片单摘要（「我的片单」列表里的一项）。 */
data class PlaylistSummary(
    val id: String,
    val path: String,
    val name: String,
    val visibility: String? = null,
    val owner: String? = null,
    val updatedAt: String? = null,
)

/** 片单详情：标题 + 里面的影片。 */
data class PlaylistDetail(
    val name: String,
    val videos: List<VideoSummary>,
)
