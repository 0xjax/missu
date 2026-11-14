package app.missu.data.model

/** 详情页片单面板里的一项：`key` 是片单标识，`added` 表示该影片是否已在其中。 */
data class VideoPlaylist(
    val key: String,
    val name: String,
    val added: Boolean,
)
