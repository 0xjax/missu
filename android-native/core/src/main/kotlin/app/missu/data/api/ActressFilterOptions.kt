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
 */
object ActressFilterOptions {

    val sorts: List<FilterOption> = listOf(
        FilterOption("videos", "影片"),
        FilterOption("debut", "出道"),
    )

    private val heights = listOf(
        "131-135", "136-140", "141-145", "146-150", "151-155", "156-160",
        "161-165", "166-170", "171-175", "176-180", "181-185", "186-190",
    ).map { FilterOption(it, it.replace("-", " - ") + "cm") }

    private val cups = ('A'..'Q').map { FilterOption(it.toString(), "$it 罩杯") }

    private val ages = listOf(
        FilterOption("0-20", "< 20"),
        FilterOption("20-30", "20 - 30"),
        FilterOption("30-40", "30 - 40"),
        FilterOption("40-50", "40 - 50"),
        FilterOption("50-60", "50 - 60"),
        FilterOption("60-99", "> 60"),
    )

    /** 出道年份：站点语义是「N 年以前」，选项从当前年份倒序到 2000。 */
    private val debutYears: List<FilterOption> = run {
        val current = Year.now().value
        (current downTo 2000).map { FilterOption(it.toString(), "$it 以前") }
    }

    val sections: List<FilterSection> = listOf(
        FilterSection("height", "身高", heights),
        FilterSection("cup", "罩杯", cups),
        FilterSection("age", "年龄", ages),
        FilterSection("debut", "出道年份", debutYears),
    )
}
