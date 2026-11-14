package app.missu.data.model

/**
 * 站点导航里的一组入口。
 *
 * NOTE: 这是**编辑排版**，不是层级容器——站点把「素人 / 无码影片 / 亚洲 AV」做成可折叠分组，
 * 但组里的每一项都是同一形状的列表页。我们只把它当分组标题用，不做嵌套导航。
 */
data class NavGroup(val title: String, val items: List<NavItem>)

/**
 * 导航里的一项。
 *
 * NOTE: `path` 已经剥掉站点的 `/dmNNN` 前缀与语言段（`SourceConfig.contentPath`），可以直接交给
 * 列表页按当前内容语言重建 URL。
 */
data class NavItem(val name: String, val path: String)
