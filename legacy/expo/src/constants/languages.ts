import type { ContentLanguage } from '@/api/missav';

// NOTE: 与源站语言菜单一致；tw 没有路径前缀，是站点默认的繁體中文。
export const CONTENT_LANGUAGES: { value: ContentLanguage; label: string }[] = [
  { value: 'tw', label: '繁體中文' },
  { value: 'cn', label: '简体中文' },
  { value: 'en', label: 'English' },
  { value: 'ja', label: '日本語' },
  { value: 'ko', label: '한국어' },
  { value: 'ms', label: 'Melayu' },
  { value: 'th', label: 'ไทย' },
];

export function languageLabel(language: ContentLanguage): string {
  return CONTENT_LANGUAGES.find((item) => item.value === language)?.label ?? language;
}
