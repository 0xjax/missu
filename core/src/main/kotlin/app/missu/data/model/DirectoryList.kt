package app.missu.data.model

/**
 * 目录条目（女优 / 类型 / 厂商）。`id` 与 `path` 现在是同一个归一化路径。
 *
 * NOTE: 站点把「条数」和「出道」写在同一段文本里（`5714 条影片 2008 出道`），解析时就拆成两段：
 * UI 要按两行排，而且**出道信息不是每条都有**（类型/厂商只有条数），所以两者都可空。
 */
data class DirectoryItem(
    val id: String,
    val path: String,
    val name: String,
    /** 形如 `5714 条影片`（随内容语言变化）。 */
    val countText: String? = null,
    /** 形如 `2008 出道`；多数类型/厂商没有这段。 */
    val debutText: String? = null,
    val imageUrl: String? = null,
)

/** 一页目录；`nextPage` 为空表示没有下一页。 */
data class DirectoryList(
    val items: List<DirectoryItem>,
    val nextPage: Int? = null,
)
