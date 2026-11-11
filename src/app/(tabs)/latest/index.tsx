import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  getVideos,
  isAbortError,
  searchVideos,
  type VideoFilter,
  type VideoList,
  type VideoSort,
  type VideoSummary,
} from '@/api/missav';
import { ChoiceMenuBar } from '@/components/choice-menu-bar';
import { SearchBox } from '@/components/search-box';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing, TabBarInset } from '@/constants/theme';
import {
  NO_FILTER,
  NO_SORT,
  SEARCH_FILTER_OPTIONS,
  VIDEO_FILTER_OPTIONS,
  VIDEO_SORT_OPTIONS,
} from '@/constants/video-options';
import { useTheme } from '@/hooks/use-theme';

const sources = [
  { label: '最近更新', path: '/new' },
  { label: '新作上市', path: '/release' },
  { label: '无码流出', path: '/uncensored-leak' },
];

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '加载失败，请稍后重试';

type LatestParams = { query?: string; source?: string; title?: string };

export default function LatestScreen() {
  const params = useLocalSearchParams<LatestParams>();
  const key = `${params.query ?? ''}|${params.source ?? ''}|${params.title ?? ''}`;

  return <LatestView key={key} params={params} />;
}

function LatestView({ params }: { params: LatestParams }) {
  const theme = useTheme();
  const initialKeyword = params.query ?? '';
  const [input, setInput] = useState(initialKeyword);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [source, setSource] = useState(params.source ?? '/new');
  const [filter, setFilter] = useState<VideoFilter>();
  const [sort, setSort] = useState<VideoSort>();
  const [videos, setVideos] = useState<VideoSummary[]>([]);
  const [nextPage, setNextPage] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();

  // NOTE: 首页“更多”还会带 /fc2 等频道，未在常用频道内时补一个当前项，避免没有任何选中态。
  const sourceOptions = sources.some((item) => item.path === source)
    ? sources
    : [...sources, { label: params.title || source, path: source }];
  const filterOptions = (keyword ? SEARCH_FILTER_OPTIONS : VIDEO_FILTER_OPTIONS).map((item) => ({
    label: item.label,
    value: item.value ?? NO_FILTER,
  }));
  const sortOptions = VIDEO_SORT_OPTIONS.map((item) => ({
    label: item.label,
    value: item.value ?? NO_SORT,
  }));
  const filterLabel = filter ? filterOptions.find((item) => item.value === filter)?.label : undefined;
  const sortLabel = sort ? sortOptions.find((item) => item.value === sort)?.label : undefined;

  const beginRequest = useCallback(() => {
    setLoading(true);
    setVideos([]);
    setError(undefined);
    setNextPage(null);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    const request = keyword
      ? searchVideos(keyword, { filters: filter, sort }, controller.signal)
      : getVideos(source, { filters: filter, sort }, controller.signal);
    request.then((result) => {
      if (!active) return;
      setVideos(result.videos);
      setNextPage(result.nextPage);
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      setError(errorMessage(reason));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [filter, keyword, sort, source]);

  const refresh = async () => {
    setRefreshing(true);
    setError(undefined);
    try {
      const result = keyword
        ? await searchVideos(keyword, { filters: filter, sort })
        : await getVideos(source, { filters: filter, sort });
      setVideos(result.videos);
      setNextPage(result.nextPage);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setRefreshing(false);
    }
  };

  const loadMore = async () => {
    if (!nextPage || loading || loadingMore || refreshing) return;
    setLoadingMore(true);
    setError(undefined);
    try {
      const options = { page: nextPage, filters: filter, sort };
      const result: VideoList = keyword
        ? await searchVideos(keyword, options)
        : await getVideos(source, options);
      setVideos((current) => {
        const ids = new Set(current.map((video) => video.id));
        return [...current, ...result.videos.filter((video) => !ids.has(video.id))];
      });
      setNextPage(result.nextPage);
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setLoadingMore(false);
    }
  };

  const filterChoices = [
    {
      key: 'filter',
      text: filterLabel ? `筛选 · ${filterLabel}` : '筛选',
      value: filter ?? NO_FILTER,
      options: filterOptions,
      onChange: (value: string) => {
        beginRequest();
        setFilter(value === NO_FILTER ? undefined : value as VideoFilter);
      },
    },
    {
      key: 'sort',
      text: sortLabel ? `排序 · ${sortLabel}` : '排序',
      value: sort ?? NO_SORT,
      options: sortOptions,
      onChange: (value: string) => {
        beginRequest();
        setSort(value === NO_SORT ? undefined : value as VideoSort);
      },
    },
  ];

  const header = (
    <View style={styles.header}>
      <SearchBox value={input} onChangeText={(nextInput) => {
        setInput(nextInput);
        if (!nextInput.trim() && keyword) {
          beginRequest();
          setFilter(undefined);
          setKeyword('');
        }
      }} onSearch={(nextKeyword) => {
        if (nextKeyword === keyword) {
          void refresh();
          return;
        }
        beginRequest();
        setFilter(undefined);
        setKeyword(nextKeyword);
      }} />
      {keyword && <ThemedText type="smallBold">搜索：{keyword}</ThemedText>}
      <ChoiceMenuBar choices={keyword ? filterChoices : [
        {
          key: 'source',
          text: sourceOptions.find((item) => item.path === source)?.label ?? source,
          value: source,
          options: sourceOptions.map((item) => ({ label: item.label, value: item.path })),
          onChange: (value: string) => {
            beginRequest();
            setSource(value);
          },
        },
        ...filterChoices,
      ]} />
    </View>
  );

  const empty = loading ? (
    <ActivityIndicator style={styles.initialState} color={theme.text} />
  ) : (
    <View style={styles.initialState}>
      <ThemedText selectable themeColor="textSecondary">{error || '没有找到视频'}</ThemedText>
      {error && (
        <Pressable accessibilityRole="button" onPress={refresh} style={[styles.retry, { backgroundColor: theme.backgroundElement }]}>
          <ThemedText type="smallBold">重试</ThemedText>
        </Pressable>
      )}
    </View>
  );

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <FlatList
        contentContainerStyle={styles.content}
        columnWrapperStyle={styles.row}
        data={videos}
        keyExtractor={(video) => video.id}
        keyboardShouldPersistTaps="handled"
        ListEmptyComponent={empty}
        ListFooterComponent={(
          <View style={styles.footer}>
            {loadingMore && <ActivityIndicator color={theme.text} />}
            {error && videos.length > 0 && (
              <Pressable accessibilityRole="button" hitSlop={8} onPress={loadMore}>
                <ThemedText themeColor="textSecondary">{error}，点击重试</ThemedText>
              </Pressable>
            )}
          </View>
        )}
        ListHeaderComponent={header}
        numColumns={2}
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
        onRefresh={refresh}
        refreshing={refreshing}
        renderItem={({ item }) => <VideoCard video={item} />}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    padding: Spacing.lg,
    paddingBottom: Spacing.xxxl + TabBarInset,
    flexGrow: 1,
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
  initialState: {
    alignItems: 'center',
    gap: Spacing.lg,
    paddingVertical: Spacing.giant,
  },
  retry: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
  footer: {
    alignItems: 'center',
    minHeight: Spacing.xxxl,
  },
});
