import AsyncStorage from '@react-native-async-storage/async-storage';

import type { VideoSummary } from '@/api/missav';

const STORAGE_KEY = 'missav:history:v1';
const HISTORY_LIMIT = 100;

export type HistoryEntry = VideoSummary & {
  viewedAt: string;
};

export async function getHistory(): Promise<HistoryEntry[]> {
  const value = await AsyncStorage.getItem(STORAGE_KEY);
  if (!value) return [];
  try {
    return JSON.parse(value) as HistoryEntry[];
  } catch {
    return [];
  }
}

export async function addToHistory(video: VideoSummary): Promise<void> {
  const current = await getHistory();
  const entry: HistoryEntry = {
    id: video.id,
    title: video.title,
    pageUrl: video.pageUrl,
    thumbnailUrl: video.thumbnailUrl,
    duration: video.duration,
    viewedAt: new Date().toISOString(),
  };
  const next = [entry, ...current.filter((item) => item.id !== video.id)].slice(0, HISTORY_LIMIT);
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(next));
}

export function clearHistory(): Promise<void> {
  return AsyncStorage.removeItem(STORAGE_KEY);
}
