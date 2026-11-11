import Storage, { migrateLegacyStorage } from '@/storage/kv';

const STORAGE_KEY = 'missav:search-history:v1';
const SEARCH_HISTORY_LIMIT = 20;

export async function getSearchHistory(): Promise<string[]> {
  await migrateLegacyStorage();
  const value = await Storage.getItem(STORAGE_KEY);
  if (!value) return [];
  try {
    const items = JSON.parse(value) as unknown;
    return Array.isArray(items) ? items.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

export async function addSearchHistory(keyword: string): Promise<string[]> {
  const current = await getSearchHistory();
  const next = [keyword, ...current.filter((item) => item !== keyword)].slice(0, SEARCH_HISTORY_LIMIT);
  await Storage.setItem(STORAGE_KEY, JSON.stringify(next));
  return next;
}

export function clearSearchHistory(): Promise<void> {
  return Storage.removeItem(STORAGE_KEY);
}
