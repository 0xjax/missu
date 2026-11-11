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
import { SearchBox } from '@/components/search-box';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { VideoFilterBar } from '@/components/video-filter-bar';
import { Radius, Spacing, TabBarInset } from '@/constants/theme';
import { SEARCH_FILTER_OPTIONS, VIDEO_FILTER_OPTIONS } from '@/constants/video-options';
import { useTheme } from '@/hooks/use-theme';

const sourceTitles: Record<string, string> = {
  '/new': '最近更新',
  '/release': '新作上市',
  '/fc2': '无码影片',
};

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
  const source = params.source ?? '/new';
  const [input, setInput] = useState(initialKeyword);
  const [keyword, setKeyword] = useState(initialKeyword);
  const [filter, setFilter] = useState<VideoFilter>();
  const [sort, setSort] = useState<VideoSort>();
  const [videos, setVideos] = useState<VideoSummary[]>([]);
  const [nextPage, setNextPage] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();

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
      <ThemedText type="smallBold">
        {keyword ? `搜索：${keyword}` : params.title || sourceTitles[source] || '视频列表'}
      </ThemedText>
      <VideoFilterBar
        filter={filter}
        filters={keyword ? SEARCH_FILTER_OPTIONS : VIDEO_FILTER_OPTIONS}
        onFilterChange={(value) => {
          beginRequest();
          setFilter(value);
        }}
        onSortChange={(value) => {
          beginRequest();
          setSort(value);
        }}
        sort={sort}
      />
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
