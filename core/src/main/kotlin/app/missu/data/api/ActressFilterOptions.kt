package app.missu.data.api

import app.missu.data.model.FilterOption
import app.missu.data.model.FilterSection
import java.time.Year

/**
 * 女优目录的筛选与排序选项。
 *
 * NOTE: 整份清单照搬 legacy `src/constants/actress-filters.ts`（那是照着站点表单写的），
 * 参数名 `height / cup / age / debut + sort` 来自 legacy `getActresses` 的请求构造。
 * WARNING: 站点页面上没有这些筛选的普通链接（是脚本拼 query），所以不能靠抓页面推断；
 * 要改选项必须对照 legacy 或站点表单，不要自己编值。
 * NOTE: 这里只有**值**，没有界面文案——`:core` 没有资源系统，文案在 app 侧按 key 组合
 * （见 `DiscoverScreen` 的 `optionLabel`）。加语言不用动这个文件。
 */
object ActressFilterOptions {

    /** `?sort=` 的两档：站点现行页面只提供这两种排序链接。 */
    const val SORT_VIDEOS = "videos"
    const val SORT_DEBUT = "debut"

    /**
     * 女优月榜。**不是** `?sort=` 的取值：站点把它做成独立页面 `/actresses/ranking`，
     * 所以这里只当「第三档口径」用，真正的请求由 `DiscoverViewModel` 分流
     * （见 `MissavApi.getActressRanking`）。
     */
    const val SORT_RANKING = "ranking"

    val sorts: List<FilterOption> = listOf(
        FilterOption(SORT_VIDEOS),
        FilterOption(SORT_DEBUT),
        FilterOption(SORT_RANKING),
    )

    private val heights = listOf(
        "131-135", "136-140", "141-145", "146-150", "151-155", "156-160",
        "161-165", "166-170", "171-175", "176-180", "181-185", "186-190",
    ).map { FilterOption(it) }

    private val cups = ('A'..'Q').map { FilterOption(it.toString()) }

    private val ages = listOf(
        "0-20", "20-30", "30-40", "40-50", "50-60", "60-99",
    ).map { FilterOption(it) }

    /** 出道年份：站点语义是「N 年以前」，选项从当前年份倒序到 2000。 */
    private val debutYears: List<FilterOption> = run {
        val current = Year.now().value
        (current downTo 2000).map { FilterOption(it.toString()) }
    }

    val sections: List<FilterSection> = listOf(
        FilterSection("height", heights),
        FilterSection("cup", cups),
        FilterSection("age", ages),
        FilterSection("debut", debutYears),
    )
}
