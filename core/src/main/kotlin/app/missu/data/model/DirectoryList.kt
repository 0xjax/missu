package app.missu.data.model

/** 目录条目（女优 / 类型 / 厂商）。`id` 与 `path` 现在是同一个归一化路径。 */
data class DirectoryItem(
    val id: String,
    val path: String,
    val name: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
)

/** 一页目录；`nextPage` 为空表示没有下一页。 */
data class DirectoryList(
    val items: List<DirectoryItem>,
    val nextPage: Int? = null,
)
