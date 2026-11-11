export type ActressSort = 'videos' | 'debut';

export const ACTRESS_SORT_OPTIONS: { label: string; value: ActressSort }[] = [
  { label: '影片', value: 'videos' },
  { label: '出道', value: 'debut' },
];

export const ACTRESS_HEIGHTS = [
  '131-135', '136-140', '141-145', '146-150', '151-155', '156-160',
  '161-165', '166-170', '171-175', '176-180', '181-185', '186-190',
];

export const ACTRESS_CUPS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q'];

export const ACTRESS_AGES = [
  { label: '< 20', value: '0-20' },
  { label: '20 - 30', value: '20-30' },
  { label: '30 - 40', value: '30-40' },
  { label: '40 - 50', value: '40-50' },
  { label: '50 - 60', value: '50-60' },
  { label: '> 60', value: '60-99' },
];

const FIRST_DEBUT_YEAR = 2000;

/** 出道年份筛选：源站语义是“N 年以前”，选项从当前年份倒序到 2000。 */
export const ACTRESS_DEBUT_YEARS = Array.from(
  { length: new Date().getFullYear() - FIRST_DEBUT_YEAR + 1 },
  (_, index) => String(new Date().getFullYear() - index),
);

export type ActressFilterKey = 'height' | 'cup' | 'age' | 'debut';

export type ActressFilters = Partial<Record<ActressFilterKey, string>>;

export const ACTRESS_FILTER_SECTIONS: {
  key: ActressFilterKey;
  title: string;
  options: { label: string; value: string }[];
}[] = [
  {
    key: 'height',
    title: '身高',
    options: ACTRESS_HEIGHTS.map((value) => ({ label: `${value.replace('-', ' - ')}cm`, value })),
  },
  {
    key: 'cup',
    title: '罩杯',
    options: ACTRESS_CUPS.map((value) => ({ label: `${value} 罩杯`, value })),
  },
  { key: 'age', title: '年龄', options: ACTRESS_AGES },
  {
    key: 'debut',
    title: '出道年份',
    options: ACTRESS_DEBUT_YEARS.map((value) => ({ label: `${value} 以前`, value })),
  },
];
