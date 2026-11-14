package app.missu.data.model

/**
 * 一个筛选选项：`value` 直接进 query。
 *
 * NOTE: 这里**不放界面文案**。`:core` 是纯 JVM，没有资源系统；文案由 app 侧按
 * [FilterSection.key] 组合（罩杯、出道年份要带单位，其余是中性格式）。
 */
data class FilterOption(val value: String)

/** 一组筛选：`key` 同时是 query 参数名，也是 app 侧取文案的键。 */
data class FilterSection(val key: String, val options: List<FilterOption>)
