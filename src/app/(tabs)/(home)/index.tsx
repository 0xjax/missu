import { useRouter, type Href } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getHome, getVideos, isAbortError, type HomeSection, type VideoSummary } from '@/api/missav';
import { SearchBox } from '@/components/search-box';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing, TabBarInset } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const HOT_PREVIEW_LIMIT = 8;

export default function HomeScreen() {
  const router = useRouter();
  const theme = useTheme();
  const [sections, setSections] = useState<HomeSection[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  // NOTE: “今日热门”不在首页 HTML 里，单独取一次；失败时只是不显示该板块，不影响首页其他内容。
  const loadHome = useCallback(async (signal?: AbortSignal) => {
    const [homeResult, hotResult] = await Promise.all([
      getHome(signal),
      getVideos('/today-hot', {}, signal)
        .then((result) => result.videos.slice(0, HOT_PREVIEW_LIMIT))
        .catch(() => [] as VideoSummary[]),
    ]);
    return hotResult.length > 0
      ? [{ id: 'today-hot', title: '今日热门', videos: hotResult }, ...homeResult]
      : homeResult;
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    loadHome(controller.signal).then((result) => {
      if (active) setSections(result);
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
  }, [attempt, loadHome]);

  const refresh = async () => {
    setRefreshing(true);
    setError(undefined);
    try {
      setSections(await loadHome());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '加载失败');
    } finally {
      setRefreshing(false);
    }
  };

  const submitSearch = (query: string) => {
    // NOTE: typed routes 对目录 index 的生成不稳定，运行时 '/latest' 才是列表页。
    router.push({ pathname: '/latest', params: { query, source: '/new', title: '' } } as unknown as Href);
  };

  const openMore = (section: HomeSection) => {
    if (section.id === 'today-hot') {
      router.push({ pathname: '/discover', params: { tab: 'hot', period: 'today', at: String(Date.now()) } } as unknown as Href);
      return;
    }
    if (!section.morePath) return;
    router.push({ pathname: '/latest', params: { source: section.morePath, title: section.title, query: '' } } as unknown as Href);
  };

  const retry = () => {
    setLoading(true);
    setError(undefined);
    setAttempt((value) => value + 1);
  };

  return (
    <SafeAreaView edges={['bottom']} style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
        <SearchBox value={input} onChangeText={setInput} onSearch={submitSearch} />
        {loading && <ActivityIndicator color={theme.text} style={styles.state} />}
        {error && (
          <View style={styles.state}>
            <ThemedText themeColor="textSecondary">{error}</ThemedText>
            <Pressable accessibilityRole="button" onPress={retry} style={[styles.retry, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">重试</ThemedText>
            </Pressable>
          </View>
        )}
        {!loading && !refreshing && !error && sections.length === 0 && (
          <View style={styles.state}>
            <ThemedText themeColor="textSecondary">暂无内容</ThemedText>
            <Pressable accessibilityRole="button" onPress={retry} style={[styles.retry, { backgroundColor: theme.backgroundElement }]}>
              <ThemedText type="smallBold">重新加载</ThemedText>
            </Pressable>
          </View>
        )}
        {!loading && sections.map((section) => (
          <View key={section.id} style={styles.section}>
            <View style={styles.sectionHeader}>
              <ThemedText type="headline">{section.title}</ThemedText>
              {(section.id === 'today-hot' || section.morePath) && (
                <Pressable accessibilityRole="button" hitSlop={8} onPress={() => openMore(section)}>
                  <ThemedText type="small" themeColor="textSecondary">更多</ThemedText>
                </Pressable>
              )}
            </View>
            <FlatList
              contentContainerStyle={styles.sectionVideos}
              data={section.videos}
              horizontal
              keyExtractor={(video) => video.id}
              renderItem={({ item }) => <VideoCard video={item} width={220} />}
              showsHorizontalScrollIndicator={false}
            />
          </View>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    gap: Spacing.lg,
    padding: Spacing.lg,
    paddingBottom: Spacing.xxxl + TabBarInset,
  },
  state: {
    alignItems: 'center',
    gap: Spacing.lg,
    paddingVertical: Spacing.huge,
  },
  retry: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
  section: {
    gap: Spacing.md,
    marginTop: Spacing.sm,
  },
  sectionHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  sectionVideos: {
    gap: Spacing.md,
  },
});
