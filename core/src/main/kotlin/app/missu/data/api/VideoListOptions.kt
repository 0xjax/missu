package app.missu.data.api

/**
 * 视频列表的两个口径：排序与过滤。
 *
 * 只放**值**，界面文案在 app 侧按 key 组合（同 [ActressFilterOptions]，`:core` 没有资源系统）。
 *
 * 2026-11-14 实测（用 App 自己的请求栈逐页确认）：
 * - `?sort=` 共 7 个取值；`?filters=` 共 3 个（`individual` 单人 / `multiple` 多人 /
 *   `chinese-subtitle` 中文字幕）。
 * - **不传 = 站点默认**。而且默认序**既不是** `published_at` 也**不是** `released_at`
 *   （实测三种取值的首批条目完全不同），所以「默认」必须作为一个独立状态保留，不能拿某个
 *   sort 值冒充它。
 * - **`filters` 只可靠单选**：同时传两个（重复参数或逗号）时，结果与只传 `chinese-subtitle`
 *   完全一致——既不是并集也不是交集，说明多值语义没实现好。多选等站点修好再说。
 * - 三个热门 path 与 `?sort=` 是同一面列表：`/today-hot?sort=weekly_views` 与 `/weekly-hot`
 *   的第 1、2 页条目 id 完全一致，月榜同理。所以「周期」不再需要三条路径。
 */
object VideoListOptions {

    /** 7 种排序。`null` 表示不传、用站点默认序。 */
    enum class Sort(val value: String) {
        RELEASED_AT("released_at"),
        PUBLISHED_AT("published_at"),
        SAVED("saved"),
        TODAY_VIEWS("today_views"),
        WEEKLY_VIEWS("weekly_views"),
        MONTHLY_VIEWS("monthly_views"),
        VIEWS("views"),
    }

    /**
     * 3 种过滤，互斥。`null` 表示不过滤（「全部」）。
     *
     * NOTE: 顺序就是界面上的顺序——中文字幕放最前：口径行可横向滚动，最常用的应当不用滑。
     */
    enum class Filter(val value: String) {
        CHINESE_SUBTITLE("chinese-subtitle"),
        INDIVIDUAL("individual"),
        MULTIPLE("multiple"),
    }
}

/**
 * 拼一个视频列表页的请求路径：`path?sort=…&filters=…&page=N`。
 *
 * NOTE: 抽成纯函数是为了能离线测——参数拼错是那种「页面能打开但内容不对」的安静故障。
 * NOTE: `path` 自带 query 时用 `&` 续接（详情页信息行给的路径可能带 `?`）。
 */
fun videoListPath(
    path: String,
    page: Int = 1,
    sort: VideoListOptions.Sort? = null,
    filter: VideoListOptions.Filter? = null,
): String {
    val params = buildList {
        if (sort != null) add("sort=${sort.value}")
        if (filter != null) add("filters=${filter.value}")
        if (page > 1) add("page=$page")
    }
    if (params.isEmpty()) return path
    val separator = if (path.contains('?')) '&' else '?'
    return path + separator + params.joinToString("&")
}

/**
 * 「同类实体列表」的路径前缀：第二段是实体名的那几种。
 *
 * NOTE: 只有实测过「整类同默认序」的才放进来——女优 / 类型 / 厂商各自整类一致（2026-11-15 真机
 * 取页头：`/actresses/波多野結衣`、`/actresses/大槻ひびき`、`/genres/VR`、`/genres/高清`、
 * `/makers/S1`、`/makers/SOD` 全是「发行日期」）。频道、板块、热门这些单段路径没验过一致，
 * 不进这里（各自按完整路径）。
 */
private val ENTITY_LIST_KINDS = setOf("actresses", "genres", "makers")

/**
 * 「站点默认排序」的缓存组：同一类的列表共用一个学习结果。
 *
 * 站点把当前排序直出在列表页头部，只有解析响应才知道是哪一档；而排序控件在响应之前就要画出来。
 * 同一路径的这一档稳定，**同类的实体列表之间也一致**，所以缓存按「类」而不是按完整路径——
 * 女优成千上万个，逐个学一遍等于每进一个女优页都先闪一次「默认」。
 *
 * 归一化走 [SourceConfig.contentPath]（剥掉 `/dmNNN` 与语言段），所以首页「更多」那种 `/cn/new`
 * 与 `/new` 算同一个列表。
 */
fun videoListSortGroup(path: String): String {
    val normalized = SourceConfig.contentPath(path.substringBefore('?'))
    val segments = normalized.split('/').filter { it.isNotEmpty() }
    return if (segments.size == 2 && segments[0] in ENTITY_LIST_KINDS) "/${segments[0]}/…" else normalized
}
