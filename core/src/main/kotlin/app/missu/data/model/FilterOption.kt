package app.missu.data.model

/** 一个筛选选项：`value` 直接进 query，`label` 是界面文案。 */
data class FilterOption(val value: String, val label: String)

/** 一组筛选（身高 / 罩杯 / 年龄 / 出道年份）。 */
data class FilterSection(val key: String, val title: String, val options: List<FilterOption>)
