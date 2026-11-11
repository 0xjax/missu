import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  getDirectory,
  getVideos,
  isAbortError,
  type DirectoryItem,
  type VideoFilter,
  type VideoSort,
  type VideoSummary,
} from '@/api/missav';
import { ChoiceMenuBar } from '@/components/choice-menu-bar';
import { ChannelGroups } from '@/components/channel-groups';
import { DirectoryCard } from '@/components/directory-card';
import { DiscoverTabs } from '@/components/discover-tabs';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing, TabBarInset } from '@/constants/theme';
import {
  NO_FILTER,
  NO_SORT,
  VIDEO_FILTER_OPTIONS,
  VIDEO_SORT_OPTIONS,
} from '@/constants/video-options';
import { useTheme } from '@/hooks/use-theme';

type Tab = 'hot' | 'genres' | 'actresses' | 'makers' | 'ranking';
type Period = 'today' | 'weekly' | 'monthly';
type Entry = VideoSummary | DirectoryItem;
type DiscoverParams = { tab?: Tab; period?: Period; at?: string };

const tabs: { id: Tab; label: string }[] = [
  { id: 'hot', label: '热门' },
  { id: 'genres', label: '分类' },
  { id: 'actresses', label: '女优' },
  { id: 'makers', label: '厂商' },
  { id: 'ranking', label: '女优榜' },
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
  const params = useLocalSearchParams<DiscoverParams>();
  const isHotShortcut = params.tab === 'hot' && periods.some((item) => item.id === params.period);
  const [tab, setTab] = useState<Tab>('hot');
  const [period, setPeriod] = useState<Period>(isHotShortcut ? params.period as Period : 'today');
  const [filter, setFilter] = useState<VideoFilter>();
  const [sort, setSort] = useState<VideoSort>();
  const [reloadKey, setReloadKey] = useState(0);
  const [items, setItems] = useState<Entry[]>([]);
  const [nextPage, setNextPage] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();
  const handledAt = useRef(params.at);

  const load = useCallback(async (page: number, signal?: AbortSignal) => {
    if (tab === 'hot') {
      const path = periods.find((item) => item.id === period)!.path;
      const result = await getVideos(path, { page, filters: filter, sort }, signal);
      return { items: result.videos as Entry[], nextPage: result.nextPage };
    }
    const result = await getDirectory(directoryPaths[tab], page, signal);
    return { items: result.items as Entry[], nextPage: result.nextPage };
  }, [filter, period, sort, tab]);

  const beginRequest = useCallback(() => {
    setLoading(true);
    setItems([]);
    setNextPage(null);
    setError(undefined);
  }, []);

  // NOTE: 首页“今日热门/更多”用 tab+period+at 跳转过来，at 变化时切回热门并重新加载。
  useEffect(() => {
    if (params.tab !== 'hot' || !periods.some((item) => item.id === params.period)) return;
    if (handledAt.current === params.at) return;
    handledAt.current = params.at;
    beginRequest();
    setTab('hot');
    setPeriod(params.period as Period);
    setReloadKey((value) => value + 1);
  }, [beginRequest, params.at, params.period, params.tab]);

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
  }, [load, reloadKey]);

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

  const periodLabel = periods.find((item) => item.id === period)?.label ?? '今日';
  const filterLabel = filter ? VIDEO_FILTER_OPTIONS.find((item) => item.value === filter)?.label : undefined;
  const sortLabel = sort ? VIDEO_SORT_OPTIONS.find((item) => item.value === sort)?.label : undefined;

  const hotChoices = [
    {
      key: 'period',
      text: `${periodLabel}热门`,
      value: period,
      options: periods.map((item) => ({ label: `${item.label}热门`, value: item.id })),
      onChange: (value: string) => {
        beginRequest();
        setPeriod(value as Period);
      },
    },
    {
      key: 'filter',
      text: filterLabel ? `筛选 · ${filterLabel}` : '筛选',
      value: filter ?? NO_FILTER,
      options: VIDEO_FILTER_OPTIONS.map((item) => ({ label: item.label, value: item.value ?? NO_FILTER })),
      onChange: (value: string) => {
        beginRequest();
        setFilter(value === NO_FILTER ? undefined : value as VideoFilter);
      },
    },
    {
      key: 'sort',
      text: sortLabel ? `排序 · ${sortLabel}` : '排序',
      value: sort ?? NO_SORT,
      options: VIDEO_SORT_OPTIONS.map((item) => ({ label: item.label, value: item.value ?? NO_SORT })),
      onChange: (value: string) => {
        beginRequest();
        setSort(value === NO_SORT ? undefined : value as VideoSort);
      },
    },
  ];

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
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
            <DiscoverTabs
              items={tabs}
              onChange={(next) => {
                beginRequest();
                setTab(next);
              }}
              value={tab}
            />
            {tab === 'hot' && <ChoiceMenuBar choices={hotChoices} />}
            {tab === 'makers' && <ChannelGroups />}
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
    paddingBottom: Spacing.xxxl + TabBarInset,
  },
  row: {
    width: '100%',
    gap: Spacing.md,
    marginBottom: Spacing.xl,
  },
  header: {
    gap: Spacing.md,
    marginBottom: Spacing.xl,
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
