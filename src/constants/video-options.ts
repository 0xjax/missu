import type { VideoFilter, VideoSort } from '@/api/missav';

export type FilterOption = { label: string; value?: VideoFilter };
export type SortOption = { label: string; value?: VideoSort };

// NOTE: 原生 Picker 的 selectedValue 必须是已有选项，用哨兵值表示“不筛选/默认排序”。
export const NO_FILTER = 'all';
export const NO_SORT = 'default';

export const VIDEO_FILTER_OPTIONS: FilterOption[] = [
  { label: '全部' },
  { label: '单人作品', value: 'individual' },
  { label: '多人作品', value: 'multiple' },
  { label: '中文字幕', value: 'chinese-subtitle' },
];

// NOTE: 源站搜索结果页的筛选项与普通列表不同，没有“多人作品”，多了内容类型。
export const SEARCH_FILTER_OPTIONS: FilterOption[] = [
  { label: '全部' },
  { label: '单人作品', value: 'individual' },
  { label: '日本AV', value: 'jav' },
  { label: '亚洲AV', value: 'asiaav' },
  { label: '无码流出', value: 'uncensored-leak' },
  { label: '无码影片', value: 'uncensored' },
  { label: '中文字幕', value: 'chinese-subtitle' },
];

export const VIDEO_SORT_OPTIONS: SortOption[] = [
  { label: '默认' },
  { label: '发行日期', value: 'released_at' },
  { label: '最近更新', value: 'published_at' },
  { label: '收藏数', value: 'saved' },
  { label: '今日浏览', value: 'today_views' },
  { label: '本周浏览', value: 'weekly_views' },
  { label: '本月浏览', value: 'monthly_views' },
  { label: '总浏览量', value: 'views' },
];
