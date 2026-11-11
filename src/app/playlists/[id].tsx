import { Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, View } from 'react-native';

import { getPlaylistDetail, isAbortError, SessionExpiredError, type PlaylistDetail } from '@/api/missav';
import { ThemedText } from '@/components/themed-text';
import { VideoCard } from '@/components/video-card';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useAuth } from '@/stores/auth';

const errorMessage = (error: unknown) => error instanceof Error ? error.message : '加载失败，请稍后重试';

export default function PlaylistDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const { expire } = useAuth();
  const [detail, setDetail] = useState<PlaylistDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (typeof id !== 'string') return;
    const controller = new AbortController();
    let active = true;
    getPlaylistDetail(id, controller.signal).then((result) => {
      if (active) setDetail(result);
    }).catch((reason: unknown) => {
      if (!active || isAbortError(reason)) return;
      if (reason instanceof SessionExpiredError) {
        void expire();
        setError(reason.message);
        return;
      }
      setError(errorMessage(reason));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
      controller.abort();
    };
  }, [attempt, expire, id]);

  const retry = () => {
    setLoading(true);
    setError(undefined);
    setAttempt((value) => value + 1);
  };

  return (
    <>
      <Stack.Screen options={{ title: detail?.name || '片单' }} />
      <FlatList
        columnWrapperStyle={styles.row}
        contentContainerStyle={styles.content}
        data={detail?.videos ?? []}
        keyExtractor={(video) => video.id}
        ListEmptyComponent={(
          <View style={styles.state}>
            {loading ? <ActivityIndicator color={theme.text} /> : (
              <>
                <ThemedText themeColor="textSecondary">{error || '这个片单还没有影片'}</ThemedText>
                {error && (
                  <Pressable accessibilityRole="button" onPress={retry} style={[styles.button, { backgroundColor: theme.backgroundElement }]}>
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
    </>
  );
}

const styles = StyleSheet.create({
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
  state: {
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
