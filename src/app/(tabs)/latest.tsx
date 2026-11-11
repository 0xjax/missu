import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from 'react-native-paper';

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
import { Radius, Spacing, TabBarInset } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const filters: { label: string; value?: VideoFilter }[] = [
  { label: '全部' },
  { label: '单人作品', value: 'individual' },
  { label: '多人作品', value: 'multiple' },
  { label: '中文字幕', value: 'chinese-subtitle' },
];
const sorts: { label: string; value?: VideoSort }[] = [
  { label: '默认' },
  { label: '发行日期', value: 'released_at' },
  { label: '最近更新', value: 'published_at' },
  { label: '收藏数', value: 'saved' },
  { label: '今日浏览', value: 'today_views' },
  { label: '本周浏览', value: 'weekly_views' },
  { label: '本月浏览', value: 'monthly_views' },
  { label: '总浏览量', value: 'views' },
];
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
      ? searchVideos(keyword, {}, controller.signal)
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
        ? await searchVideos(keyword)
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
      <ThemedText type="subtitle">MissAV</ThemedText>
      <SearchBox value={input} onChangeText={(nextInput) => {
        setInput(nextInput);
        if (!nextInput.trim() && keyword) {
          beginRequest();
          setKeyword('');
        }
      }} onSearch={(nextKeyword) => {
        if (nextKeyword === keyword) {
          void refresh();
          return;
        }
        beginRequest();
        setKeyword(nextKeyword);
      }} />
      <ThemedText type="smallBold">
        {keyword ? `搜索：${keyword}` : params.title || sourceTitles[source] || '视频列表'}
      </ThemedText>
      {!keyword && (
        <>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.choices}>
            {filters.map((item) => (
              <Chip key={item.label} compact selected={filter === item.value} onPress={() => {
                beginRequest();
                setFilter(item.value);
              }} style={[styles.choice, { backgroundColor: filter === item.value ? theme.accent : theme.backgroundElement }]} textStyle={{ color: filter === item.value ? theme.onAccent : theme.text }}>
                {item.label}
              </Chip>
            ))}
          </ScrollView>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.choices}>
            {sorts.map((item) => (
              <Chip key={item.label} compact selected={sort === item.value} onPress={() => {
                beginRequest();
                setSort(item.value);
              }} style={[styles.choice, { backgroundColor: sort === item.value ? theme.accent : theme.backgroundElement }]} textStyle={{ color: sort === item.value ? theme.onAccent : theme.text }}>
                {item.label}
              </Chip>
            ))}
          </ScrollView>
        </>
      )}
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
    <SafeAreaView edges={['top', 'bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
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
  choices: {
    gap: Spacing.sm,
  },
  choice: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.md,
    paddingVertical: Spacing.sm,
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
