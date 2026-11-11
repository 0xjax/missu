import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, View } from 'react-native';

import { getSavedVideos, isAbortError, type VideoSummary } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';

export default function SavedScreen() {
  const router = useRouter();
  const theme = useTheme();
  const { user, ready } = useAuth();
  const [videos, setVideos] = useState<VideoSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const loadSaved = useCallback(async (signal?: AbortSignal) => {
    if (!ready || !user) return;
    setError(undefined);
    try {
      const result = await getSavedVideos({}, signal);
      setVideos(result.videos);
    } catch (reason) {
      if (isAbortError(reason) || signal?.aborted) return;
      setError(reason instanceof Error ? reason.message : '加载失败');
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [ready, user]);

  useFocusEffect(useCallback(() => {
    const controller = new AbortController();
    void loadSaved(controller.signal);
    return () => controller.abort();
  }, [loadSaved]));

  const visibleVideos = ready && user ? videos : [];

  return (
    <FlatList
      contentContainerStyle={styles.content}
      columnWrapperStyle={styles.row}
      data={visibleVideos}
      keyExtractor={(video) => video.id}
      ListEmptyComponent={(
        <View style={styles.empty}>
          {!ready ? (
            <ActivityIndicator color={theme.text} />
          ) : !user ? (
            <>
              <ThemedText themeColor="textSecondary">登录后查看云端收藏</ThemedText>
              <Pressable accessibilityRole="button" onPress={() => router.push('/account')} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
                <ThemedText type="smallBold">前往登录</ThemedText>
              </Pressable>
            </>
          ) : loading ? (
            <ActivityIndicator color={theme.text} />
          ) : (
            <>
              <ThemedText selectable themeColor="textSecondary">{error || '暂无收藏'}</ThemedText>
              {error && (
                <Pressable accessibilityRole="button" onPress={() => {
                  setLoading(true);
                  setError(undefined);
                  void loadSaved();
                }} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
                  <ThemedText type="smallBold">重试</ThemedText>
                </Pressable>
              )}
            </>
          )}
        </View>
      )}
      numColumns={2}
      renderItem={({ item }) => <VideoCard video={item} />}
      style={{ backgroundColor: theme.background }}
    />
  );
}

const styles = StyleSheet.create({
  content: {
    flexGrow: 1,
    padding: Spacing.lg,
  },
  row: {
    width: '100%',
    gap: Spacing.md,
    marginBottom: Spacing.xl,
  },
  empty: {
    alignItems: 'center',
    gap: Spacing.lg,
    paddingVertical: Spacing.huge,
  },
  button: {
    borderRadius: Radius.sm,
    paddingHorizontal: Spacing.xl,
    paddingVertical: Spacing.md,
  },
});
