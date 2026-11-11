import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from 'react-native-paper';

import {
  getDirectory,
  getVideos,
  isAbortError,
  type DirectoryItem,
  type VideoSummary,
} from '@/api/missav';
import { AppNav } from '@/components/app-nav';
import { DirectoryCard } from '@/components/directory-card';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type Tab = 'hot' | 'genres' | 'actresses' | 'makers' | 'ranking';
type Period = 'today' | 'weekly' | 'monthly';
type Entry = VideoSummary | DirectoryItem;

const tabs: { id: Tab; label: string }[] = [
  { id: 'hot', label: '热门' },
  { id: 'genres', label: '分类' },
  { id: 'actresses', label: '女优' },
  { id: 'makers', label: '厂商' },
  { id: 'ranking', label: '女优排行' },
];
const periods: { id: Period; label: string; path: string }[] = [
  { id: 'today', label: '今日', path: '/today-hot' },
  { id: 'weekly', label: '本周', path: '/weekly-hot' },
  { id: 'monthly', label: '本月', path: '/monthly-hot' },
];
const directoryPaths: Record<Exclude<Tab, 'hot'>, string> = {
  genres: '/genres',
  actresses: '/actresses',
  makers: '/makers',
  ranking: '/actresses/ranking',
};

export default function DiscoverScreen() {
  const theme = useTheme();
  const [tab, setTab] = useState<Tab>('hot');
  const [period, setPeriod] = useState<Period>('today');
  const [items, setItems] = useState<Entry[]>([]);
  const [nextPage, setNextPage] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();

  const load = useCallback(async (page: number, signal?: AbortSignal) => {
    if (tab === 'hot') {
      const path = periods.find((item) => item.id === period)!.path;
      const result = await getVideos(path, { page }, signal);
      return { items: result.videos as Entry[], nextPage: result.nextPage };
    }
    const result = await getDirectory(directoryPaths[tab], page, signal);
    return { items: result.items as Entry[], nextPage: result.nextPage };
  }, [period, tab]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    load(1, controller.signal).then((result) => {
      if (!active) return;
      setItems(result.items);
      setNextPage(result.nextPage);
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      setError(reason instanceof Error ? reason.message : '加载失败');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [load]);

  const beginRequest = () => {
    setLoading(true);
    setItems([]);
    setNextPage(null);
    setError(undefined);
  };

  const refresh = async () => {
    if (items.length === 0) setLoading(true);
    setRefreshing(true);
    setError(undefined);
    try {
      const result = await load(1);
      setItems(result.items);
      setNextPage(result.nextPage);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载失败');
    } finally {
      setRefreshing(false);
    }
  };

  const loadMore = async () => {
    if (!nextPage || loadingMore || loading || refreshing) return;
    setLoadingMore(true);
    try {
      const result = await load(nextPage);
      setItems((current) => {
        const ids = new Set(current.map((item) => item.id));
        return [...current, ...result.items.filter((item) => !ids.has(item.id))];
      });
      setNextPage(result.nextPage);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载失败');
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <SafeAreaView edges={['top', 'bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <FlatList
        contentContainerStyle={styles.content}
        columnWrapperStyle={styles.row}
        data={items}
        keyExtractor={(item) => item.id}
        ListEmptyComponent={(
          <View style={styles.empty}>
            {loading ? <ActivityIndicator color={theme.text} /> : (
              <>
                <ThemedText themeColor="textSecondary">{error || '暂无内容'}</ThemedText>
                {error && (
                  <Pressable accessibilityRole="button" onPress={refresh} style={[styles.retry, { backgroundColor: theme.backgroundElement }]}>
                    <ThemedText type="smallBold">重试</ThemedText>
                  </Pressable>
                )}
              </>
            )}
          </View>
        )}
        ListFooterComponent={loadingMore ? <ActivityIndicator color={theme.text} /> : undefined}
        ListHeaderComponent={(
          <View style={styles.header}>
            <ThemedText type="subtitle">发现</ThemedText>
            <AppNav current="discover" />
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.choices}>
              {tabs.map((item) => (
                <Chip key={item.id} compact selected={tab === item.id} onPress={() => {
                  beginRequest();
                  setTab(item.id);
                }} style={[styles.choice, { backgroundColor: tab === item.id ? theme.accent : theme.backgroundElement }]} textStyle={{ color: tab === item.id ? theme.onAccent : theme.text }}>
                  {item.label}
                </Chip>
              ))}
            </ScrollView>
            {tab === 'hot' && (
              <View style={styles.periods}>
                {periods.map((item) => (
                  <Chip key={item.id} compact selected={period === item.id} onPress={() => {
                    beginRequest();
                    setPeriod(item.id);
                  }} style={[styles.period, { backgroundColor: period === item.id ? theme.backgroundSelected : theme.backgroundElement }]}>
                    {item.label}热门
                  </Chip>
                ))}
              </View>
            )}
          </View>
        )}
        numColumns={2}
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
        onRefresh={refresh}
        refreshing={refreshing}
        renderItem={({ item }) => 'pageUrl' in item ? <VideoCard video={item} /> : <DirectoryCard item={item} />}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    padding: Spacing.lg,
    paddingBottom: Spacing.xxxl,
  },
  row: {
    width: '100%',
    gap: Spacing.md,
    marginBottom: Spacing.xl,
  },
  header: {
    gap: Spacing.lg,
    marginBottom: Spacing.xl,
  },
  choices: {
    gap: Spacing.sm,
  },
  choice: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.lg,
    paddingVertical: Spacing.sm,
  },
  periods: {
    flexDirection: 'row',
    gap: Spacing.sm,
  },
  period: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
  },
  empty: {
    alignItems: 'center',
    gap: Spacing.lg,
    paddingVertical: Spacing.giant,
  },
  retry: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
});
